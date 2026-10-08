package com.zt.security.decision;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.
annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import com.zt.security.common.TenantSession;
@RestController @RequestMapping("/v1/security/decisions") public class SecurityDecisionEvidenceController {
    final SecurityDecisionExplainabilityService service;
    final TenantSession session;
    SecurityDecisionEvidenceController(SecurityDecisionExplainabilityService s,
    TenantSession ts){
        service=s;
        session=ts;
    }
 @GetMapping("/{id}/explain") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','APPROVER'" +
",'AGENT_MANAGER')") @Transactional(readOnly=
 true) Map<String,
 Object> explain(@RequestHeader("X-Tenant-Id") UUID tenant,@PathVariable UUID id){
     session.set(tenant);
     return service.explain(tenant,id);
     }
 @GetMapping("/{id}/evidence") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','APPROVER'" +
",'AGENT_MANAGER')") @Transactional(readOnly=
 true) List<SecurityDecisionEvidence> evidence(@RequestHeader("X-Tenant-Id") UUID tenant,
 @PathVariable UUID id){
     session.set(tenant);
     return service.evidence(tenant,
     id);
     }
}
