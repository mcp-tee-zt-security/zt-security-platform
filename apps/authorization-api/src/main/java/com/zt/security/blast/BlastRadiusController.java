package com.zt.security.blast;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1/security/blast-radius")
public class BlastRadiusController {
    private final BlastRadiusService service;
    BlastRadiusController(BlastRadiusService service){
        this.service=service;
    }
    @GetMapping("/agents/{agent}")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','AGENT_MANAGER')")
    public Map<String,Object> assess(@RequestHeader("X-Tenant-Id") UUID tenant,@PathVariable String agent,
                                     @RequestParam(defaultValue="120") int windowMinutes,
                                     @RequestParam(defaultValue="12") int maxDepth,
                                     @RequestParam(defaultValue="false") boolean includeDenied){
        return service.assess(tenant,agent,windowMinutes,maxDepth,includeDenied);
    }
    @GetMapping("/agents/{agent}/history")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','AGENT_MANAGER')")
    public List<Map<String,Object>> history(@RequestHeader("X-Tenant-Id") UUID tenant,
    @PathVariable String agent){
        return service.history(tenant,agent);
    }
}
