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
    final McpInvocationService calls = new McpInvocationService(registry, jdbc, mapper, actions, approvals, events, properties, risk);
    McpToolRegistry.Binding binding;
    JsonNode arguments;

    @BeforeEach void setup() throws Exception {
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
}
