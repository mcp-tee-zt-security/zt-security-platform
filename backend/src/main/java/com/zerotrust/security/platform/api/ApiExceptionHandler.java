package com.zerotrust.security.platform.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(
            IllegalArgumentException error,
            HttpServletRequest request) {
        return error("BAD_REQUEST", error.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> internalError(
            Exception error,
            HttpServletRequest request) {
        return error("INTERNAL_ERROR", error.getMessage(), request);
    }

    private Map<String, Object> error(
            String code,
            String message,
            HttpServletRequest request) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "code", code,
                "message", message == null ? "request failed" : message,
                "path", request.getRequestURI());
    }
}
