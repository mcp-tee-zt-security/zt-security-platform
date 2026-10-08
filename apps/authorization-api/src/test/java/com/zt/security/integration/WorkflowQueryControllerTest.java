package com.zt.security.integration;

import com.zt.security.approval.Approval;
import com.zt.security.approval.ApprovalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowQueryControllerTest {
    final GovernanceStore records=mock(GovernanceStore.class);
    final ApprovalRepository approvals=mock(ApprovalRepository.class);
    final WorkflowQueryController controller=new WorkflowQueryController(records,approvals);
    final UUID tenant=UUID.randomUUID(),workspace=UUID.randomUUID(),id=UUID.randomUUID(),decisionId=UUID.randomUUID(),requestId=UUID.randomUUID();

    Map<String,Object> decision() {
        return Map.of("id",decisionId.toString(),"requestId",requestId.toString(),"decision","ALLOW",
            "request",Map.of("principal",Map.of("id","agent-a","attributes",Map.of("secret","hidden")),
                "action",Map.of("name","read"),"resource",Map.of("type","document","id","1"),"context",Map.of("token","hidden")),
            "risk",Map.of("score",10,"level","LOW","evidence",List.of("hidden")));
    }

    @Test void requestUsesScopeAndRedactsRequestContextAndRiskEvidence() {
        when(records.workflowDecision(tenant,workspace,requestId)).thenReturn(decision());
        when(records.workflowContracts(tenant,workspace,null,requestId)).thenReturn(List.of(Map.of("id",id.toString(),"request",Map.of("secret","hidden"))));
        var result=controller.request(tenant,workspace,requestId);
        assertFalse(result.toString().contains("hidden"));
        assertTrue(result.toString().contains("agent-a"));
        verify(records).workflowDecision(tenant,workspace,requestId);
        verify(records).workflowContracts(tenant,workspace,null,requestId);
        verifyNoMoreInteractions(records);
    }

    @Test void detailRedactsConnectorResultAndSeparatesSuccessFromVerification() {
        UUID executionId=UUID.randomUUID();
        when(records.get(tenant,workspace,"CONTRACT",id.toString(),false)).thenReturn(Map.of("id",id.toString(),"decisionId",decisionId.toString(),"executionId",executionId.toString()));
        when(records.get(tenant,workspace,"DECISION",decisionId.toString(),false)).thenReturn(decision());
        when(records.get(tenant,workspace,"EXECUTION",executionId.toString(),false)).thenReturn(Map.of("id",executionId.toString(),"contractId",id.toString(),"status","SUCCEEDED","result",Map.of("secret","hidden")));
        when(records.workflowVerifications(tenant,workspace,executionId.toString())).thenReturn(List.of(Map.of("id","verification","verified",false,"reason","Missing evidence")));
        var result=controller.detail(tenant,workspace,id);
        assertFalse(result.toString().contains("hidden"));
        assertTrue(result.toString().contains("SUCCEEDED"));
        assertTrue(result.toString().contains("verified=false"));
        verify(records,never()).update(any(),any(),any(),any());
    }

    @Test void foreignExecutionIsRejected() {
        UUID executionId=UUID.randomUUID();
        when(records.get(tenant,workspace,"CONTRACT",id.toString(),false)).thenReturn(Map.of("decisionId",decisionId.toString(),"executionId",executionId.toString()));
        when(records.get(tenant,workspace,"DECISION",decisionId.toString(),false)).thenReturn(decision());
        when(records.get(tenant,workspace,"EXECUTION",executionId.toString(),false)).thenReturn(Map.of("contractId",UUID.randomUUID().toString()));
        assertThrows(AccessDeniedException.class,()->controller.detail(tenant,workspace,id));
        verify(records,never()).workflowVerifications(any(),any(),any());
    }

    @Test void foreignApprovalRequestIsRejected() {
        UUID approvalId=UUID.randomUUID();
        when(records.get(tenant,workspace,"CONTRACT",id.toString(),false)).thenReturn(Map.of("decisionId",decisionId.toString(),"approvalId",approvalId.toString()));
        when(records.get(tenant,workspace,"DECISION",decisionId.toString(),false)).thenReturn(decision());
        Approval approval=new Approval();approval.setRequestId(UUID.randomUUID());
        when(approvals.findByIdAndTenantId(approvalId,tenant)).thenReturn(Optional.of(approval));
        assertThrows(AccessDeniedException.class,()->controller.detail(tenant,workspace,id));
    }

    @Test void expiredApprovalIsReadAsExpiredWithoutMutationOrPayloadExposure() {
        Approval approval=new Approval();approval.setId(id);approval.setRequestId(requestId);approval.setPayload("hidden");approval.setExpiresAt(Instant.now().minusSeconds(60));
        when(records.workflowApprovalIds(tenant,workspace,"agent-a")).thenReturn(List.of(id));
        when(approvals.findByIdAndTenantId(id,tenant)).thenReturn(Optional.of(approval));
        var result=controller.approvals(tenant,workspace,"agent-a");
        assertEquals("EXPIRED",result.get(0).get("status"));
        assertEquals("PENDING",approval.getStatus());
        assertFalse(result.toString().contains("hidden"));
        verify(approvals,never()).save(any());
    }
}
