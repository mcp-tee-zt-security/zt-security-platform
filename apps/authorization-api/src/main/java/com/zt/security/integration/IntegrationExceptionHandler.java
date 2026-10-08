package com.zt.security.integration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice(assignableTypes=IntegratedPlatformController.class)
public class IntegrationExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException exception){
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("status",exception.getStatusCode().value(),
            "error",exception.getReason()==null?"Request failed":exception.getReason()));
    }
}
