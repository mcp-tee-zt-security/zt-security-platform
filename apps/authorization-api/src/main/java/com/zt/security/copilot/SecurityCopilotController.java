package com.zt.security.copilot;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1/security/copilot")
public class SecurityCopilotController {
    private final SecurityCopilotService service;
    SecurityCopilotController(SecurityCopilotService service){
        this.service=service;
    }

    @GetMapping("/analysis")
    public Map<String,Object> analysis(@RequestHeader("X-Tenant-Id") UUID tenant,
                                       @RequestParam(defaultValue="120") int windowMinutes){
        return service.analyze(tenant, windowMinutes);
    }

    @PostMapping("/policy-recommendation")
    public Map<String,Object> recommendation(@RequestHeader("X-Tenant-Id") UUID tenant,
                                               @RequestBody(required=false) Map<String,Object> body){
        return service.recommendPolicy(tenant, body==null?null:String.valueOf(body.get("scenario")));
    }
}
