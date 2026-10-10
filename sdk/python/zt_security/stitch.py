from __future__ import annotations

from typing import TYPE_CHECKING, Any
from urllib.parse import quote
from .transport import nonempty, valid_uuid
from .errors import ZtSecurityProtocolError, ZtSecurityError

if TYPE_CHECKING:
    from .client import ZtSecurityClient


class StitchAccessDenied(ZtSecurityError):
    """A denied retrieval must not be passed to an LLM as usable context."""


class StitchRetrieval:
    """Session is injected by trusted application code, not supplied by the model."""
    ROOT = '/v1/integrations/stitch'

    def __init__(self, client: ZtSecurityClient, session_id: str | None = None):
        self.client = client
        self.session_id = valid_uuid(session_id, 'session_id') if session_id else None

    def for_session(self, session_id: str) -> StitchRetrieval:
        return StitchRetrieval(self.client, session_id)

    def delegate(self, ai_subject: str) -> dict[str, Any]:
        return self.client._post(self.ROOT + '/sessions', {'aiSubject': nonempty(ai_subject, 'ai_subject')})

    def revoke(self, session_id: str) -> dict[str, Any]:
        return self.client._object(self.client.http.request('DELETE', self.ROOT + '/sessions/' + valid_uuid(session_id, 'session_id')))

    def _read(self, operation: str, body: dict[str, Any]) -> list[dict[str, Any]]:
        if self.session_id:
            body['sessionId'] = self.session_id
        result = self.client._post(self.ROOT + '/' + operation, body)
        if result.get('decision') == 'DENY':
            raise StitchAccessDenied('ZT denied this retrieval; no source content was returned')
        if result.get('decision') != 'ALLOW' or not isinstance(result.get('results'), list) or any(not isinstance(x, dict) for x in result['results']):
            raise ZtSecurityProtocolError('Invalid Stitch retrieval response')
        return result['results']

    def read_document(self, resource_id: str) -> list[dict[str, Any]]:
        return self._read('retrieve', {'resourceId': nonempty(resource_id, 'resource_id')})

    def list_children(self, resource_id: str) -> list[dict[str, Any]]:
        return self._read('children', {'resourceId': nonempty(resource_id, 'resource_id')})

    def search(self, query: str, *, query_embedding: list[float] | None = None) -> list[dict[str, Any]]:
        body: dict[str, Any] = {'query': nonempty(query, 'query')}
        if query_embedding is not None:
            body['queryEmbedding'] = query_embedding
        return self._read('search', body)

    def sync_resource(self, resource: dict[str, Any]) -> dict[str, Any]:
        return self.client._post(self.ROOT + '/resources', resource)

    def sync_subject(self, subject: dict[str, Any]) -> dict[str, Any]:
        return self.client._post(self.ROOT + '/subjects', subject)

    def source_state(self) -> dict[str, Any]:
        return self.client._object(self.client.http.request('GET', self.ROOT + '/source-state'))

    def commit_source_snapshot(self, acl_version: int, *, ready: bool = True) -> dict[str, Any]:
        return self.client._post(self.ROOT + '/source-state', {'ready': ready, 'expectedAclVersion': acl_version})

    def delete_resource(self, resource_id: str) -> dict[str, Any]:
        return self.client._object(self.client.http.request('DELETE', self.ROOT + '/resources/' + quote(nonempty(resource_id, 'resource_id'), safe='')))

    def pending_deletions(self) -> list[dict[str, Any]]:
        result = self.client.http.request('GET', self.ROOT + '/deletion-events')
        if not isinstance(result, list):
            raise ZtSecurityProtocolError('Deletion event list expected')
        return result

    def acknowledge_deletion(self, event_id: str, receipt_id: str) -> dict[str, Any]:
        return self.client._post(self.ROOT + '/deletion-events/' + valid_uuid(event_id, 'event_id') + '/ack', {'receiptId': nonempty(receipt_id, 'receipt_id')})
