"""Trusted local demo controller. Credentials stay server-side, separate from Stitchy."""
import json
import os
import threading
import time
import urllib.parse
from pathlib import Path
from connector import Connector
from fixtures import ALICE, BOB
from http_support import Handler, request, RemoteError, serve

LOCK = threading.RLock()
ZT = os.environ['ZT_CONNECTOR_API_URL'].rstrip('/')
SOURCE = os.environ['STITCH_SOURCE_URL'].rstrip('/')
RUNNER = os.environ['STITCH_AGENT_URL'].rstrip('/')
SCOPE = {'X-Tenant-Id': os.environ['ZT_TENANT_ID'], 'X-Workspace-Id': os.environ['ZT_WORKSPACE_ID']}
SOURCE_HEADERS = {'Authorization': 'Bearer ' + os.environ['STITCH_SOURCE_TOKEN']}
RUNNER_HEADERS = {'Authorization': 'Bearer ' + os.environ['STITCH_SIM_INTERNAL_TOKEN']}
CLIENT_ID = os.getenv('STITCH_SIM_CLIENT_ID', 'stitchy-simulator')
STATE_FILE = Path('/state/client.json')
CONNECTOR = Connector()
TOKENS = {}
SESSIONS = {}
PREPARED = False
AUTO_SYNC = True
LAST_SYNC = None
SYNC_ERROR = None
CREDENTIAL = json.loads(STATE_FILE.read_text()) if STATE_FILE.exists() else None


def sync():
    global LAST_SYNC, SYNC_ERROR
    try:
        CONNECTOR.run_once()
        LAST_SYNC, SYNC_ERROR = time.time(), None
    except Exception:
        SYNC_ERROR = 'Connector cycle failed; source readiness or deletion delivery may be incomplete.'
        raise


def token(actor):
    if actor not in ('alice', 'bob'):
        raise ValueError('Unknown human')
    cached = TOKENS.get(actor)
    if not cached or cached['expires'] <= time.time():
        result = CONNECTOR.request(CONNECTOR.token_url, 'POST', {
            'grant_type': 'password', 'client_id': 'zt-stitch-human-demo',
            'username': actor + '-demo', 'password': 'local-stitch-human-only'}, form=True)
        cached = {'token': result['access_token'], 'expires': time.time() + int(result['expires_in']) - 15}
        TOKENS[actor] = cached
    return {**SCOPE, 'Authorization': 'Bearer ' + cached['token']}


def human(actor, path, method='GET', data=None):
    try:
        return request(ZT + '/v1/integrations/retrieval' + path, method, data, token(actor))
    except RemoteError as error:
        if error.status != 401:
            raise
        TOKENS.pop(actor, None)
        return request(ZT + '/v1/integrations/retrieval' + path, method, data, token(actor))


def prepare():
    global CREDENTIAL, PREPARED
    # Registration is a ZT administrator action, not a client-side policy/setup mutation.
    CONNECTOR.call('/source-state')
    if CREDENTIAL is None:
        result = request(ZT + '/v1/clients', 'POST', {
            'clientId': CLIENT_ID, 'name': 'Local Stitchy Simulator',
            'workspaceId': SCOPE['X-Workspace-Id'], 'scopes': []},
            {**SCOPE, 'X-API-Key': os.environ['ZT_SIM_ADMIN_KEY']})
        CREDENTIAL = {'clientId': result['clientId'], 'secret': result['secret']}
        STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
        STATE_FILE.write_text(json.dumps(CREDENTIAL))
        STATE_FILE.chmod(0o600)
    sync()
    for actor, expected in [('alice', ALICE), ('bob', BOB)]:
        identity = human(actor, '/context')
        if identity['subject'] != expected or identity['actorType'] != 'HUMAN':
            raise ValueError('Realm fixture identity mismatch')
    PREPARED = True
    return {'prepared': True, 'aiSubject': 'client:' + CLIENT_ID, 'lastSync': LAST_SYNC}


