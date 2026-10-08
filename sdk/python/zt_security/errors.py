from __future__ import annotations

from typing import Any


class ZtSecurityError(RuntimeError):
    """Base exception for the public SDK API."""
    workflow: dict[str, Any] | None = None


class ZtSecurityConfigurationError(ZtSecurityError, ValueError):
    """Invalid client configuration or request input."""


class ZtSecurityProtocolError(ZtSecurityError):
    """The server returned a response outside the supported API contract."""


class ZtSecurityHttpError(ZtSecurityError):
    """HTTP failure with structured metadata; credentials are redacted."""

    def __init__(
        self,
        status_code: int,
        message: str,
        request_id: str | None = None,
        *,
        code: str | None = None,
        body: Any = None,
        retry_after: float | None = None,
        idempotency_key: str | None = None,
    ):
        super().__init__(f"HTTP {status_code}: {message}")
        self.status_code = status_code
        self.message = message
        self.request_id = request_id
        self.code = code
        self.body = body
        self.retry_after = retry_after
        self.idempotency_key = idempotency_key


class ZtSecurityValidationError(ZtSecurityHttpError):
    """The server rejected the request as invalid."""


class ZtSecurityAuthenticationError(ZtSecurityHttpError):
    """Authentication failed (401)."""


class ZtSecurityPermissionError(ZtSecurityHttpError):
    """Permission was denied (403)."""


class ZtSecurityNotFoundError(ZtSecurityHttpError):
    """The scoped resource was not found (404)."""


class ZtSecurityConflictError(ZtSecurityHttpError):
    """State conflict, expired contract, or idempotency conflict (409)."""


class ZtSecurityRateLimitError(ZtSecurityHttpError):
    """Rate limited (429); inspect retry_after."""


class ZtSecurityUnsupportedError(ZtSecurityHttpError):
    """A server-side feature or connector is unavailable (501)."""


class ZtSecurityServerError(ZtSecurityHttpError):
    """Server failure (5xx)."""


class ZtSecurityTransportError(ZtSecurityError):
    """Network failure; a write may already have reached the server."""

    def __init__(
        self,
        message: str,
        *,
        ambiguous: bool = False,
        idempotency_key: str | None = None,
    ):
        super().__init__(message)
        self.ambiguous = ambiguous
        self.idempotency_key = idempotency_key


class ZtSecurityTimeout(ZtSecurityTransportError):
    """An HTTP connection or read exceeded its configured timeout."""

