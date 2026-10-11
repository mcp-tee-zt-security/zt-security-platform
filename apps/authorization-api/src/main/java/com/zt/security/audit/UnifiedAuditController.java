package com.zt.security.audit;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.*;

@RestController @RequestMapping("/v1/audit/events") @PreAuthorize("isAuthenticated()")
public class UnifiedAuditController {
    private final UnifiedAuditService service;
    public UnifiedAuditController(UnifiedAuditService service){this.service=service;}
    @GetMapping public Object list(@RequestHeader("X-Tenant-Id") UUID tenant,@RequestHeader("X-Workspace-Id") UUID workspace,
            @RequestParam(defaultValue="ALL") String kind,@RequestParam(defaultValue="ALL") String decision,
            @RequestParam(defaultValue="") String subject,@RequestParam(required=false) String cursor,
            @RequestParam(defaultValue="50") int limit,Authentication auth){
        return service.list(tenant,workspace,kind,decision,subject,cursor,limit,auth);
    }
    @GetMapping("/{kind}/{id}") public Object detail(@RequestHeader("X-Tenant-Id") UUID tenant,@RequestHeader("X-Workspace-Id") UUID workspace,
            @PathVariable String kind,@PathVariable UUID id,Authentication auth){return service.detail(tenant,workspace,kind,id,auth);}
}
