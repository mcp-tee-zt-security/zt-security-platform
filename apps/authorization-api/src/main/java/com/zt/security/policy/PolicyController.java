package com.zt.security.policy;
import com.zt.security.common.TenantSession;
import com.zt.security.common.WorkspaceSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.*;
@RestController @RequestMapping("/v1/policies") public class PolicyController {
    final PolicyRepository repo;
    final PolicyService service;
    final TenantSession session;
    final WorkspaceSession workspace;
    PolicyController(PolicyRepository r,
    PolicyService s,TenantSession ts,WorkspaceSession ws){
        repo=r;
        service=s;
        session=ts;
        workspace=ws;
        }
 record Upsert(String name,Integer version,Integer priority,String status,String policyText){
 }
 @GetMapping @Transactional(readOnly=true) List<Policy> list(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestHeader(value="X-Workspace-Id",required=false) UUID w){
     session.set(t);
     workspace.set(w);
     return service.active(t);
     }
 @GetMapping("/all") @Transactional(readOnly=true) List<Policy> all(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestHeader(value="X-Workspace-Id",required=false) UUID w){
     session.set(t);
     workspace.set(w);
     return repo.findAll().stream().filter(x->t.equals(x.getTenantId())).toList();
 }
 @PostMapping @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','POLICY_MANAGER')") @Transactional Policy create(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestHeader(value="X-Workspace-Id",required=false) UUID w,@RequestBody Upsert x){
     session.set(t);
     workspace.set(w);
     PolicyDsl d=PolicyDsl.parse(x.policyText());
     if(d.name()==null||d.effect()==null)throw new IllegalArgumentException("Invalid policy DSL");
     Policy p=new Policy();
     p.setId(UUID.randomUUID());
     p.setName(x.name()!=null?x.name():d.name());
     p.setVersion(x.version()==null?1:x.version());
     p.setPriority(x.priority()==null?d.priority():x.priority());
     p.setStatus(x.status()==null?"DRAFT":x.status());
     p.setEffect(d.effect());
     p.setPolicyText(x.policyText());
     p.setWorkspaceId(w);
     return service.save(t,
     p);
     }
 @PostMapping("/{id}/publish") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','POLICY_MANAGER')") @Transactional Policy publish(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestHeader(value="X-Workspace-Id",required=false) UUID w,@PathVariable UUID id){
     session.set(t);
     workspace.set(w);
     return service.publish(t,id);
     }
 @PostMapping("/rollback") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','POLICY_MANAGER')") @Transactional Map<String,Object> rollback(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestHeader(value="X-Workspace-Id",required=false) UUID w,@RequestParam String name,
 @RequestParam int version){
     session.set(t);
     workspace.set(w);
     service.rollback(t,
     name,version);
     return Map.of("rolledBackTo",version,"name",name);
     }
 @PostMapping("/validate") Map<String,Object> validate(@RequestBody Map<String,
 String> body){
     try{
         PolicyDsl d=PolicyDsl.parse(body.get("policyText"));
         Map<String,Object> out=new LinkedHashMap<>();
         out.put("valid",true);
         out.put("name",
         d.name());
         out.put("effect",d.effect());
         out.put("priority",d.priority());
         out.put("description",d.description());
         out.put("mode",d.mode());
         out.put("tags",
         d.tags());
         out.put("rules",d.rules());
         out.put("condition",d.condition()==null?null:d.condition().expression());
         return out;
         }
         catch(Exception e){
             return Map.of("valid",false,"error",String.valueOf(e.getMessage()));
     }
     }
}
