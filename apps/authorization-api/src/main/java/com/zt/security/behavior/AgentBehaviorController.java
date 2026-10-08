package com.zt.security.behavior;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.
annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import com.zt.security.common.TenantSession;
@RestController @RequestMapping("/v1/agents/behavior") public class AgentBehaviorController {
    final AgentBehaviorService service;
    final TenantSession session;
    AgentBehaviorController(AgentBehaviorService s,
    TenantSession ts){
        service=s;
        session=ts;
    }
 @PostMapping("/profiles") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AGENT_MANAGER')") @Transactional
public AgentBehaviorProfile save(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody AgentBehaviorProfile p){
     session.set(t);
     return service.save(t,
     p);
     }
 @GetMapping("/profiles") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR'" +
",'AGENT_MANAGER')") @Transactional(readOnly=
 true) public List<AgentBehaviorProfile> profiles(@RequestHeader("X-Tenant-Id") UUID t){
     session.set(t);
     return service.profiles(t);
     }
 @GetMapping("/{agent}/anomalies") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR'" +
",'AGENT_MANAGER')") @Transactional(readOnly=
 true) public List<AgentAnomaly> anomalies(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable String agent){
     session.set(t);
     return service.anomalies(t,
     agent);
     }
 @GetMapping("/{agent}/decisions") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR'" +
",'AGENT_MANAGER')") @Transactional(readOnly=
 true) public List<AdaptiveDecision> decisions(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable String agent){
     session.set(t);
     return service.decisions(t,
     agent);
     }
}
