package com.zt.security.event;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/events") public class SecurityEventController {
    final SecurityEventOutboxRepository repo;
    SecurityEventController(SecurityEventOutboxRepository r){
        repo=r;
        }
        @GetMapping List<SecurityEventOutbox> list(@RequestHeader("X-Tenant-Id") UUID t){
        return repo.findTop100ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc("PENDING",
        java.time.Instant.now()).stream().filter(x->t.equals(x.getTenantId())).toList();
    }
    }
