package com.zt.security.approval;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import com.zt.security.common.TenantSession;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/v1/approvals") public class ApprovalController {
    final ApprovalRepository repo;
    final TenantSession tenantSession;
    final com.zt.security.integration.GovernanceStore records;
    ApprovalController(ApprovalRepository r,
    TenantSession ts,com.zt.security.integration.GovernanceStore records){
        repo=r;
        tenantSession=ts;
        this.records=records;
    }
 record Create(UUID requestId,String approverId,String reason,String payload,Integer ttlMinutes,String approvalType,
 Map<String,Object> decision,Map<String,Object> policyDecision,String action,String resource){
 }
 @GetMapping @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','APPROVER')") @Transactional(readOnly=
 true) List<Approval> list(@RequestHeader("X-Tenant-Id") UUID t){
     tenantSession.set(t);
     var rows=repo.findByTenantIdOrderByCreatedAtDesc(t);
     rows.stream().filter(a->"PENDING".equals(a.getStatus())&&a.getExpiresAt()!=
     null&&a.getExpiresAt().isBefore(Instant.now())).forEach(a->a.setStatus("EXPIRED"));
     return rows;
     }
 @PostMapping @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") @Transactional Approval
create(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,
 @RequestBody Create x){
     records.scope(t,workspace);
     UUID requestId=x.requestId();
     Map<String,Object> decision=x.decision()!=null?x.decision():x.policyDecision();
     if(decision!=null){
         var stored=records.get(t,workspace,"DECISION",String.valueOf(decision.get("id")),false);
         requestId=com.zt.security.integration.GovernanceStore.uuid(stored.get("requestId"));
         if(!"STEP_UP".equals(stored.get("decision")))throw new IllegalArgumentException("Decision does not require approval");
         var existing=repo.findByTenantIdAndRequestIdOrderByCreatedAtDesc(t,requestId);
         if(!existing.isEmpty())return existing.get(0);
     }
     if(requestId==null)throw new IllegalArgumentException("requestId or persisted policy decision is required");
     Approval a=new Approval();
     a.setId(UUID.randomUUID());
     a.setTenantId(t);
     a.setRequestId(requestId);
     a.setApproverId(x.approverId());
     a.setReason(x.reason());
     a.setPayload(x.payload()==null?"{}":x.payload());
     a.setApprovalType(x.approvalType()==null?"HUMAN":x.approvalType());
     a.setExpiresAt(Instant.now().
     plusSeconds(Math.max(1,
     (x.ttlMinutes()==null?30:x.ttlMinutes()))*60L));
     return repo.save(a);
     }
 @PostMapping("/{id}/approve") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','APPROVER')") @Transactional
 public Approval approve(@RequestHeader("X-Tenant-Id") UUID tenant,@PathVariable UUID id,
 org.springframework.security.core.Authentication actor){
     return decision(tenant,id,"APPROVED",actor);
 }
 @PostMapping("/{id}/decision") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','APPROVER')") @Transactional
Approval decision(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@RequestParam String status,org.springframework.security.core.Authentication actor){
     tenantSession.set(t);
     if(!Set.of("APPROVED","REJECTED").contains(status))throw new
IllegalArgumentException("status must be APPROVED or REJECTED");
     Approval a=repo.findByIdAndTenantId(id,t).orElseThrow();
     if(!"PENDING".equals(a.getStatus()))throw
new IllegalStateException("approval is not pending");
     if(a.getExpiresAt()!=null&&a.getExpiresAt().isBefore(Instant.now())){
         a.setStatus("EXPIRED");
         return repo.save(a);
         }
         String subject=actor==null?"unknown":actor.getName();
     if(a.getApproverId()!=null&&!a.getApproverId().isBlank()&&!a.getApproverId().equals(subject)&&
     !actor.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_PLATFORM")||
x.getAuthority().equals("ROLE_ADMIN")))throw new IllegalStateException("approval assigned to a different approver");
     if(a.getApproverId()!=null&&a.getApproverId().equals(subject)&&a.getPayload()!=
     null&&a.getPayload().contains("\"requestedBy\":\""+subject+"\""))throw new
IllegalStateException("self-approval is not allowed");
     a.setStatus(status);
     a.setDecidedBy(subject);
     a.setDecidedAt(Instant.now());
     return repo.save(a);
     }
}
