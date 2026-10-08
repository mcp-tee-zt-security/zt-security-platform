from __future__ import annotations

import json
from typing import Any, Iterator
from urllib.request import Request, urlopen


class EventStream:
    """Read Server-Sent Event replays from the security event fabric."""

    def __init__(
        self,
        base_url: str,
        api_key: str,
        tenant_id: str,
        workspace_id: str | None = None,
        timeout: int = 30,
    ):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.tenant_id = tenant_id
        self.workspace_id = workspace_id
        self.timeout = timeout

    def replay(self, path: str, *, limit: int = 100) -> Iterator[dict[str, Any]]:
        headers = {
            "x-api-key": self.api_key,
            "x-tenant-id": self.tenant_id,
        }
        if self.workspace_id:
            headers["x-workspace-id"] = self.workspace_id

        request = Request(
            f"{self.base_url}{path}?limit={limit}",
            headers=headers,
        )
        with urlopen(request, timeout=self.timeout) as response:
            for line in response:
                decoded = line.decode().strip()
                if decoded.startswith("data:"):
                    yield json.loads(decoded[5:].strip())
