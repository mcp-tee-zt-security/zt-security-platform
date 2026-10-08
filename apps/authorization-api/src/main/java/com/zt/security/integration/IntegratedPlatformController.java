package com.zt.security.integration;

import com.zt.security.identity.Identity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1")
@Transactional
@PreAuthorize("hasAnyRole('PLATFORM','ADMIN','SERVICE_CLIENT')")
public class IntegratedPlatformController {
    private final GovernanceStore records;
    private final GovernedExecutionService governance;
    private final PlatformIntegrationService platform;
    public IntegratedPlatformController(GovernanceStore records,GovernedExecutionService governance,PlatformIntegrationService platform) {
        this.records=records;this.governance=governance;this.platform=platform;
    }
    private UUID tenant(HttpServletRequest request){return GovernanceStore.uuid(request.getHeader("X-Tenant-Id"));}
    private UUID workspace(HttpServletRequest request){String value=request.getHeader("X-Workspace-Id");return value==null?null:GovernanceStore.uuid(value);}

    @PostMapping("/evidence")
    public Map<String,Object> evidence(HttpServletRequest r,@RequestBody Map<String,Object> body){return governance.evidence(tenant(r),workspace(r),body);}
    @PostMapping("/evidence/{id}/verify")
    public Map<String,Object> verifyEvidence(HttpServletRequest r,@PathVariable String id){return governance.verifyEvidence(tenant(r),workspace(r),id);}
    @PostMapping("/execution/contracts")
    public Map<String,Object> contract(HttpServletRequest r,@RequestBody Map<String,Object> body){return governance.contract(tenant(r),workspace(r),body);}
    @PostMapping("/execution/contracts/{id}/execute")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','EXECUTOR')")
    public Map<String,Object> execute(HttpServletRequest r,@PathVariable String id){return governance.execute(tenant(r),workspace(r),id);}
    @PostMapping("/execution/{id}/verify")
    public Map<String,Object> verify(HttpServletRequest r,@PathVariable String id){return governance.verify(tenant(r),workspace(r),id);}
    @GetMapping("/execution/contracts/{id}")
    public Map<String,Object> contract(HttpServletRequest r,@PathVariable String id){return records.get(tenant(r),workspace(r),"CONTRACT",id,false);}
    @PostMapping("/identity/resolve")
    public Map<String,Object> identity(HttpServletRequest r,@RequestBody Map<String,Object> body){return platform.identity(tenant(r),workspace(r),body);}
    @PostMapping("/attestation/verify")
    public ResponseEntity<?> attestation(){return ResponseEntity.status(501).body(Map.of("error","ATTESTATION_VERIFIER_NOT_CONFIGURED","verified",false));}
    @PostMapping("/capabilities/check")
    public ResponseEntity<?> capability(){return ResponseEntity.status(501).body(Map.of("error","CAPABILITY_VERIFIER_NOT_CONFIGURED","allowed",false));}

    @PostMapping("/agents") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AGENT_MANAGER')")
    public Identity agent(HttpServletRequest r,@RequestBody Map<String,Object> body){return platform.registerAgent(tenant(r),workspace(r),body);}
    @PostMapping("/agents/{id}/missions") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AGENT_MANAGER')")
    public Map<String,Object> mission(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,Object> body){return platform.mission(tenant(r),workspace(r),id,body);}
    @PostMapping("/federation/organizations") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Map<String,Object> organization(HttpServletRequest r,@RequestBody Map<String,Object> body){return platform.register(tenant(r),workspace(r),"ORGANIZATION",body,"REGISTERED");}
    @PostMapping("/federation/trust") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Map<String,Object> trust(HttpServletRequest r,@RequestBody Map<String,Object> body){return platform.trust(tenant(r),workspace(r),body);}
    @PostMapping("/simulations")
    public Map<String,Object> simulation(HttpServletRequest r,@RequestBody Map<String,Object> body){return platform.register(tenant(r),workspace(r),"SIMULATION",body,"REGISTERED");}
    @PostMapping("/simulations/{id}/run")
    public Map<String,Object> simulate(HttpServletRequest r,@PathVariable String id,@RequestBody(required=false) Map<String,Object> body){return platform.simulate(tenant(r),workspace(r),id,body==null?Map.of():body);}
    @PostMapping("/kubernetes/policies") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Map<String,Object> kubernetes(HttpServletRequest r,@RequestBody Map<String,Object> body){
        var result=platform.register(tenant(r),workspace(r),"KUBERNETES_POLICY",body,"NOT_APPLIED");
        result.put("applied",false);result.put("reason","No Kubernetes apply connector installed");
        records.update(tenant(r),workspace(r),"KUBERNETES_POLICY",result);return result;
    }

    @GetMapping("/observability/events") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','SERVICE_CLIENT')")
    public List<Map<String,Object>> events(HttpServletRequest r,@RequestParam(required=false) String traceId,@RequestParam(defaultValue="100") int limit){return records.events(tenant(r),workspace(r),traceId,limit);}
    @GetMapping("/observability/metrics") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','SERVICE_CLIENT')")
    public Map<String,Object> metrics(HttpServletRequest r){
        return records.metrics(tenant(r),workspace(r));
    }
    @PostMapping("/operations/sre/incidents/{incidentId}/events") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','SOC_ANALYST')")
    public Map<String,Object> sre(HttpServletRequest r,@PathVariable String incidentId,@RequestParam(required=false) UUID tenantId,
            @RequestParam String eventType,@RequestParam String severity,@RequestParam String source,
            @RequestParam(required=false) String correlationKey,@RequestBody(required=false) String details){
        UUID tenant=tenant(r);
        if(tenantId!=null&&!tenant.equals(tenantId))throw new org.springframework.security.access.AccessDeniedException("tenant mismatch");
        Map<String,Object> value=new LinkedHashMap<>();value.put("incidentId",incidentId);value.put("eventType",eventType);
        value.put("severity",severity);value.put("source",source);value.put("correlationKey",correlationKey);
        value.put("details",details==null?"{}":details);
        value.put("eventTime",java.time.Instant.now().toString());
        var result=records.create(tenant,workspace(r),"SRE_EVENT",value);
        governance.event(tenant,workspace(r),"SRE_INCIDENT_EVENT",result);
        return result;
    }
    @GetMapping("/operations/sre/incidents/{incidentId}/events") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','SOC_ANALYST')")
    public List<Map<String,Object>> sreEvents(HttpServletRequest r,@PathVariable String incidentId){
        return records.listByField(tenant(r),workspace(r),"SRE_EVENT","incidentId",incidentId,500);
    }
}
