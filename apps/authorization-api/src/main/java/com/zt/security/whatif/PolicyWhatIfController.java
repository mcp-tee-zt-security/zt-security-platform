package com.zt.security.whatif;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1/security/what-if")
public class PolicyWhatIfController {
    private final PolicyWhatIfService service;
    public PolicyWhatIfController(PolicyWhatIfService service){
        this.service=service;
    }
    public record Request(String policyText, List<PolicyWhatIfService.Scenario> scenarios){
    }
    @PostMapping("/simulate")
    public Map<String,Object> simulate(@RequestHeader("X-Tenant-Id") UUID tenant,@RequestBody Request request){
        if(request==null || request.policyText()==null || request.policyText().isBlank()) throw new
IllegalArgumentException("policyText is required");
        return service.simulate(tenant,request.policyText(),request.scenarios());
    }
}
