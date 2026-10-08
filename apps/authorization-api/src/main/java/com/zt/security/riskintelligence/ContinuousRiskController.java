package com.zt.security.riskintelligence;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1/security/agent-risk")
public class ContinuousRiskController {
    private final ContinuousRiskService service;
    ContinuousRiskController(ContinuousRiskService service){
        this.service=service;
    }

    @GetMapping("/overview")
    public Map<String,Object> overview(@RequestHeader("X-Tenant-Id") UUID tenant,
                                        @RequestParam(defaultValue="24") int windowHours){
        return service.overview(tenant, Math.max(1, Math.min(windowHours, 168)));
    }

    @GetMapping("/{agent}")
    public Map<String,Object> agent(@RequestHeader("X-Tenant-Id") UUID tenant,
                                    @PathVariable String agent,
                                    @RequestParam(defaultValue="24") int windowHours){
        return service.agent(tenant, agent, Math.max(1, Math.min(windowHours, 168)));
    }
}
