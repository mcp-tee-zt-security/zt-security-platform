package com.zt.security.incident;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import com.zt.security.common.TenantSession;
import java.util.*;
@RestController @RequestMapping("/v1/security/incidents") public class SecurityIncidentController {
    final SecurityIncidentRepository repo;
    final ObjectMapper mapper;
    final TenantSession session;
    SecurityIncidentController(SecurityIncidentRepository r,ObjectMapper m,
    TenantSession ts){
        repo=r;
        mapper=m;
        session=ts;
    }
 @GetMapping @Transactional(readOnly=true) @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')")
List<SecurityIncident> list(@RequestHeader("X-Tenant-Id") UUID t){
     session.set(t);
     return repo.findTop100ByTenantIdOrderByCreatedAtDesc(t);
 }
 @PostMapping @Transactional @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')") SecurityIncident
create(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody Map<String,Object> body){
     session.set(t);
     SecurityIncident i=new SecurityIncident();
     i.setTenantId(t);
     i.setTitle(String.valueOf(body.getOrDefault("title","Security incident")));
     i.setSeverity(String.valueOf(body.getOrDefault("severity","MEDIUM")));
     i.setPrincipalId(body.get("principalId")==null?null:String.valueOf(body.get("principalId")));
     if(body.get("sourceDecisionId")!=null)i.setSourceDecisionId(UUID.fromString(String.valueOf(body.
     get("sourceDecisionId"))));
     i.setSummary(String.valueOf(body.getOrDefault("summary","")));
     try{
         i.setEvidence(mapper.writeValueAsString(body.
         getOrDefault("evidence",
         List.of())));
         }
         catch(Exception ignored){
         }
         return repo.save(i);
         }
 @PostMapping("/{id}/status") @Transactional @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')")
SecurityIncident status(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@RequestParam String value){
     session.set(t);
     SecurityIncident i=repo.findById(id).orElseThrow();
     i.setStatus(value.toUpperCase(Locale.ROOT));
     return repo.save(i);
     }
}
