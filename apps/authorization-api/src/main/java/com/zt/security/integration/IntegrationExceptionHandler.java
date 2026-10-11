package com.zt.security.integration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice(assignableTypes=IntegratedPlatformController.class)
public class IntegrationExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid(IllegalArgumentException exception){return ResponseEntity.badRequest().body(Map.of("message",exception.getMessage()==null?"Invalid execution request":exception.getMessage()));}
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> state(IllegalStateException exception){return ResponseEntity.status(409).body(Map.of("message",exception.getMessage()==null?"Execution state conflict":exception.getMessage()));}
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> denied(org.springframework.security.access.AccessDeniedException exception){return ResponseEntity.status(403).body(Map.of("message",exception.getMessage()));}
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException exception){
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("status",exception.getStatusCode().value(),
            "error",exception.getReason()==null?"Request failed":exception.getReason()));
    }
}
