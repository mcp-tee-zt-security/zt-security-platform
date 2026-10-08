package com.zt.sdk;

public final class ZtSecurityHttpException extends ZtSecurityException {
    public final int status;
    public final String requestId;

    public ZtSecurityHttpException(int status, String message, String requestId) {
        super("HTTP " + status + ": " + message);
        this.status = status;
        this.requestId = requestId;
    }
}
