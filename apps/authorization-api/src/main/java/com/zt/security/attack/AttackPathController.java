package com.zt.security.attack;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/security/attack-paths")
public class AttackPathController {
    private final AttackPathService service;
    AttackPathController(AttackPathService service){
        this.service=service;
    }

    @GetMapping("/agents/{agent}")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','AGENT_MANAGER')")
    public Map<String,Object> assess(@RequestHeader("X-Tenant-Id") UUID tenant,
                                     @PathVariable String agent,
                                     @RequestParam(defaultValue="60") int windowMinutes,
                                     @RequestParam(defaultValue="8") int maxDepth,
                                     @RequestParam(defaultValue="false") boolean includeDenied) {
        return service.assess(tenant, agent, windowMinutes, maxDepth, includeDenied);
    }

    @PostMapping("/assess")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')")
    public Map<String,Object> assessPost(@RequestHeader("X-Tenant-Id") UUID tenant,
                                          @RequestParam String agent,
                                          @RequestParam(defaultValue="60") int windowMinutes,
                                          @RequestParam(defaultValue="8") int maxDepth,
                                          @RequestParam(defaultValue="false") boolean includeDenied) {
        return service.assess(tenant, agent, windowMinutes, maxDepth, includeDenied);
    }
}
