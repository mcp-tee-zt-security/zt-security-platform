package com.zt.sdk;

public class ZtSecurityException extends RuntimeException {
    public ZtSecurityException(String message, Throwable cause) {
        super(message, cause);
    }

    public ZtSecurityException(String message) {
        super(message);
    }
}
