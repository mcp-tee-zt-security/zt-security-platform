package com.zt.security.risk;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/v1/security/risk")
public class RiskController {
    private final RiskEngine risk;
    private final IncrementalRiskService incremental;
    public RiskController(RiskEngine risk,IncrementalRiskService incremental){
        this.risk=risk;
        this.incremental=incremental;
        }
    record RiskRequest(Map<String,Object> context){
    }
    record DeltaRequest(int windowMinutes,Set<String> changedNodeIds){
    }
    @PostMapping("/score") Map<String,Object> score(@RequestHeader("X-Tenant-Id") UUID tenant,
    @RequestBody RiskRequest x){
        var r=risk.score(tenant,risk.fingerprint(x.context()),
        x.context());
        return Map.of("score",r.score(),"level",r.level(),"evidence",
        r.evidence());
        }
    @PostMapping("/incremental") Map<String,Object> delta(@RequestHeader("X-Tenant-Id") UUID tenant,
    @RequestBody DeltaRequest x){
        return incremental.calculateDelta(tenant,
        x.windowMinutes(),x.changedNodeIds());
        }
}
