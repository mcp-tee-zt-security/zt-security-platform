package com.zt.security.llm;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.
StreamingResponseBody;
import java.util.*;
@RestController @RequestMapping("/v1/security/private-llm") public class PrivateLlmController {
    private final PrivateLlmService service;
    public PrivateLlmController(PrivateLlmService service){
        this.service=service;
        }
 @PostMapping("/health") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')") public Map<String,
 Object> health(@RequestHeader("X-Tenant-Id")UUID t,@RequestBody Map<String,
 Object>b){
     return service.health(t,b);
 }
 @PostMapping("/chat") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") public Map<String,
 Object> chat(@RequestHeader("X-Tenant-Id")UUID t,@RequestBody Map<String,
 Object>b){
     return service.chat(t,b);
 }
 @PostMapping(value="/chat/stream",produces="text/event-stream") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
public ResponseEntity<StreamingResponseBody> stream(@RequestHeader("X-Tenant-Id")UUID t,
 @RequestBody Map<String,Object>b){
     return ResponseEntity.ok().header("Cache-Control",
     "no-cache").header("X-Accel-Buffering","no").body(service.stream(t,b));
 }
}
