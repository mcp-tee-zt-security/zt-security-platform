package com.zt.security.audit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/audit") public class AuditVerificationController {
    final AuditVerificationService service;
    AuditVerificationController(AuditVerificationService s){
        service=s;
        }
        @GetMapping("/verify") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')") public Map<String,
    Object> verify(@RequestHeader("X-Tenant-Id")UUID tenant){
        return service.verify(tenant);
    }
    }
