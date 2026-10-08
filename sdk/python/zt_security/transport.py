from __future__ import annotations

import json as json_module
import math
import random
import time
from collections.abc import Callable, Mapping
from copy import deepcopy
from email.utils import parsedate_to_datetime
from typing import Any
from urllib.parse import urlsplit
from uuid import UUID

import requests

from ._version import __version__
from .errors import (
    ZtSecurityAuthenticationError,
    ZtSecurityConfigurationError,
    ZtSecurityConflictError,
    ZtSecurityHttpError,
    ZtSecurityNotFoundError,
    ZtSecurityPermissionError,
    ZtSecurityProtocolError,
    ZtSecurityRateLimitError,
    ZtSecurityServerError,
    ZtSecurityTimeout,
    ZtSecurityTransportError,
    ZtSecurityUnsupportedError,
    ZtSecurityValidationError,
)

TokenProvider = Callable[[], str]
Timeout = float | tuple[float, float]


class _HeaderAuthentication(requests.auth.AuthBase):
    def __call__(self, request: requests.PreparedRequest) -> requests.PreparedRequest:
        # Keep the SDK's explicit headers authoritative over .netrc/session auth.
        return request


def valid_uuid(value: object, name: str) -> str:
    if not isinstance(value, str):
        raise ZtSecurityConfigurationError(f"{name} must be a UUID string")
    try:
        return str(UUID(value))
    except (ValueError, AttributeError) as error:
        raise ZtSecurityConfigurationError(f"{name} must be a UUID string") from error


def nonempty(value: object, name: str) -> str:
    if not isinstance(value, str) or not value.strip() or "\r" in value or "\n" in value:
        raise ZtSecurityConfigurationError(f"{name} must be a nonempty string without line breaks")
    return value


def _header_value(value: object, name: str) -> str:
    text = nonempty(value, name)
    if text != text.strip():
        raise ZtSecurityConfigurationError(f"{name} cannot have surrounding whitespace")
    try:
        text.encode("latin-1")
    except UnicodeEncodeError:
        raise ZtSecurityConfigurationError(f"{name} contains unsupported HTTP header characters") from None
    return text


