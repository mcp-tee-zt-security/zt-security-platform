from __future__ import annotations

from collections.abc import Iterator
from typing import TYPE_CHECKING

from .errors import ZtSecurityConfigurationError
from .models import JsonObject, SecurityEvent
from .transport import Timeout, TokenProvider

if TYPE_CHECKING:
    from .client import ZtSecurityClient


class EventReplay:
    """One bounded JSON snapshot, newest first. This is not a live SSE stream."""

    def __init__(self, client: ZtSecurityClient):
        self._client = client

    def replay(self, *, trace_id: str | None = None, limit: int = 100) -> Iterator[SecurityEvent]:
        yield from self._client.replay_events_typed(trace_id, limit)


class EventStream:
    """Compatibility name for JSON replay; the integrated server does not provide SSE."""

    def __init__(
        self,
        base_url: str,
        api_key: str | None = None,
        tenant_id: str | None = None,
        workspace_id: str | None = None,
        timeout: Timeout = 30,
        *,
        client_id: str | None = None,
        bearer_token: str | TokenProvider | None = None,
    ):
        from .client import ZtSecurityClient

        self._client = ZtSecurityClient(
            base_url, api_key, tenant_id, workspace_id, timeout,
            client_id=client_id, bearer_token=bearer_token,
        )

    def replay(
        self, path: str = "/v1/observability/events", *, limit: int = 100,
        trace_id: str | None = None,
    ) -> Iterator[JsonObject]:
        if path != "/v1/observability/events":
            raise ZtSecurityConfigurationError("JSON replay supports /v1/observability/events only")
        yield from self._client.replay_events(trace_id, limit)

    def close(self) -> None:
        self._client.close()

    def __enter__(self) -> EventStream:
        self._client.__enter__()
        return self

    def __exit__(self, *args: object) -> None:
        self.close()

