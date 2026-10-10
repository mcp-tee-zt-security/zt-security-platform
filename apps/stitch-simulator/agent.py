"""Stitchy simulator. Only ZT-authorized tool results can enter its ephemeral memory."""
import json
import os
import socket
import threading
import time
import uuid
from http_support import Handler, request, RemoteError, serve

GATEWAY = os.environ['ZT_SIM_GATEWAY_URL'].rstrip('/')
TOKEN = os.environ['STITCH_SIM_INTERNAL_TOKEN']
LOCK = threading.RLock()
MEMORY = []
ANSWERS = []
ERASURES = []
TTL = 60  # Short demo retention, deliberately independent of receipt TTL in ZT.


def expire():
    now = time.time()
    MEMORY[:] = [item for item in MEMORY if item['expiresAt'] > now]
    ANSWERS[:] = [item for item in ANSWERS if item['expiresAt'] > now]


def guarded_request(path, payload, headers):
    try:
        return request(GATEWAY + path, 'POST', payload, headers)
    except RemoteError as error:
        return {'decision': 'REJECTED', 'dependencyStatus': error.status, 'contentReachedAgent': 0,
                'answer': 'No content returned: ZT rejected authentication/session/access or the gateway failed.'}


class AgentHandler(Handler):
    def handle_request(self):
        if not self.authorized(TOKEN):
            return
        with LOCK:
            expire()
            if self.command == 'GET' and self.path == '/state':
                self.send({'mode': 'Extractive simulator — no LLM', 'memory': [
                    {'resourceId': item['resourceId'], 'expiresAt': item['expiresAt']} for item in MEMORY],
                    'retainedAnswers': len(ANSWERS), 'erasureReceipts': ERASURES[-50:], 'retentionSeconds': TTL})
                return
            if self.command != 'POST':
                self.send({'error': 'Unknown route'}, 404)
                return
            body = self.body()
            if self.path == '/erase':
                if body['targetSubject'] != os.environ['STITCH_SIM_AI_SUBJECT']:
                    self.send({'error': 'Recipient mismatch'}, 403)
                    return
                resource = body['resourceId']
                before = len(MEMORY) + len(ANSWERS)
                MEMORY[:] = [item for item in MEMORY if item['resourceId'] != resource]
                ANSWERS[:] = [item for item in ANSWERS if resource not in item['resources']]
                receipt = {'eventId': body['eventId'], 'status': 'DELETED', 'receiptId': 'sim-' + str(uuid.uuid4()),
                           'resourceId': resource, 'removedItems': before - len(MEMORY) - len(ANSWERS),
                           'scope': 'Local simulator memory and retained answers only'}
                ERASURES.append(receipt)
                del ERASURES[:-100]
                self.send(receipt)
                return
            if self.path == '/clear':
                MEMORY.clear()
                ANSWERS.clear()
                self.send({'cleared': True})
                return
            if self.path == '/bypass':
                results = []
                # Fixed harmless TCP probes, no SQL/HTTP/content and no credentials.
                for host, port in [('stitch-source', 8765), ('authorization-api', 8080), ('postgres', 5432)]:
                    try:
                        with socket.create_connection((host, port), timeout=2):
                            reachable = True
                    except OSError:
                        reachable = False
                    results.append({'target': f'{host}:{port}', 'reachable': reachable,
                                    'observation': 'TCP reachable' if reachable else 'DNS or TCP connection blocked'})
                self.send({'probes': results, 'scope': 'This container network only; not a production boundary proof'})
                return
            if self.path != '/run':
                self.send({'error': 'Unknown route'}, 404)
                return
            kind = body['operation']
            credential = body['credential']
            headers = {'X-Client-Id': credential['clientId'], 'X-API-Key': credential['secret'],
                       'X-Tenant-Id': body['tenantId'], 'X-Workspace-Id': body['workspaceId'],
                       'X-ZT-Delegation': body['sessionId'], 'MCP-Protocol-Version': '2025-06-18'}
            identifier = body.get('resourceId', '')
            if kind == 'download':
                result = guarded_request('/v1/integrations/stitch/download',
                                         {'resourceId': identifier, 'sessionId': body['sessionId']}, headers)
                if result.get('decision') == 'REJECTED':
                    self.send(result)
                    return
                now = time.time()
                MEMORY.append({'resourceId': identifier, 'content': result['text'], 'expiresAt': now + TTL})
                del MEMORY[:-200]
                self.send({'operation': kind, 'decision': 'ALLOW', 'download': result,
                           'contentReachedAgent': 1, 'answer': 'Authorized file download.'})
                return
            tool = {'read': 'readDocument', 'search': 'searchDocuments', 'children': 'listChannelMessages'}.get(kind)
            if not tool:
                raise ValueError('Unknown operation')
            arguments = {'query': body['query']} if kind == 'search' else {'resourceId': identifier}
            rpc = {'jsonrpc': '2.0', 'id': str(uuid.uuid4()), 'method': 'tools/call',
                   'params': {'name': tool, 'arguments': arguments}}
            envelope = guarded_request('/v1/integrations/stitch/mcp', rpc, headers)
            if envelope.get('decision') == 'REJECTED':
                self.send(envelope)
                return
            if 'error' in envelope:
                self.send({'toolResponse': envelope, 'answer': 'Tool call failed; no content used.'})
                return
            result = json.loads(envelope['result']['content'][0]['text'])
            rows = result.get('results', []) if result.get('decision') == 'ALLOW' else []
            # Never answer from old memory; every response uses this fresh authorized result only.
            answer = '\n\n'.join(f"[{row['resource_id']}] {row.get('content', '')}" for row in rows)
            if not answer:
                answer = 'Access denied. No content reached Stitchy.' if result.get('decision') == 'DENY' else 'No authorized matching content found.'
            now = time.time()
            for row in rows:
                MEMORY.append({'resourceId': row['resource_id'], 'content': row.get('content', ''), 'expiresAt': now + TTL})
            if rows:
                ANSWERS.append({'resources': list({r['resource_id'] for r in rows}), 'text': answer, 'expiresAt': now + TTL})
            del MEMORY[:-200]
            del ANSWERS[:-100]
            self.send({'mode': 'Extractive simulator — no LLM', 'tool': tool, 'toolResponse': result,
                       'answer': answer, 'contentReachedAgent': len(rows), 'freshAuthorization': True})


if __name__ == '__main__':
    # TTL applies even without subsequent browser activity.
    def maintenance():
        while True:
            time.sleep(5)
            with LOCK:
                expire()
    threading.Thread(target=maintenance, daemon=True).start()
    serve(AgentHandler, 8767)