def _positive(value: float, name: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ZtSecurityConfigurationError(f"{name} must be a positive finite number")
    if not math.isfinite(value) or value <= 0:
        raise ZtSecurityConfigurationError(f"{name} must be a positive finite number")
    return float(value)


class Transport:
    """Synchronous session transport. One client per thread; no implicit write replay."""

    RETRYABLE_STATUS_CODES = frozenset({429, 502, 503, 504})
    _PROTECTED_HEADERS = frozenset(
        {"authorization", "x-api-key", "x-client-id", "x-tenant-id", "x-workspace-id",
         "idempotency-key", "host", "content-length"}
    )

    def __init__(
        self,
        base_url: str,
        api_key: str | None = None,
        tenant_id: str | None = None,
        workspace_id: str | None = None,
        timeout: Timeout = 10,
        retries: int = 2,
        *,
        client_id: str | None = None,
        bearer_token: str | TokenProvider | None = None,
        session: requests.Session | None = None,
        max_retry_delay: float = 30,
    ):
        if not isinstance(base_url, str):
            raise ZtSecurityConfigurationError("base_url must be an HTTP(S) URL")
        try:
            parsed = urlsplit(base_url)
            port = parsed.port
        except ValueError as error:
            raise ZtSecurityConfigurationError("Invalid base_url") from error
        if (
            parsed.scheme not in {"http", "https"} or not parsed.hostname
            or parsed.username is not None or parsed.password is not None
            or parsed.query or parsed.fragment or "\\" in base_url
            or any(character.isspace() for character in base_url)
        ):
            raise ZtSecurityConfigurationError("base_url must be HTTP(S), without credentials/query/fragment")
        if port is not None and port <= 0:
            raise ZtSecurityConfigurationError("Invalid base_url port")
        if (api_key is None) == (bearer_token is None):
            raise ZtSecurityConfigurationError("Provide exactly one of api_key or bearer_token")
        if api_key is not None:
            _header_value(api_key, "api_key")
        if isinstance(bearer_token, str):
            _header_value(bearer_token, "bearer_token")
        elif bearer_token is not None and not callable(bearer_token):
            raise ZtSecurityConfigurationError("bearer_token must be a string or callable")
        if client_id is not None:
            _header_value(client_id, "client_id")
            if api_key is None:
                raise ZtSecurityConfigurationError("client_id requires API-key authentication")
        if isinstance(retries, bool) or not isinstance(retries, int) or not 0 <= retries <= 10:
            raise ZtSecurityConfigurationError("retries must be an integer from 0 to 10")
        if isinstance(timeout, tuple):
            if len(timeout) != 2:
                raise ZtSecurityConfigurationError("timeout must be seconds or (connect, read)")
            self.timeout: Timeout = (
                _positive(timeout[0], "connect timeout"), _positive(timeout[1], "read timeout")
            )
        else:
            self.timeout = _positive(timeout, "timeout")
        self.base_url = base_url.rstrip("/")
        self.tenant_id = valid_uuid(tenant_id, "tenant_id")
        self.workspace_id = valid_uuid(workspace_id, "workspace_id") if workspace_id is not None else None
        self.retries = retries
        self.max_retry_delay = _positive(max_retry_delay, "max_retry_delay")
        self.headers = {
            "X-Tenant-Id": self.tenant_id,
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": f"zt-security-python/{__version__}",
        }
        if api_key is not None:
            self.headers["X-API-Key"] = api_key
        if client_id is not None:
            self.headers["X-Client-Id"] = client_id
        if self.workspace_id is not None:
            self.headers["X-Workspace-Id"] = self.workspace_id
        self._bearer_token = bearer_token
        self._session = session if session is not None else requests.Session()
        self._owns_session = session is None
        self._closed = False

    def close(self) -> None:
        if not self._closed:
            self._closed = True
            if self._owns_session:
                self._session.close()

    def __enter__(self) -> Transport:
        if self._closed:
            raise ZtSecurityConfigurationError("Client is closed")
        return self

    def __exit__(self, *args: Any) -> None:
        self.close()

    @staticmethod
    def _retry_after(value: str | None) -> float | None:
        if value is None:
            return None
        try:
            seconds = float(value)
            return max(0.0, seconds) if math.isfinite(seconds) else None
        except ValueError:
            try:
                parsed = parsedate_to_datetime(value)
                if parsed.tzinfo is None:
                    return None
                return max(0.0, parsed.timestamp() - time.time())
            except (TypeError, ValueError, OverflowError):
                return None

    @staticmethod
    def _redact(value: Any, secrets: tuple[str, ...]) -> Any:
        if isinstance(value, str):
            for secret in secrets:
                if secret:
                    value = value.replace(secret, "[REDACTED]")
            return value
        if isinstance(value, list):
            return [Transport._redact(item, secrets) for item in value]
        if isinstance(value, dict):
            return {key: Transport._redact(item, secrets) for key, item in value.items()}
        return value

    def _http_error(
        self, response: requests.Response, secrets: tuple[str, ...], idempotency_key: str | None
    ) -> ZtSecurityHttpError:
        try:
            payload = response.json() if response.content else None
        except ValueError:
            payload = {"message": response.text[:2048]}
        payload = self._redact(payload, secrets)
        code = payload.get("error") if isinstance(payload, dict) else None
        if not isinstance(code, str):
            code = None
        detail = payload.get("message") if isinstance(payload, dict) else None
        if not isinstance(detail, str) or not detail:
            detail = code or response.reason or "Request failed"
        detail = self._redact(detail, secrets)[:2048]
        status = response.status_code
        error_type = {
            400: ZtSecurityValidationError, 422: ZtSecurityValidationError,
            401: ZtSecurityAuthenticationError, 403: ZtSecurityPermissionError,
            404: ZtSecurityNotFoundError, 409: ZtSecurityConflictError,
            429: ZtSecurityRateLimitError, 501: ZtSecurityUnsupportedError,
        }.get(status, ZtSecurityServerError if status >= 500 else ZtSecurityHttpError)
        return error_type(
            status, detail, self._redact(response.headers.get("X-Request-Id"), secrets),
            code=code, body=payload,
            retry_after=self._retry_after(response.headers.get("Retry-After")),
            idempotency_key=idempotency_key,
        )

    def request(
        self,
        method: str,
        path: str,
        json: Any = None,
        headers: Mapping[str, str] | None = None,
        idempotency_key: str | None = None,
        *,
        params: Mapping[str, Any] | None = None,
    ) -> Any:
        if self._closed:
            raise ZtSecurityConfigurationError("Client is closed")
        method = nonempty(method, "method").upper()
        if method not in {"GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"}:
            raise ZtSecurityConfigurationError("Unsupported HTTP method")
        if (
            not isinstance(path, str) or not path.startswith("/") or path.startswith("//")
            or "\\" in path or any(character.isspace() for character in path)
            or urlsplit(path).fragment
        ):
            raise ZtSecurityConfigurationError("path must be a relative API path starting with /")
        if idempotency_key is not None:
            _header_value(idempotency_key, "idempotency_key")
            if len(idempotency_key) > 200:
                raise ZtSecurityConfigurationError("idempotency_key must be at most 200 characters")
        if json is not None:
            try:
                json_module.dumps(json, allow_nan=False)
                json = deepcopy(json)
            except (TypeError, ValueError) as error:
                raise ZtSecurityConfigurationError("Request body must contain finite JSON values") from error
        extra_headers: dict[str, str] = {}
        for key, value in (headers or {}).items():
            nonempty(key, "header name")
            _header_value(value, "header value")
            if key.lower() in self._PROTECTED_HEADERS:
                raise ZtSecurityConfigurationError(f"Cannot override scoped/authentication header {key}")
            extra_headers[key] = value
        retry_safe = method in {"GET", "HEAD"} or (
            method == "POST" and path == "/v1/actions/evaluate" and idempotency_key is not None
        )
        attempts = self.retries + 1 if retry_safe else 1
        for attempt in range(attempts):
            request_headers = {**self.headers, **extra_headers}
            if self._bearer_token is not None:
                try:
                    token = self._bearer_token() if callable(self._bearer_token) else self._bearer_token
                except Exception:
                    raise ZtSecurityConfigurationError("Bearer token provider failed") from None
                request_headers["Authorization"] = "Bearer " + _header_value(token, "bearer_token")
            if idempotency_key is not None:
                request_headers["Idempotency-Key"] = idempotency_key
            secrets = tuple(
                value.removeprefix("Bearer ")
                for key, value in request_headers.items()
                if key.lower() in {"authorization", "x-api-key"}
            )
            response: requests.Response | None = None
            retry_after = None
            try:
                response = self._session.request(
                    method, self.base_url + path, headers=request_headers, json=json,
                    params=params, timeout=self.timeout, allow_redirects=False,
                    auth=_HeaderAuthentication(),
                )
            except requests.Timeout as error:
                failure: ZtSecurityErrorLike = ZtSecurityTimeout(
                    "HTTP connection/read timed out; a write may already have been processed",
                    ambiguous=method not in {"GET", "HEAD"}, idempotency_key=idempotency_key,
                )
                cause: Exception | None = error
            except requests.ConnectionError as error:
                failure = ZtSecurityTransportError(
                    "HTTP connection failed; a write may already have been processed",
                    ambiguous=method not in {"GET", "HEAD"}, idempotency_key=idempotency_key,
                )
                cause = error
                if isinstance(error, requests.exceptions.SSLError):
                    raise failure from error
            except requests.RequestException as error:
                raise ZtSecurityTransportError(
                    "HTTP transport failed", ambiguous=method not in {"GET", "HEAD"},
                    idempotency_key=idempotency_key,
                ) from error
            else:
                cause = None
                try:
                    if 200 <= response.status_code < 300:
                        if not response.content:
                            return {}
                        try:
                            decoded = response.json()
                            json_module.dumps(decoded, allow_nan=False)
                            return decoded
                        except (TypeError, ValueError) as error:
                            raise ZtSecurityProtocolError("Successful response is not valid JSON") from error
                    if 300 <= response.status_code < 400:
                        raise ZtSecurityProtocolError("Unexpected redirect; credentials were not forwarded")
                    failure = self._http_error(response, secrets, idempotency_key)
                    retry_after = failure.retry_after
                    if response.status_code not in self.RETRYABLE_STATUS_CODES:
                        raise failure
                finally:
                    response.close()
            if attempt + 1 >= attempts:
                raise failure from cause
            delay = min(self.max_retry_delay, 0.25 * (2**attempt) + random.uniform(0, 0.1))
            if retry_after is not None:
                # Never retry before the server's requested delay. Long waits return control to callers.
                if retry_after > self.max_retry_delay:
                    raise failure from cause
                delay = max(delay, retry_after)
            time.sleep(delay)
        raise ZtSecurityTransportError("Request failed")

    def post(self, path: str, body: Any = None, **kwargs: Any) -> Any:
        return self.request("POST", path, json=body, **kwargs)

    def get(self, path: str, **kwargs: Any) -> Any:
        return self.request("GET", path, **kwargs)


ZtSecurityErrorLike = ZtSecurityHttpError | ZtSecurityTransportError

