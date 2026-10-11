package com.zt.security.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.approval.*;
import com.zt.security.integration.GovernanceStore;
import com.zt.security.mcp.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/v1/approval-inbox")
@PreAuthorize("hasAnyRole('PLATFORM','ADMIN','APPROVER','AUDITOR','EXECUTOR')")
@Transactional(readOnly=true)
public class ApprovalInboxController {
    private final GovernanceStore records;private final ApprovalRepository approvals;
    private final McpInvocationService mcp;private final McpGatewayProperties properties;
    private final ObjectMapper mapper;private final WorkflowAccess access;
    public ApprovalInboxController(GovernanceStore records,ApprovalRepository approvals,McpInvocationService mcp,McpGatewayProperties properties,ObjectMapper mapper,WorkflowAccess access){
        this.records=records;this.approvals=approvals;this.mcp=mcp;this.properties=properties;this.mapper=mapper;this.access=access;
    }
    @GetMapping public Object list(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication auth,@RequestParam(required=false) String subject){
        access.scoped(auth,t,w);McpActor.from(auth,t,w,properties);
        List<Map<String,Object>> rows=new ArrayList<>();
        for(UUID id:records.workflowApprovalIds(t,w,subject)){
            var approval=approvals.findByIdAndTenantId(id,t).orElseThrow();
            if("MCP_EXECUTION".equals(approval.getApprovalType()))continue;
            var decision=records.workflowDecision(t,w,approval.getRequestId());
            var request=mapper.valueToTree(decision.get("request"));var context=request.path("context");
            var row=new LinkedHashMap<String,Object>();row.put("kind","WORKFLOW");row.put("id",id);row.put("requestId",approval.getRequestId());
            row.put("action",request.path("action").path("name").asText());row.put("subject",request.path("principal").path("id").asText());
            row.put("requestedBy",context.path("requestedBy").asText(request.path("principal").path("id").asText()));
            row.put("amount",context.path("amount").isNumber()?context.path("amount").decimalValue():null);row.put("currency",context.path("currency").asText(""));row.put("recipient",context.path("recipient").asText(""));
            row.put("reason",approval.getReason());row.put("status",expired(approval.getStatus(),approval.getExpiresAt()));row.put("expiresAt",approval.getExpiresAt());row.put("createdAt",approval.getCreatedAt());row.put("decidedBy",approval.getDecidedBy());
            row.put("approverId",approval.getApproverId());
            row.put("canApprove",access.has(auth,"ROLE_ADMIN","ROLE_PLATFORM","ROLE_APPROVER")&&!auth.getName().equals(row.get("requestedBy"))
                &&(approval.getApproverId()==null||approval.getApproverId().isBlank()||approval.getApproverId().equals(auth.getName())||access.has(auth,"ROLE_ADMIN","ROLE_PLATFORM")));
            rows.add(row);
        }
        for(var call:mcp.approvalQueue(t,w)){
            if(subject!=null&&!subject.equals(call.get("requestedBy"))&&!subject.equals(call.get("policySubject")))continue;
            var row=new LinkedHashMap<String,Object>(call);row.put("kind","MCP");row.put("id",call.get("approvalId"));row.put("action",call.get("toolName"));row.put("subject",call.get("policySubject"));
            row.put("canApprove",access.has(auth,"ROLE_ADMIN","ROLE_PLATFORM","ROLE_APPROVER")&&!auth.getName().equals(call.get("requestedBy")));
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String,Object> r)->String.valueOf(r.getOrDefault("createdAt",r.get("expiresAt")))).reversed());
        return rows;
    }
    private String expired(String status,Instant expiry){return "PENDING".equals(status)&&expiry!=null&&!expiry.isAfter(Instant.now())?"EXPIRED":status;}
}
