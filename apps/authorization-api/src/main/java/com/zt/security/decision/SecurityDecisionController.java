package com.zt.security.decision;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.
annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import com.zt.security.common.TenantSession;
@RestController @RequestMapping("/v1/security/decisions") public class SecurityDecisionController {
    final ContinuousSecurityDecisionService service;
    final TenantSession session;
    SecurityDecisionController(ContinuousSecurityDecisionService s,TenantSession ts){
        service=s;
        session=ts;
        }
 @GetMapping @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')") @Transactional(readOnly=
 true) List<SecurityDecision> recent(@RequestHeader("X-Tenant-Id") UUID t){
     session.set(t);
     return service.recent(t);
     }
 @GetMapping("/assets") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR'" +
",'AGENT_MANAGER')") @Transactional(readOnly=
 true) List<SecurityAsset> assets(@RequestHeader("X-Tenant-Id") UUID t){
     session.set(t);
     return service.assets(t);
     }
 @PostMapping("/assets") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") @Transactional SecurityAsset
asset(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody SecurityAsset in){
     session.set(t);
     return service.upsertAsset(t,
     in);
     }
}