class PortalHandler(Handler):
    def handle_request(self):
        global AUTO_SYNC
        if self.command == 'GET' and self.path == '/':
            self.send(Path('/app/index.html').read_text(), content_type='text/html')
            return
        # Loopback-only published UI, same-origin requests and no CORS. No tokens exposed to JS.
        host = self.headers.get('Host', '')
        if host not in ('localhost:8766', '127.0.0.1:8766'):
            self.send({'error': 'Use http://localhost:8766'}, 403)
            return
        if self.command == 'POST' and self.headers.get('Origin') not in ('http://localhost:8766', 'http://127.0.0.1:8766'):
            self.send({'error': 'Same-origin browser request required'}, 403)
            return
        with LOCK:
            if self.command == 'GET' and self.path == '/api/setup':
                identity = CONNECTOR.call('/context')
                self.send({'connectionId': 'stitch', 'displayName': 'Stitch local simulator',
                           'connectorSubject': identity['subject'], 'enabled': True,
                           'workspaceId': SCOPE['X-Workspace-Id'],
                           'instruction': 'Register this identity in ZT Retrieval Access. AI policies are managed only in ZT.'})
                return
            if self.command == 'GET' and self.path == '/api/state':
                self.send({'prepared': PREPARED, 'autoSync': AUTO_SYNC, 'lastSync': LAST_SYNC, 'syncError': SYNC_ERROR,
                           'source': request(SOURCE + '/demo/state', headers=SOURCE_HEADERS),
                           'agent': request(RUNNER + '/state', headers=RUNNER_HEADERS),
                           'sessions': SESSIONS, 'aiSubject': 'client:' + CLIENT_ID})
                return
            if self.command != 'POST' or not self.path.startswith('/api/'):
                self.send({'error': 'Unknown route'}, 404)
                return
            body = self.body()
            action = self.path.removeprefix('/api/')
            if action == 'prepare':
                self.send(prepare())
                return
            if not PREPARED:
                self.send({'error': 'Click Prepare demo first.'}, 409)
                return
            if action == 'delegate':
                actor = body.get('human', 'alice')
                if actor in SESSIONS:
                    try:
                        human(actor, '/sessions/' + SESSIONS[actor]['sessionId'], 'DELETE')
                    except RemoteError as error:
                        if error.status != 404:
                            raise
                result = human(actor, '/sessions', 'POST', {'aiSubject': 'client:' + CLIENT_ID})
                SESSIONS[actor] = result
            elif action == 'run':
                actor = body['actor']
                operation = body['operation']
                if actor == 'stitchy':
                    delegate = body.get('human', 'alice')
                    if delegate not in SESSIONS:
                        self.send({'error': 'Create a human → Stitchy session first.'}, 409)
                        return
                    try:
                        result = request(RUNNER + '/run', 'POST', {
                            'operation': operation, 'resourceId': body.get('resourceId'), 'query': body.get('query', ''),
                            'sessionId': SESSIONS[delegate]['sessionId'], 'credential': CREDENTIAL,
                            'tenantId': SCOPE['X-Tenant-Id'], 'workspaceId': SCOPE['X-Workspace-Id']}, RUNNER_HEADERS)
                    except RemoteError as error:
                        # Do not silently renew a revoked/expired session. Show actual rejection.
                        result = {'decision': 'ERROR', 'dependencyStatus': error.status,
                                  'answer': 'No content returned. Session expired/revoked, download denied, or dependency failed. Check ZT audit.'}
                else:
                    paths = {'read': '/retrieve', 'children': '/children', 'search': '/search', 'download': '/download'}
                    payload = {'query': body.get('query', '')} if operation == 'search' else {'resourceId': body['resourceId']}
                    result = human(actor, paths[operation], 'POST', payload)
            elif action == 'source-change':
                kind = body['kind']
                paths = {'membership': '/demo/membership', 'delete': '/demo/delete', 'reset': '/demo/reset'}
                if kind == 'access':
                    self.send({'error': 'Manage AI access policies in the ZT dashboard, not in the client.'}, 400)
                    return
                # Block content calls until the changed source ACL has been fully committed to ZT.
                state = CONNECTOR.call('/source-state')
                CONNECTOR.call('/source-state', 'POST', {'ready': False, 'expectedAclVersion': state['acl_version']})
                result = request(SOURCE + paths[kind], 'POST', body, SOURCE_HEADERS)
                if AUTO_SYNC:
                    sync()
                    result['synced'] = True
                else:
                    result['synced'] = False
                if kind == 'reset':
                    request(RUNNER + '/clear', 'POST', {}, RUNNER_HEADERS)
            elif action == 'sync':
                sync()
                result = {'synced': True, 'lastSync': LAST_SYNC}
            elif action == 'auto-sync':
                AUTO_SYNC = bool(body['enabled'])
                result = {'autoSync': AUTO_SYNC}
            elif action == 'revoke':
                actor = body.get('human', 'alice')
                result = human(actor, '/sessions/' + SESSIONS[actor]['sessionId'], 'DELETE')
                # Keep handle deliberately, so the next run demonstrates actual ZT rejection.
                SESSIONS[actor]['revoked'] = True
                CONNECTOR.erase()
            elif action == 'history':
                result = {'calls': CONNECTOR.call('/calls'), 'pendingDeletions': CONNECTOR.call('/deletion-events'),
                          'sourceState': CONNECTOR.call('/source-state')}
            elif action == 'bypass':
                result = request(RUNNER + '/bypass', 'POST', {}, RUNNER_HEADERS)
            elif action == 'maintenance':
                sync()
                result = request(RUNNER + '/state', headers=RUNNER_HEADERS)
            else:
                self.send({'error': 'Unknown operation'}, 404)
                return
            self.send(result)


if __name__ == '__main__':
    def worker():
        while True:
            time.sleep(20)
            with LOCK:
                if PREPARED and AUTO_SYNC:
                    try:
                        sync()
                    except Exception:
                        pass
    threading.Thread(target=worker, daemon=True).start()
    serve(PortalHandler, 8766)
