"""Read real demo files, sync source ACLs via authenticated ZT APIs, and refresh readiness.

The volume is also exported by NFS. This worker reads the shared volume directly;
it does not claim to exercise the NFS wire protocol or give end users a mount.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        return None


class ApiError(RuntimeError):
    def __init__(self, code):
        self.code = code
        super().__init__(f'ZT connector HTTP {code}; check registration/configuration')


class Worker:
    def __init__(self):
        self.api = os.environ['ZT_API_URL'].rstrip('/')
        self.token_url = os.environ['ZT_TOKEN_URL']
        for value, host in ((self.api, 'authorization-api'), (self.token_url, 'keycloak')):
            url = urllib.parse.urlsplit(value)
            if url.scheme != 'http' or url.hostname != host or url.username or url.password:
                raise ValueError('Only fixed local container endpoints are supported')
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
        self.state_path = Path('/state/cursor.json')
        self.state = json.loads(self.state_path.read_text()) if self.state_path.exists() else {'items': {}, 'version': 0}
        self.token = ''
        self.token_until = 0

    def request(self, url, method='GET', body=None, headers=None, form=False):
        data = None if body is None else (urllib.parse.urlencode(body).encode() if form else json.dumps(body).encode())
        values = dict(headers or {})
        if data is not None:
            values['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
        req = urllib.request.Request(url, data=data, method=method, headers=values)
        try:
            with self.opener.open(req, timeout=20) as reply:
                raw = reply.read(4 * 1024 * 1024 + 1)
                if len(raw) > 4 * 1024 * 1024:
                    raise RuntimeError('Oversized connector response')
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as error:
            # Never print token, headers, source data, or response bodies.
            raise ApiError(error.code) from None

    def call(self, path, method='GET', body=None):
        if time.time() >= self.token_until:
            value = self.request(self.token_url, 'POST', {'grant_type': 'client_credentials', 'client_id': self.config['clientId'], 'client_secret': self.config['clientSecret']}, form=True)
            self.token = value['access_token']
            self.token_until = time.time() + max(1, int(value['expires_in']) - 20)
        headers = {'Authorization': 'Bearer ' + self.token, 'X-Tenant-Id': self.config['tenantId'], 'X-Workspace-Id': self.config['workspaceId']}
        return self.request(self.api + '/v1/integrations/retrieval' + path, method, body, headers)

    def persist(self):
        tmp = self.state_path.with_suffix('.tmp')
        tmp.write_text(json.dumps(self.state))
        tmp.replace(self.state_path)

    def version(self):
        self.state['version'] = max(int(time.time() * 1000), self.state['version'] + 1)
        self.persist()
        return self.state['version']

    def upsert(self, key, endpoint, payload, current):
        digest = hashlib.sha256(json.dumps(payload, sort_keys=True).encode()).hexdigest()
        current[key] = digest
        if self.state['items'].get(key) == digest:
            return
        payload['sourceVersion'] = self.version()
        self.call(endpoint, 'POST', payload)
        self.state['items'][key] = digest
        self.persist()

    @staticmethod
    def resource_id(path):
        return 'file-demo-' + (path if path in ('payroll', 'it', 'general') else hashlib.sha256(path.encode()).hexdigest()[:24])

    def sync(self):
        self.config = json.loads(Path('/config/config.json').read_text())
        scope = self.config['tenantId'] + ':' + self.config['workspaceId']
        if self.state.get('scope', scope) != scope:
            raise RuntimeError('Cursor scope changed; use a separate state volume')
        self.state['scope'] = scope
        current = {}
        for human in self.config['subjects']:
            payload = {'subject': human['subject'], 'groups': human['groups'], 'active': True}
            self.upsert('subject:' + human['subject'], '/subjects', payload, current)
        for key in list(self.state['items']):
            if key.startswith('subject:') and key not in current:
                self.call('/subjects', 'POST', {'subject': key[8:], 'groups': [], 'active': False, 'sourceVersion': self.version()})
                del self.state['items'][key]
        root = Path('/files').resolve()
        paths = sorted(root.rglob('*'), key=lambda p: (len(p.relative_to(root).parts), str(p)))
        if len(paths) > 100:
            raise RuntimeError('Demo scope is limited to 100 paths')
        for path in paths:
            if path.is_symlink() or not path.resolve().is_relative_to(root):
                raise RuntimeError('Symlinks and paths outside the file root are not supported')
            rel = path.relative_to(root).as_posix()
            parts = path.relative_to(root).parts
            if parts[0] not in ('payroll', 'it', 'general'):
                raise RuntimeError('Only payroll/it/general demo folders are registered')
            if not path.is_dir() and (not path.is_file() or path.stat().st_size > 64000):
                raise RuntimeError('Only regular text files of at most 64000 bytes are supported')
            raw = b'' if path.is_dir() else path.read_bytes()
            text = raw.decode('utf-8')
            parent = None if len(parts) == 1 else self.resource_id(path.parent.relative_to(root).as_posix())
            groups = ('finance', 'it') if parts[0] == 'general' else (('finance',) if parts[0] == 'payroll' else ('it',))
            acl = [{'principalKind': 'GROUP', 'principalId': group, 'permission': permission, 'effect': 'ALLOW'} for group in groups for permission in ('READ', 'SEARCH')]
            payload = {'resourceId': self.resource_id(rel), 'parentId': parent, 'kind': 'FOLDER' if path.is_dir() else 'FILE', 'title': path.name,
                       'ownerSubject': 'file-demo-source-owner', 'aiAccess': 'ALLOW', 'purgeAfter': None, 'content': text, 'acl': acl,
                       'chunks': [{'content': text[n:n + 16000]} for n in range(0, len(text), 16000)],
                       'fileBase64': None if path.is_dir() else base64.b64encode(raw).decode()}
            self.upsert('resource:' + self.resource_id(rel), '/resources', payload, current)
        # Deleted files are removed before publishing a fresh source checkpoint.
        for key in list(self.state['items']):
            if key.startswith('resource:') and key not in current:
                try:
                    self.call('/resources/' + urllib.parse.quote(key[9:], safe=''), 'DELETE')
                except ApiError as error:
                    if error.code != 404:
                        raise
                del self.state['items'][key]
        state = self.call('/source-state')
        self.call('/source-state', 'POST', {'ready': True, 'expectedAclVersion': state['acl_version']})
        self.persist()
        Path('/state/health.json').write_text(json.dumps({'syncedAt': time.time(), 'resourceCount': sum(k.startswith('resource:') for k in current)}))
        print('File connector synchronized; source ACL checkpoint ready', flush=True)


if __name__ == '__main__':
    worker = Worker()
    while True:
        try:
            worker.sync()
        except Exception as error:
            worker.token_until = 0
            print('File connector cycle failed: ' + (str(error) if isinstance(error, RuntimeError) else type(error).__name__), flush=True)
        time.sleep(30)
