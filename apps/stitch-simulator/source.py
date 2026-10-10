"""Virtual Stitch ACL/content source with cursor feed and recipient erasure contract."""
import copy
import json
import os
import threading
import time
import urllib.parse
from pathlib import Path
from fixtures import resources, subjects, ALICE, PREFIX
from http_support import Handler, request, serve

LOCK = threading.RLock()
PATH = Path('/state/source.json')
TOKEN = os.environ['STITCH_SOURCE_TOKEN']
TENANT = os.environ['ZT_TENANT_ID']
WORKSPACE = os.environ['ZT_WORKSPACE_ID']
AGENT = os.environ['STITCH_AGENT_URL'].rstrip('/')
AGENT_TOKEN = os.environ['STITCH_SIM_INTERNAL_TOKEN']


def save():
    pending = PATH.with_suffix('.tmp')
    pending.write_text(json.dumps(STATE))
    pending.replace(PATH)


def version():
    STATE['version'] = max(STATE.get('version', 0) + 1, time.time_ns())
    return STATE['version']


def append(kind, payload):
    STATE['sequence'] += 1
    STATE['changes'].append({'sequence': STATE['sequence'], 'type': kind, 'payload': copy.deepcopy(payload)})


def reset():
    revision = version()
    STATE['resources'] = {r['resourceId']: r for r in resources(revision)}
    STATE['subjects'] = {s['subject']: s for s in subjects(revision)}
    # Keep cursor history/sequence monotonic; reset is a new source snapshot, not a DB reset.
    for subject in STATE['subjects'].values():
        append('subject.upsert', subject)
    for resource in STATE['resources'].values():
        append('resource.upsert', resource)
    save()


PATH.parent.mkdir(parents=True, exist_ok=True)
STATE = json.loads(PATH.read_text()) if PATH.exists() else {'sequence': 0, 'version': 0, 'changes': [], 'receipts': {}}
if not STATE['sequence']:
    reset()


class SourceHandler(Handler):
    def handle_request(self):
        if not self.authorized(TOKEN):
            return
        parsed = urllib.parse.urlsplit(self.path)
        with LOCK:
            if self.command == 'GET' and parsed.path == '/zt/changes':
                query = urllib.parse.parse_qs(parsed.query)
                if query.get('tenantId') != [TENANT] or query.get('workspaceId') != [WORKSPACE]:
                    self.send({'error': 'Scope mismatch'}, 403)
                    return
                cursor = int(query.get('cursor', ['0'])[0] or '0')
                if not 0 <= cursor <= STATE['sequence']:
                    raise ValueError('Invalid cursor')
                limit = max(1, min(100, int(query.get('limit', ['100'])[0])))
                changes = [c for c in STATE['changes'] if c['sequence'] > cursor][:limit]
                next_cursor = changes[-1]['sequence'] if changes else cursor
                self.send({'changes': changes, 'nextCursor': str(next_cursor), 'hasMore': next_cursor < STATE['sequence']})
                return
            if self.command == 'GET' and parsed.path == '/demo/state':
                self.send({'resources': [{k: r[k] for k in ('resourceId', 'parentId', 'kind', 'title', 'aiAccess', 'sourceVersion')}
                                        for r in STATE['resources'].values()],
                           'subjects': list(STATE['subjects'].values()), 'sequence': STATE['sequence'],
                           'receipts': list(STATE['receipts'].values())[-100:]})
                return
            if self.command != 'POST':
                self.send({'error': 'Unknown route'}, 404)
                return
            body = self.body()
            if parsed.path == '/zt/deletions':
                if body.get('tenantId') != TENANT or body.get('workspaceId') != WORKSPACE:
                    raise ValueError('Scope mismatch')
                event = body['eventId']
                if event not in STATE['receipts']:
                    receipt = request(AGENT + '/erase', 'POST', body, {'Authorization': 'Bearer ' + AGENT_TOKEN})
                    if receipt.get('status') != 'DELETED' or receipt.get('eventId') != event:
                        raise ValueError('Recipient deletion not completed')
                    STATE['receipts'][event] = receipt
                    save()
                self.send(STATE['receipts'][event])
                return
            if parsed.path == '/demo/reset':
                reset()
            elif parsed.path == '/demo/membership':
                subject = STATE['subjects'][ALICE]
                subject['groups'] = ['employees', 'finance'] if body['finance'] else ['employees']
                subject['sourceVersion'] = version()
                append('subject.upsert', subject)
                save()
            elif parsed.path == '/demo/delete':
                identifier = body['resourceId']
                if identifier not in STATE['resources']:
                    raise ValueError('Unknown resource')
                removed = {identifier}
                while True:
                    children = {r['resourceId'] for r in STATE['resources'].values() if r['parentId'] in removed}
                    if children <= removed:
                        break
                    removed.update(children)
                for resource_id in removed:
                    del STATE['resources'][resource_id]
                version()  # Must exceed backend tombstone revision on the next reset.
                append('resource.deleted', {'resourceId': identifier})
                save()
            else:
                self.send({'error': 'Unknown route'}, 404)
                return
            self.send({'sourceChanged': True, 'sequence': STATE['sequence']})


if __name__ == '__main__':
    serve(SourceHandler, 8765)
