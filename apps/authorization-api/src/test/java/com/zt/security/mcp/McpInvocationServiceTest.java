package com.zt.security.mcp;

import com.fasterxml.jackson.databind.*;
import com.zt.security.action.*;
import com.zt.security.approval.*;
import com.zt.security.event.SecurityEventService;
import com.zerotrust.security.config.RiskScoringProperties;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpInvocationServiceTest {
    final ObjectMapper mapper = new ObjectMapper();
    final UUID tenant = UUID.randomUUID(), workspace = UUID.randomUUID(), tool = UUID.randomUUID();
    final McpActor actor = new McpActor("verified-actor", "client:payments");
    final McpToolRegistry registry = mock(McpToolRegistry.class);
    final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    final ActionEvaluationService actions = mock(ActionEvaluationService.class);
    final ApprovalRepository approvals = mock(ApprovalRepository.class);
    final SecurityEventService events = mock(SecurityEventService.class);
    final McpGatewayProperties properties = new McpGatewayProperties();
    final RiskScoringProperties risk = mock(RiskScoringProperties.class);
    final McpAgentBindingService agents=mock(McpAgentBindingService.class);
    final McpInvocationService calls = new McpInvocationService(registry, jdbc, mapper, actions, approvals, events, properties, risk,agents);
    McpToolRegistry.Binding binding;
    JsonNode arguments;

    @BeforeEach void setup() throws Exception {
        when(agents.resolve(tenant,workspace,actor)).thenReturn(McpAgentBindingService.Principal.legacy(actor));
        arguments = mapper.readTree("{\"amount\":100,\"agent\":\"spoofed-admin\"}");
        var config = new McpToolConfig("payments", "payment.transfer", Set.of(actor.subject()),
            mapper.readTree("{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"},\"agent\":{\"type\":\"string\"}},\"additionalProperties\":false}"),
            List.of(), List.of(), false);
        binding = new McpToolRegistry.Binding(tool, "payment.transfer", "Transfer", "LOW", config, "binding-hash");
        when(registry.get(tenant, workspace, tool, actor)).thenReturn(binding);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        when(events.enqueue(eq(tenant), eq(workspace), anyString(), anyString(), anyString(), anyString())).thenReturn(UUID.randomUUID());
    }
    private EvaluateModels.EvaluateResponse decision(String value) {
        return new EvaluateModels.EvaluateResponse(UUID.randomUUID(), value, "policy result", List.of(),
            new EvaluateModels.Risk(0, "LOW"), new EvaluateModels.Audit(UUID.randomUUID()), 1);
    }
    @Test void callerArgumentsCannotReplaceAuthenticatedPrincipal() {
        when(actions.evaluate(eq(tenant), eq(workspace), any(), eq(false))).thenReturn(decision("ALLOW"));
        var plan = calls.prepare(tenant, workspace, actor, tool, arguments, true, null);
        assertTrue(plan.executable());
        var captured = ArgumentCaptor.forClass(EvaluateModels.EvaluateRequest.class);
        verify(actions).evaluate(eq(tenant), eq(workspace), captured.capture(), eq(false));
        assertEquals(actor.subject(), captured.getValue().principal().id());
        assertEquals(actor.subject(), captured.getValue().context().get("mcp.actor"));
        var metadata = (Map<?, ?>) captured.getValue().context().get("mcp");
        assertEquals(actor.subject(), metadata.get("actor"));
        assertEquals(100, ((Map<?, ?>) metadata.get("arguments")).get("amount"));
        assertEquals("spoofed-admin", captured.getValue().context().get("agent")); // Remains ordinary tool input.
    }
    @Test void replayOfExecutingRpcIdDoesNotEvaluateOrClaimAgain() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("id", UUID.randomUUID()); existing.put("tool_id", tool);
        existing.put("arguments_hash", McpJson.hash(arguments)); existing.put("status", "EXECUTING");
        existing.put("approval_id", null); existing.put("decision_request_id", UUID.randomUUID());
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(existing));
        var plan = calls.prepare(tenant, workspace, actor, tool, arguments, true, "rpc-key");
        assertFalse(plan.executable());
        assertEquals("REPLAY_BLOCKED", plan.status());
        verifyNoInteractions(actions, approvals);
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }
    @Test void previouslyApprovedOperationCannotOverrideCurrentDeny() throws Exception {
        UUID id = UUID.randomUUID(), approvalId = UUID.randomUUID(), requestId = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", "PENDING_APPROVAL"); row.put("expires_at", Timestamp.from(Instant.now().plusSeconds(300)));
        row.put("tool_id", tool); row.put("binding_hash", binding.hash()); row.put("arguments_json", arguments.toString());
        row.put("arguments_hash", McpJson.hash(arguments)); row.put("approval_id", approvalId);
        row.put("decision_request_id", requestId); row.put("policy_hash", McpJson.hash(mapper.valueToTree(List.of())));
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(row));
        Approval approved = new Approval();
        approved.setId(approvalId); approved.setTenantId(tenant); approved.setRequestId(requestId);
        approved.setStatus("APPROVED"); approved.setDecidedBy("independent-approver");
        approved.setExpiresAt(Instant.now().plusSeconds(300));
        approved.setPayload(mapper.valueToTree(Map.of("mcpCallId", id.toString(), "requestedBy", actor.subject(),
            "argumentsHash", McpJson.hash(arguments), "bindingHash", binding.hash())).toString());
        when(approvals.findByIdAndTenantId(approvalId, tenant)).thenReturn(Optional.of(approved));
        when(actions.evaluate(eq(tenant), eq(workspace), any(), eq(false))).thenReturn(decision("DENY"));
        var plan = calls.resume(tenant, workspace, actor, id);
        assertEquals("DENIED", plan.status());
        assertFalse(plan.executable());
        verify(jdbc).queryForList(contains("FOR UPDATE"), any(SqlParameterSource.class));
        verify(events).enqueue(eq(tenant), eq(workspace), eq("MCP_CALL_DENIED"), eq("MCP_CALL"), eq(id.toString()), anyString());
    }
    @Test void linkedAgentBecomesPrincipalButAuthenticationRemainsService() {
        var linked=new McpAgentBindingService.Principal("order-agent","revision-1");
        when(agents.resolve(tenant,workspace,actor)).thenReturn(linked);
        when(actions.evaluate(eq(tenant),eq(workspace),any(),eq(false))).thenReturn(decision("ALLOW"));
        var plan=calls.prepare(tenant,workspace,actor,tool,arguments,true,null);
        var request=ArgumentCaptor.forClass(EvaluateModels.EvaluateRequest.class);
        verify(actions).evaluate(eq(tenant),eq(workspace),request.capture(),eq(false));
        assertEquals("order-agent",request.getValue().principal().id());
        assertEquals(actor.subject(),request.getValue().context().get("mcp.actor"));
        assertEquals("order-agent",request.getValue().context().get("mcp.agent"));
        assertEquals(actor.subject(),plan.security().get("authenticatedSubject"));
        assertEquals("order-agent",plan.security().get("policySubject"));
        var parameters=ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("INSERT INTO mcp_invocations"),parameters.capture());
        assertEquals("order-agent",parameters.getValue().getValue("principal"));
        assertEquals("revision-1",parameters.getValue().getValue("agentRevision"));
    }
    @Test void bindingChangeBlocksResumeBeforeApprovalOrPolicyEvaluation() {
        var row=new HashMap<String,Object>();row.put("policy_subject","orders");row.put("agent_binding_revision","old");
        when(jdbc.queryForList(anyString(),any(SqlParameterSource.class))).thenReturn(List.of(row));
        when(agents.resolve(tenant,workspace,actor)).thenReturn(new McpAgentBindingService.Principal("orders","new"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->calls.resume(tenant,workspace,actor,UUID.randomUUID()));
        verifyNoInteractions(actions,approvals);
    }
    @Test void rpcIdCannotBeReusedForAnotherAgent() {
        var row=new HashMap<String,Object>();row.put("policy_subject","orders");row.put("agent_binding_revision","orders-revision");
        when(jdbc.queryForList(anyString(),any(SqlParameterSource.class))).thenReturn(List.of(row));
        when(agents.resolve(tenant,workspace,actor)).thenReturn(new McpAgentBindingService.Principal("refunds","refunds-revision"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->calls.prepare(tenant,workspace,actor,tool,arguments,true,"same-rpc-id"));
        verifyNoInteractions(actions,approvals);
    }
    @Test void pendingApprovalBindsServiceAgentAndRevision() {
        when(agents.resolve(tenant,workspace,actor)).thenReturn(new McpAgentBindingService.Principal("orders","revision-1"));
        when(actions.evaluate(eq(tenant),eq(workspace),any(),eq(false))).thenReturn(decision("STEP_UP"));
        when(approvals.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        var plan=calls.prepare(tenant,workspace,actor,tool,arguments,true,null);
        assertEquals("PENDING_APPROVAL",plan.status());
        var approval=ArgumentCaptor.forClass(Approval.class);verify(approvals).saveAndFlush(approval.capture());
        var payload=mapper.valueToTree(approval.getValue().getPayload());
        assertTrue(payload.asText().contains("revision-1"));assertTrue(payload.asText().contains("orders"));
        assertTrue(payload.asText().contains(actor.subject()));
    }
    @Test void approvedCallResumesOnlyWithItsOriginalLinkedAgent() throws Exception {
        UUID id=UUID.randomUUID(),approvalId=UUID.randomUUID(),requestId=UUID.randomUUID();
        var principal=new McpAgentBindingService.Principal("orders","revision-1");
        when(agents.resolve(tenant,workspace,actor)).thenReturn(principal);
        var row=new HashMap<String,Object>();
        row.put("policy_subject","orders");row.put("agent_binding_revision","revision-1");
        row.put("status","PENDING_APPROVAL");row.put("expires_at",Timestamp.from(Instant.now().plusSeconds(300)));
        row.put("tool_id",tool);row.put("binding_hash",binding.hash());row.put("arguments_json",arguments.toString());
        row.put("arguments_hash",McpJson.hash(arguments));row.put("approval_id",approvalId);row.put("decision_request_id",requestId);
        row.put("policy_hash",McpJson.hash(mapper.valueToTree(List.of())));
        when(jdbc.queryForList(anyString(),any(SqlParameterSource.class))).thenReturn(List.of(row));
        Approval approval=new Approval();approval.setId(approvalId);approval.setRequestId(requestId);approval.setStatus("APPROVED");
        approval.setExpiresAt(Instant.now().plusSeconds(300));approval.setDecidedBy("independent-admin");
        approval.setPayload(mapper.valueToTree(Map.of("mcpCallId",id.toString(),"requestedBy",actor.subject(),
            "argumentsHash",McpJson.hash(arguments),"bindingHash",binding.hash(),"policySubject","orders","agentBindingRevision","revision-1")).toString());
        when(approvals.findByIdAndTenantId(approvalId,tenant)).thenReturn(Optional.of(approval));
        when(actions.evaluate(eq(tenant),eq(workspace),any(),eq(false))).thenReturn(decision("ALLOW"));
        assertTrue(calls.resume(tenant,workspace,actor,id).executable());
        when(agents.resolve(tenant,workspace,actor)).thenReturn(new McpAgentBindingService.Principal("refunds","revision-2"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->calls.resume(tenant,workspace,actor,id));
        verify(actions,times(1)).evaluate(eq(tenant),eq(workspace),any(),eq(false));
    }
}
