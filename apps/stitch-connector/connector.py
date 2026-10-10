"""Version 1 connector contract worker. No vendor credentials or deployment assumptions are embedded."""
import json
import os
import time
import ssl
import urllib.parse
import urllib.request
import urllib.error
from pathlib import Path

MAX_BYTES = 4 * 1024 * 1024


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class ApiError(RuntimeError):
    def __init__(self, code):
        super().__init__(f"Connector HTTP request failed ({code}); credentials and response body omitted")
        self.code = code


class Connector:
    def __init__(self):
        self.allowed_hosts = {x.strip().lower() for x in os.environ['STITCH_CONNECTOR_ALLOWED_HOSTS'].split(',') if x.strip()}
        self.allow_http = os.getenv('STITCH_CONNECTOR_DEVELOPMENT_HTTP', 'false').lower() == 'true'
        self.zt = self.validate(os.environ['ZT_CONNECTOR_API_URL']).rstrip('/') + '/v1/integrations/retrieval'
        self.source = self.validate(os.environ['STITCH_SOURCE_URL']).rstrip('/')
        self.token_url = self.validate(os.environ['STITCH_CONNECTOR_TOKEN_URL'])
        self.client_id = os.environ['STITCH_CONNECTOR_CLIENT_ID']
        self.client_secret = os.environ['STITCH_CONNECTOR_CLIENT_SECRET']
        self.source_token = os.environ['STITCH_SOURCE_TOKEN']
        self.tenant = os.environ['ZT_TENANT_ID']
        self.workspace = os.environ['ZT_WORKSPACE_ID']
        self.cursor_file = Path(os.getenv('STITCH_CONNECTOR_CURSOR_FILE', '/state/cursor.json'))
        context = ssl.create_default_context()
        if os.getenv('STITCH_CONNECTOR_CA_FILE'):
            context.load_verify_locations(cafile=os.environ['STITCH_CONNECTOR_CA_FILE'])
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect(), urllib.request.HTTPSHandler(context=context))
        self.token = None
        self.token_expiry = 0

    def validate(self, url):
        parsed = urllib.parse.urlsplit(url)
        if parsed.hostname not in self.allowed_hosts or parsed.username or parsed.password or parsed.fragment or parsed.query:
            raise ValueError('Connector URL must use an explicitly allowed host and must not contain credentials/query/fragment')
        if parsed.scheme != 'https' and not (self.allow_http and parsed.scheme == 'http' and parsed.hostname in {'localhost', '127.0.0.1', 'host.docker.internal', 'keycloak', 'authorization-api', 'stitch-source'}):
            raise ValueError('HTTPS required outside explicit local development')
        return url

    def request(self, url, method='GET', data=None, headers=None, form=False):
        body = None if data is None else (urllib.parse.urlencode(data).encode() if form else json.dumps(data, allow_nan=False).encode())
        if body is not None and len(body) > MAX_BYTES:
            raise ValueError('Connector payload exceeds 4 MiB')
        values = dict(headers or {})
        if body is not None:
            values['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
        values['Accept'] = 'application/json'
        request = urllib.request.Request(url, data=body, method=method, headers=values)
        try:
            with self.opener.open(request, timeout=20) as response:
                payload = response.read(MAX_BYTES + 1)
                if len(payload) > MAX_BYTES:
                    raise ValueError('Connector response exceeds 4 MiB')
                return json.loads(payload) if payload else {}
        except urllib.error.HTTPError as error:
            raise ApiError(error.code) from None

    def zt_headers(self):
        if time.time() >= self.token_expiry:
            result = self.request(self.token_url, 'POST', {'grant_type': 'client_credentials', 'client_id': self.client_id, 'client_secret': self.client_secret}, form=True)
            self.token = result['access_token']
            self.token_expiry = time.time() + max(1, int(result.get('expires_in', 60)) - 15)
        return {'Authorization': 'Bearer ' + self.token, 'X-Tenant-Id': self.tenant, 'X-Workspace-Id': self.workspace}

    def call(self, path, method='GET', data=None):
        try:
            return self.request(self.zt + path, method, data, self.zt_headers())
        except ApiError as error:
            if error.code != 401:
                raise
            self.token_expiry = 0
            return self.request(self.zt + path, method, data, self.zt_headers())

    def source_call(self, path, method='GET', data=None):
        return self.request(self.source + path, method, data, {'Authorization': 'Bearer ' + self.source_token})

    def sync(self):
        cursor = json.loads(self.cursor_file.read_text()).get('cursor', '') if self.cursor_file.exists() else ''
        for _ in range(10):
            page = self.source_call('/zt/changes?' + urllib.parse.urlencode({'cursor': cursor, 'limit': 100, 'tenantId': self.tenant, 'workspaceId': self.workspace}))
            changes = page.get('changes', [])
            if not isinstance(changes, list) or len(changes) > 100:
                raise ValueError('Invalid source change page')
            for change in changes:
                kind = change['type']
                body = change.get('payload', {})
                try:
                    if kind == 'subject.upsert':
                        self.call('/subjects', 'POST', body)
                    elif kind == 'resource.upsert':
                        self.call('/resources', 'POST', body)
                    elif kind == 'resource.deleted':
                        self.call('/resources/' + urllib.parse.quote(body['resourceId'], safe=''), 'DELETE')
                    elif kind == 'session.revoked':
                        self.call('/sessions/' + urllib.parse.quote(body['sessionId'], safe=''), 'DELETE')
                    elif kind == 'token.revoked':
                        self.call('/revoked-tokens', 'POST', body)
                    else:
                        raise ValueError('Unsupported source change type')
                except ApiError as error:
                    # Monotonic source versions make replayed upserts non-destructive.
                    if not (error.code == 409 and kind.endswith('.upsert')) and not (error.code == 404 and kind in {'resource.deleted', 'session.revoked'}):
                        raise
            next_cursor = page.get('nextCursor', cursor)
            if not isinstance(next_cursor, str) or len(next_cursor) > 512:
                raise ValueError('Invalid source cursor')
            self.cursor_file.parent.mkdir(parents=True, exist_ok=True)
            pending = self.cursor_file.with_suffix('.tmp')
            pending.write_text(json.dumps({'cursor': next_cursor}))
            pending.replace(self.cursor_file)
            cursor = next_cursor
            if not page.get('hasMore', False):
                return
        raise RuntimeError('Source page budget reached; access remains blocked until backlog is synchronized')

    def erase(self):
        for event in self.call('/deletion-events'):
            if event['status'] != 'PENDING':
                continue
            receipt = self.source_call('/zt/deletions', 'POST', {'eventId': event['id'], 'tenantId': self.tenant, 'workspaceId': self.workspace, 'resourceId': event['resource_id'], 'targetSubject': event['target_subject'], 'reason': event['reason']})
            if receipt.get('eventId') != event['id'] or receipt.get('status') != 'DELETED' or not isinstance(receipt.get('receiptId'), str):
                raise ValueError('Recipient did not acknowledge matching deletion event')
            self.call('/deletion-events/' + event['id'] + '/ack', 'POST', {'receiptId': receipt['receiptId']})

    def run_once(self):
        try:
            state = self.call('/source-state')
            self.call('/source-state', 'POST', {'ready': False, 'expectedAclVersion': state['acl_version']})
            self.sync()
            self.call('/maintenance', 'POST', {})
            state = self.call('/source-state')
            self.call('/source-state', 'POST', {'ready': True, 'expectedAclVersion': state['acl_version']})
        finally:
            # Cleanup is allowed for a bound disabled connection; it never grants content access.
            self.erase()


if __name__ == '__main__':
    connector = Connector()
    once = os.getenv('STITCH_CONNECTOR_ONCE', 'false').lower() == 'true'
    while True:
        try:
            connector.run_once()
        except Exception:
            # Never print source content, tokens, raw HTTP bodies or credential-bearing URLs.
            print('Connector cycle failed; no cursor is advanced past a failed change. Inspect source/target status.', flush=True)
            if once:
                raise SystemExit(1)
        if once:
            break
        time.sleep(max(10, int(os.getenv('STITCH_CONNECTOR_INTERVAL_SECONDS', '60'))))
