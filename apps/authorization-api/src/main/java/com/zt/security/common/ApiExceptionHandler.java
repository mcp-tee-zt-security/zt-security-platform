package com.zt.security.common;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestControllerAdvice public class ApiExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?
    > denied(AccessDeniedException e){
        return ResponseEntity.status(403).body(Map.of("timestamp",Instant.now(),
        "error","FORBIDDEN","message",e.getMessage()));
        }
        @ExceptionHandler(IllegalArgumentException.class)
ResponseEntity<?
        > bad(IllegalArgumentException e){
        return ResponseEntity.badRequest().body(Map.of("timestamp",Instant.now(),
        "error","BAD_REQUEST","message",e.getMessage()));
        }
        @ExceptionHandler(IllegalStateException.class)
ResponseEntity<?
        > conflict(IllegalStateException e){
        return ResponseEntity.status(409).body(Map.of("timestamp",Instant.now(),
        "error","CONFLICT","message",e.getMessage()));
        }
        }
