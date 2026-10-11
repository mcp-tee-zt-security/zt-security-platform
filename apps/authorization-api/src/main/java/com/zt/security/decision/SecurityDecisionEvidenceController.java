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
    @org.springframework.beans.factory.annotation.Autowired private SecurityDecisionRepository decisions;
    private void requireScope(UUID tenant,UUID workspace,UUID id){if(workspace!=null&&decisions.countInWorkspace(tenant,workspace,id)==0)throw new org.springframework.security.access.AccessDeniedException("Decision does not belong to selected workspace");}
    SecurityDecisionEvidenceController(SecurityDecisionExplainabilityService s,
    TenantSession ts){
        service=s;
        session=ts;
    }
 @GetMapping("/{id}/explain") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','APPROVER'" +
",'AGENT_MANAGER')") @Transactional(readOnly=
 true) Map<String,
 Object> explain(@RequestHeader("X-Tenant-Id") UUID tenant,@RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,@PathVariable UUID id){
     session.set(tenant);
     requireScope(tenant,workspace,id);
     return service.explain(tenant,id);
     }
 @GetMapping("/{id}/evidence") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','APPROVER'" +
",'AGENT_MANAGER')") @Transactional(readOnly=
 true) List<SecurityDecisionEvidence> evidence(@RequestHeader("X-Tenant-Id") UUID tenant,
 @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,@PathVariable UUID id){
     session.set(tenant);
     requireScope(tenant,workspace,id);
     return service.evidence(tenant,
     id);
     }
}
