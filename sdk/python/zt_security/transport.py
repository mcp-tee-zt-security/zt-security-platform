from __future__ import annotations

import time
from typing import Any
import uuid

import requests

from .errors import ZtSecurityHttpError, ZtSecurityTimeout


class Transport:
    """HTTP transport with bounded retries and idempotency support."""

    RETRYABLE_STATUS_CODES = {429, 502, 503, 504}

    def __init__(
        self,
        base_url: str,
        api_key: str,
        tenant_id: str,
        workspace_id: str | None = None,
        timeout: int = 10,
        retries: int = 2,
    ):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.retries = max(0, retries)
        self.headers = {
            "X-API-Key": api_key,
            "X-Tenant-Id": tenant_id,
            "Content-Type": "application/json",
            "User-Agent": "zt-security-python/2.0",
        }
        if workspace_id:
            self.headers["X-Workspace-Id"] = workspace_id

    def request(
        self,
        method: str,
        path: str,
        json: Any = None,
        headers: dict[str, str] | None = None,
        idempotency_key: str | None = None,
    ) -> dict[str, Any]:
        request_headers = {**self.headers, **(headers or {})}
        if idempotency_key:
            request_headers["Idempotency-Key"] = idempotency_key

        last_error: Exception | requests.Response | None = None
        for attempt in range(self.retries + 1):
            try:
                response = requests.request(
                    method,
                    self.base_url + path,
                    headers=request_headers,
                    json=json,
                    timeout=self.timeout,
                )
                if response.ok:
                    return response.json() if response.content else {}
                if response.status_code not in self.RETRYABLE_STATUS_CODES:
                    raise ZtSecurityHttpError(
                        response.status_code,
                        response.text,
                        response.headers.get("X-Request-Id"),
                    )
                last_error = response
            except requests.Timeout as error:
                last_error = error

            if attempt < self.retries:
                time.sleep(0.25 * (2**attempt))

        if isinstance(last_error, requests.Timeout):
            raise ZtSecurityTimeout(str(last_error))
        if isinstance(last_error, requests.Response):
            raise ZtSecurityHttpError(
                last_error.status_code,
                last_error.text,
                last_error.headers.get("X-Request-Id"),
            )
        raise ZtSecurityTimeout("Request failed without a response")

    def post(self, path: str, body: Any = None, **kwargs: Any) -> dict[str, Any]:
        return self.request("POST", path, json=body, **kwargs)

    def get(self, path: str, **kwargs: Any) -> dict[str, Any]:
        return self.request("GET", path, **kwargs)
