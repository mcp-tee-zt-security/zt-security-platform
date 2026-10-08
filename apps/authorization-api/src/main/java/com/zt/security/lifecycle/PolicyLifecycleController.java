package com.zt.security.lifecycle;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/policy-lifecycle") public class PolicyLifecycleController {
 final PolicyLifecycleService s;
 final CanaryGuardService canary;
 PolicyLifecycleController(PolicyLifecycleService x,
 CanaryGuardService c){
     s=x;
     canary=c;
 }
 record Propose(String source,String sourceRef,String name,Integer version,
 String policyText,String requestedBy,Integer canaryPercent,String commitSha){
 }
 record Approval(String approver,String comment){
 }
 @GetMapping("/changes") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','APPROVER')")
List<PolicyChangeRequest> changes(@RequestHeader("X-Tenant-Id") UUID t){
     return s.changes(t);
     }
 @PostMapping("/propose") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") PolicyChangeRequest
propose(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody Propose x){
     return s.propose(t,x.source(),x.sourceRef(),x.name(),
     x.version()==null?1:x.version(),x.policyText(),x.requestedBy(),x.canaryPercent()==null?0:x.canaryPercent(),
     x.commitSha());
     }
 @PostMapping("/changes/{id}/approve") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','APPROVER')")
PolicyChangeRequest approve(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@RequestBody(required=false) Approval body,@RequestParam(required=false) String approver){
     String who=body!=null&&body.approver()!=null?body.approver():approver;
     return s.approve(t,id,who,body==null?null:body.comment());
     }
 @PostMapping("/changes/{id}/publish") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
PolicyChangeRequest publish(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id){
     return s.publish(t,id);
 }
 @GetMapping("/deployments/{id}/observations") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','APPROVER')")
List<Map<String,
 Object>> observations(@RequestHeader("X-Tenant-Id") UUID t,@PathVariable UUID id){
     return canary.history(t,id);
     }
 @PostMapping("/deployments/{id}/evaluate") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','APPROVER')") Map<String,
 Object> evaluateCanary(@RequestHeader("X-Tenant-Id") UUID t,@PathVariable UUID id){
     return canary.evaluateNow(t,id);
     }
 @GetMapping("/deployments") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','APPROVER')")
List<PolicyDeployment> deployments(@RequestHeader("X-Tenant-Id") UUID t){
     return s.deployments(t);
     }
  @GetMapping("/changes/{id}/diff") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','APPROVER')") Map<String,
  Object> diff(@RequestHeader("X-Tenant-Id") UUID t,@PathVariable UUID id){
      return s.diff(t,id);
      }
 @PostMapping("/deployments/{id}/promote") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','APPROVER')") Map<String,
 Object> promote(@RequestHeader("X-Tenant-Id") UUID t,@PathVariable UUID id){
     s.promote(t,id);
     return Map.of("status","PROMOTED","deploymentId",id);
     }
 @PostMapping("/deployments/{id}/abort") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','APPROVER')") Map<String,
 Object> abort(@RequestHeader("X-Tenant-Id") UUID t,@PathVariable UUID id,
 @RequestParam(defaultValue="manual abort") String reason){
     s.abortCanary(t,
     id,reason);
     return Map.of("status","ABORTED","deploymentId",id);
     }
 @PostMapping("/rollback") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','APPROVER')") Map<String,
 Object> rollback(@RequestHeader("X-Tenant-Id") UUID t,@RequestParam String name,
 @RequestParam int version){
     s.rollback(t,name,version);
     return Map.of("status",
     "ROLLED_BACK","name",name,"version",version);
     }
 @PostMapping("/gitops/sources") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") GitOpsSource
source(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody GitOpsSource x){
     return s.registerGit(t,x);
 }
 record Sync(String commitSha,String policyText,String name,Integer version,String requestedBy){
 }
 @PostMapping("/gitops/sources/{id}/sync") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") Map<String,
 Object> sync(@RequestHeader("X-Tenant-Id") UUID t,@PathVariable UUID id,
 @RequestBody Sync x){
     return s.sync(t,id,x.commitSha(),x.policyText(),x.name(),
     x.version()==null?1:x.version(),x.requestedBy());
     }
}
