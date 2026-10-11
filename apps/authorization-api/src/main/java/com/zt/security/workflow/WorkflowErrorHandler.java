package com.zt.security.workflow;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice(basePackages="com.zt.security.workflow")
public class WorkflowErrorHandler {
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Workflow request failed":e.getReason()));}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()==null?"Invalid workflow request":e.getMessage()));}
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> state(IllegalStateException e){return ResponseEntity.status(409).body(Map.of("message",e.getMessage()==null?"Workflow state conflict":e.getMessage()));}
    @ExceptionHandler(AccessDeniedException.class) public ResponseEntity<?> denied(AccessDeniedException e){return ResponseEntity.status(403).body(Map.of("message",e.getMessage()));}
}
