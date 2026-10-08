class ZtSecurityError(RuntimeError):
    """Base exception for the ZT Security SDK."""


class ZtSecurityHttpError(ZtSecurityError):
    """Raised when the backend returns a non-retryable HTTP error."""

    def __init__(self, status_code: int, message: str, request_id: str | None = None):
        super().__init__(f"HTTP {status_code}: {message}")
        self.status_code = status_code
        self.request_id = request_id


class ZtSecurityTimeout(ZtSecurityError):
    """Raised when an SDK request exceeds its timeout."""
