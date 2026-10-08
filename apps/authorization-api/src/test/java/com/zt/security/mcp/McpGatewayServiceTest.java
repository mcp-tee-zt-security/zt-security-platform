package com.zt.security.mcp;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpGatewayServiceTest {
    final ObjectMapper mapper = new ObjectMapper();
    final UUID tenant = UUID.randomUUID(), workspace = UUID.randomUUID(), tool = UUID.randomUUID();
    final McpActor actor = new McpActor("verified-key", "client:payments");
    final McpToolRegistry registry = mock(McpToolRegistry.class);
    final McpInvocationService calls = mock(McpInvocationService.class);
    final McpUpstreamClient upstream = mock(McpUpstreamClient.class);
    final McpGatewayService gateway = new McpGatewayService(registry, calls, upstream, mapper, new McpGatewayProperties());
    McpToolRegistry.Binding binding;
    JsonNode arguments;

    @BeforeEach void setup() throws Exception {
        arguments = mapper.readTree("{\"amount\":100}");
        var config = new McpToolConfig("payments", "payment.transfer", Set.of(actor.subject()),
            mapper.readTree("{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\",\"maximum\":500}},\"required\":[\"amount\"],\"additionalProperties\":false}"),
            List.of("/structuredContent/secret"), List.of("confidential-value"), false);
        binding = new McpToolRegistry.Binding(tool, "payment.transfer", "Transfer", "LOW", config, "binding-hash");
        when(registry.named(tenant, workspace, "payment.transfer", actor)).thenReturn(binding);
        when(calls.finish(eq(tenant), eq(workspace), any(), anyString(), any(), nullable(String.class))).thenReturn(UUID.randomUUID());
    }
    private McpInvocationService.Plan plan(String status, String decision) {
        return new McpInvocationService.Plan(UUID.randomUUID(), binding, arguments, status, decision,
            "policy decision", null, UUID.randomUUID());
    }
    private JsonNode rpc() {
        return mapper.valueToTree(Map.of("jsonrpc", "2.0", "id", "call-1", "method", "tools/call",
            "params", Map.of("name", "payment.transfer", "arguments", arguments)));
    }
    @Test void deniedAndPendingCallsNeverReachUpstream() {
        for (var blocked : List.of(plan("DENIED", "DENY"), plan("PENDING_APPROVAL", "STEP_UP"))) {
            when(calls.prepare(eq(tenant), eq(workspace), eq(actor), eq(tool), any(), eq(true), anyString())).thenReturn(blocked);
            JsonNode response = gateway.jsonRpc(tenant, workspace, actor, rpc());
            assertTrue(response.path("result").path("isError").booleanValue());
        }
        verifyNoInteractions(upstream);
        verify(calls, never()).finish(any(), any(), any(), anyString(), any(), any());
    }
    @Test void allowedCallReturnsActualFilteredUpstreamResult() throws Exception {
        var allowed = plan("EXECUTING", "ALLOW");
        when(calls.prepare(eq(tenant), eq(workspace), eq(actor), eq(tool), any(), eq(true), anyString())).thenReturn(allowed);
        when(upstream.call(binding.config(), arguments)).thenReturn(mapper.readTree("""
            {"content":[{"type":"text","text":"completed confidential-value"}],
             "structuredContent":{"receipt":"receipt-42","secret":"private"},
             "_meta":{"upstreamSecret":"must-not-leak"}}
            """));
        JsonNode result = gateway.jsonRpc(tenant, workspace, actor, rpc()).get("result");
        assertEquals("completed [REDACTED]", result.at("/content/0/text").textValue());
        assertEquals("receipt-42", result.at("/structuredContent/receipt").textValue());
        assertTrue(result.at("/structuredContent/secret").isMissingNode());
        assertTrue(result.at("/_meta/upstreamSecret").isMissingNode());
        assertEquals("SUCCEEDED", result.at("/_meta/zt.security/status").textValue());
        verify(upstream, times(1)).call(binding.config(), arguments);
        verify(calls).finish(eq(tenant), eq(workspace), eq(allowed.callId()), eq("SUCCEEDED"), any(), isNull());
    }
    @Test void uncertainExecutionIsRecordedWithoutRetry() {
        var allowed = plan("EXECUTING", "ALLOW");
        when(calls.prepare(eq(tenant), eq(workspace), eq(actor), eq(tool), any(), eq(true), anyString())).thenReturn(allowed);
        when(upstream.call(binding.config(), arguments)).thenThrow(new McpUpstreamClient.Failure("UPSTREAM_TIMEOUT", true));
        JsonNode result = gateway.jsonRpc(tenant, workspace, actor, rpc()).get("result");
        assertEquals("UNKNOWN", result.at("/_meta/zt.security/status").textValue());
        verify(upstream, times(1)).call(binding.config(), arguments);
        verify(calls).finish(eq(tenant), eq(workspace), eq(allowed.callId()), eq("UNKNOWN"), any(), eq("UPSTREAM_TIMEOUT"));
    }
    @Test void completionPersistenceFailureDoesNotReturnAnAuditedSuccess() throws Exception {
        var allowed = plan("EXECUTING", "ALLOW");
        when(calls.prepare(eq(tenant), eq(workspace), eq(actor), eq(tool), any(), eq(true), anyString())).thenReturn(allowed);
        when(upstream.call(binding.config(), arguments)).thenReturn(mapper.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"done\"}]}"));
        when(calls.finish(eq(tenant), eq(workspace), any(), anyString(), any(), nullable(String.class))).thenThrow(new IllegalStateException("database unavailable"));
        JsonNode response = gateway.jsonRpc(tenant, workspace, actor, rpc());
        assertFalse(response.has("result"));
        assertTrue(response.path("error").path("message").textValue().contains(allowed.callId().toString()));
        verify(upstream, times(1)).call(binding.config(), arguments);
    }
}
