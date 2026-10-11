package com.zt.security.approval;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import com.zt.security.common.TenantSession;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/v1/approvals") public class ApprovalController {
    @org.springframework.beans.factory.annotation.Value("${zt.security.mcp.require-oidc-approval:false}")
    boolean requireOidcApproval;
    @org.springframework.beans.factory.annotation.Value("${zt.security.mcp.audience:zt-mcp-gateway}")
    String mcpAudience;
    final org.springframework.jdbc.core.JdbcTemplate jdbc;
    final ApprovalRepository repo;
    final TenantSession tenantSession;
    final com.zt.security.integration.GovernanceStore records;
    final com.fasterxml.jackson.databind.ObjectMapper mapper;
    ApprovalController(ApprovalRepository r,
    TenantSession ts,com.zt.security.integration.GovernanceStore records,
    com.fasterxml.jackson.databind.ObjectMapper mapper,org.springframework.jdbc.core.JdbcTemplate jdbc){
        repo=r;
        tenantSession=ts;
        this.records=records;
        this.mapper=mapper;
        this.jdbc=jdbc;
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
 public Approval approve(@RequestHeader("X-Tenant-Id") UUID tenant,@RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,@PathVariable UUID id,
 org.springframework.security.core.Authentication actor){
     return decision(tenant,workspace,id,"APPROVED",actor);
 }
 @PostMapping("/{id}/decision") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','APPROVER')") @Transactional
Approval decision(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,
 @PathVariable UUID id,@RequestParam String status,org.springframework.security.core.Authentication actor){
     tenantSession.set(t);
     if(!Set.of("APPROVED","REJECTED").contains(status))throw new
IllegalArgumentException("status must be APPROVED or REJECTED");
     Approval a=repo.findByIdAndTenantId(id,t).orElseThrow();
     if("MCP_EXECUTION".equals(a.getApprovalType())&&requireOidcApproval){
         if(!(actor instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt)
             ||!jwt.getToken().getAudience().contains(mcpAudience)
             ||!t.toString().equalsIgnoreCase(jwt.getToken().getClaimAsString("tenant_id"))
             ||!"HUMAN".equals(jwt.getToken().getClaimAsString("actor_type"))
             ||workspace==null
             ||!workspace.toString().equalsIgnoreCase(jwt.getToken().getClaimAsString("workspace_id"))){
             throw new org.springframework.security.access.AccessDeniedException("MCP approval requires a human OIDC token with matching audience, tenant and workspace");
         }
         Integer bound=jdbc.queryForObject("SELECT count(*) FROM mcp_invocations WHERE approval_id=? AND tenant_id=? AND workspace_id=?",Integer.class,id,t,workspace);
         if(bound==null||bound!=1)throw new org.springframework.security.access.AccessDeniedException("MCP approval unavailable in this workspace");
     }
     if(!"PENDING".equals(a.getStatus()))throw
new IllegalStateException("approval is not pending");
     if(a.getExpiresAt()!=null&&a.getExpiresAt().isBefore(Instant.now())){
         a.setStatus("EXPIRED");
         return repo.save(a);
         }
         String subject=actor==null?"unknown":actor.getName();
     if("MCP_EXECUTION".equals(a.getApprovalType())){
         try {
             var payload=mapper.readTree(a.getPayload());
             if(payload==null||!payload.path("requestedBy").isTextual()||!payload.path("mcpCallId").isTextual())
                 throw new IllegalStateException("invalid MCP approval binding");
             if(subject.equals(payload.path("requestedBy").asText()))
                 throw new IllegalStateException("MCP calls require an independent approver");
         }catch(com.fasterxml.jackson.core.JsonProcessingException ex){
             throw new IllegalStateException("invalid MCP approval binding");
         }
     }
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
