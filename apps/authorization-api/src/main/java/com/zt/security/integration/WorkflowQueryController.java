package com.zt.security.integration;

import com.zt.security.approval.Approval;
import com.zt.security.approval.ApprovalRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

/** Read-only navigation across existing records; never executes or verifies an action. */
@RestController
@RequestMapping("/v1/workflows")
@PreAuthorize("hasAnyRole('PLATFORM','ADMIN','APPROVER','AUDITOR','SOC_ANALYST')")
@Transactional(readOnly=true)
public class WorkflowQueryController {
    private final GovernanceStore records;
    private final ApprovalRepository approvals;
    public WorkflowQueryController(GovernanceStore records, ApprovalRepository approvals) {
        this.records=records; this.approvals=approvals;
    }

    @GetMapping("/contracts")
    public List<Map<String,Object>> contracts(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,
            @RequestParam(required=false) String subject,@RequestParam(required=false) UUID requestId) {
        return records.workflowContracts(tenant,workspace,subject,requestId).stream().map(this::contractSummary).toList();
    }

    @GetMapping("/approvals")
    public List<Map<String,Object>> approvals(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,
            @RequestParam(required=false) String subject) {
        return records.workflowApprovalIds(tenant,workspace,subject).stream()
            .map(id->approvals.findByIdAndTenantId(id,tenant).orElseThrow()).map(this::approvalSummary).toList();
    }

    @GetMapping("/requests/{requestId}")
    public Map<String,Object> request(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,@PathVariable UUID requestId) {
        return Map.of("decision",decisionSummary(records.workflowDecision(tenant,workspace,requestId)),
            "contracts",records.workflowContracts(tenant,workspace,null,requestId).stream().map(this::contractSummary).toList());
    }

    @GetMapping("/contracts/{id}")
    public Map<String,Object> detail(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,@PathVariable UUID id) {
        var contract=records.get(tenant,workspace,"CONTRACT",id.toString(),false);
        var decision=records.get(tenant,workspace,"DECISION",String.valueOf(contract.get("decisionId")),false);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("contract",contractSummary(contract)); out.put("decision",decisionSummary(decision));
        if(contract.get("approvalId")!=null){
            var approval=approvals.findByIdAndTenantId(GovernanceStore.uuid(contract.get("approvalId")),tenant)
                .orElseThrow(()->new AccessDeniedException("Linked approval unavailable"));
            if(!approval.getRequestId().toString().equals(String.valueOf(decision.get("requestId"))))
                throw new AccessDeniedException("Linked approval belongs to another request");
            out.put("approval",approvalSummary(approval));
        }
        if(contract.get("executionId")!=null){
            String executionId=String.valueOf(contract.get("executionId"));
            var execution=records.get(tenant,workspace,"EXECUTION",executionId,false);
            if(!id.toString().equals(String.valueOf(execution.get("contractId"))))
                throw new AccessDeniedException("Linked execution belongs to another contract");
            out.put("execution",select(execution,"id","status","startedAt","completedAt","traceId"));
            out.put("verifications",records.workflowVerifications(tenant,workspace,executionId).stream()
                .map(value->select(value,"id","executionId","verified","reason","createdAt")).toList());
        }else out.put("verifications",List.of());
        return out;
    }

    private Map<String,Object> contractSummary(Map<String,Object> value) {
        return select(value,"id","subject","action","resource","status","decisionId","approvalId",
            "executionId","evidenceIds","traceId","createdAt","expiresAt");
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> decisionSummary(Map<String,Object> value) {
        var out=select(value,"id","requestId","decision","reason","matchedPolicies","traceId","decidedAt");
        if(value.get("request") instanceof Map<?,?> request){
            if(request.get("principal") instanceof Map<?,?> principal)out.put("subject",principal.get("id"));
            if(request.get("action") instanceof Map<?,?> action)out.put("action",action.get("name"));
            if(request.get("resource") instanceof Map<?,?> resource)out.put("resource",resource.get("type")+"/"+resource.get("id"));
        }
        if(value.get("risk") instanceof Map<?,?> risk)out.put("risk",select((Map<String,Object>)risk,"score","level"));
        return out;
    }

    private Map<String,Object> approvalSummary(Approval approval) {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("id",approval.getId());out.put("requestId",approval.getRequestId());
        out.put("approverId",approval.getApproverId());out.put("reason",approval.getReason());
        String status=approval.getStatus();
        if("PENDING".equals(status)&&approval.getExpiresAt()!=null&&approval.getExpiresAt().isBefore(Instant.now()))status="EXPIRED";
        out.put("status",status);out.put("createdAt",approval.getCreatedAt());out.put("expiresAt",approval.getExpiresAt());
        out.put("decidedAt",approval.getDecidedAt());out.put("decidedBy",approval.getDecidedBy());
        return out;
    }

    private static Map<String,Object> select(Map<String,Object> source,String... keys) {
        Map<String,Object> out=new LinkedHashMap<>();
        for(String key:keys)if(source.containsKey(key))out.put(key,source.get(key));
        return out;
    }
}
