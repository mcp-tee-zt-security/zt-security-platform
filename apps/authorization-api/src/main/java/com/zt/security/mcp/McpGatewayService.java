package com.zt.security.mcp;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.*;

/** Coordinates durable authorization and real upstream execution, outside database transactions. */
@Service
public class McpGatewayService {
    private final McpToolRegistry registry;
    private final McpInvocationService invocations;
    private final McpUpstreamClient upstream;
    private final ObjectMapper mapper;
    private final McpGatewayProperties properties;
    static final Set<String> PROTOCOLS = Set.of("2025-11-25", "2025-06-18");

    McpGatewayService(McpToolRegistry registry, McpInvocationService invocations,
            McpUpstreamClient upstream, ObjectMapper mapper, McpGatewayProperties properties) {
        this.registry = registry; this.invocations = invocations; this.upstream = upstream; this.mapper = mapper;
        this.properties = properties;
    }
    public Map<String, Object> capabilities(UUID tenant, UUID workspace) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocol", "MCP"); result.put("gatewayVersion", "3.0.0");
        result.put("security", List.of("authenticated-caller", "argument-constraints", "policy-enforcement",
            "bound-human-approval", "response-filtering", "execution-audit"));
        result.put("executionTransport", "STREAMABLE_HTTP");
        result.put("tenant", tenant); result.put("workspace", workspace);
        return result;
    }
    public List<Map<String, Object>> listTools(UUID tenant, UUID workspace, McpActor actor) {
        return registry.list(tenant, workspace, actor);
    }
    public Map<String, Object> authorize(UUID tenant, UUID workspace, McpActor actor, UUID toolId, JsonNode arguments) {
        return invocations.prepare(tenant, workspace, actor, toolId, arguments, false, null).security();
    }
    public JsonNode resume(UUID tenant, UUID workspace, McpActor actor, UUID callId) {
        return execute(tenant, workspace, invocations.resume(tenant, workspace, actor, callId));
    }
    public JsonNode jsonRpc(UUID tenant, UUID workspace, McpActor actor, JsonNode rpc) {
        JsonNode id = rpc != null && rpc.isObject() ? rpc.get("id") : null;
        try {
            if (rpc == null || !rpc.isObject() || !"2.0".equals(rpc.path("jsonrpc").asText())
                || id == null || !(id.isTextual() || id.isIntegralNumber())
                || id.isTextual() && id.textValue().length() > 512) {
                return error(null, -32600, "Invalid JSON-RPC request");
            }
            String method = McpJson.text(rpc, "method");
            JsonNode params = rpc.has("params") ? rpc.get("params") : mapper.createObjectNode();
            if (!params.isObject()) return error(id, -32602, "params must be an object");
            JsonNode result;
            switch (method) {
                case "initialize" -> {
                    String requested = McpJson.text(params, "protocolVersion");
                    result = mapper.valueToTree(Map.of("protocolVersion", PROTOCOLS.contains(requested) ? requested : "2025-11-25",
                        "capabilities", Map.of("tools", Map.of()),
                        "serverInfo", Map.of("name", "zt-mcp-security-gateway", "version", "3.0.0")));
                }
                case "ping" -> result = mapper.createObjectNode();
                case "tools/list" -> {
                    var tools = registry.list(tenant, workspace, actor).stream()
                        .map(tool -> Map.of("name", tool.get("name"), "description", tool.get("description"),
                            "inputSchema", tool.get("inputSchema"))).toList();
                    result = mapper.valueToTree(Map.of("tools", tools));
                }
                case "tools/call" -> {
                    var binding = registry.named(tenant, workspace, McpJson.text(params, "name"), actor);
                    JsonNode arguments = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();
                    result = execute(tenant, workspace, invocations.prepare(tenant, workspace, actor, binding.toolId(), arguments, true,
                        McpJson.digest(id.toString())));
                }
                default -> { return error(id, -32601, "Unsupported MCP method"); }
            }
            ObjectNode response = mapper.createObjectNode().put("jsonrpc", "2.0");
            response.set("id", id); response.set("result", result);
            return response;
        } catch (AccessDeniedException ex) {
            return error(id, -32001, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return error(id, -32602, ex.getMessage());
        } catch (IllegalStateException ex) {
            return error(id, -32002, ex.getMessage());
        }
    }
    private JsonNode execute(UUID tenant, UUID workspace, McpInvocationService.Plan plan) {
        if (!plan.executable()) return blocked(plan, plan.status(), plan.reason(), null);
        JsonNode filtered;
        String status, code = null;
        try {
            JsonNode response = upstream.call(plan.binding().config(), plan.arguments());
            try {
                filtered = McpResultFilter.filter(response, plan.binding().config(), mapper, properties.getMaxResponseBytes());
                if (filtered.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > properties.getMaxResponseBytes())
                    throw new IllegalArgumentException("Filtered MCP response exceeds size limit");
                McpJson.hash(filtered); // Reject unsupported numeric/depth metadata before recording completion.
                status = filtered.path("isError").asBoolean() ? "TOOL_ERROR" : "SUCCEEDED";
            } catch (IllegalArgumentException ex) {
                throw new McpUpstreamClient.Failure("RESULT_FILTER_REJECTED", true);
            }
        } catch (McpUpstreamClient.Failure failure) {
            status = failure.executionPossible() ? "UNKNOWN" : "NOT_EXECUTED";
            code = failure.code();
            filtered = mapper.createObjectNode().put("isError", true);
            ((ObjectNode) filtered).putArray("content").addObject().put("type", "text")
                .put("text", status.equals("UNKNOWN") ? "Upstream execution outcome is unknown; do not retry automatically"
                    : "Upstream MCP tool was not executed");
        }
        UUID event;
        try {
            event = invocations.finish(tenant, workspace, plan.callId(), status, filtered, code);
        } catch (RuntimeException failure) {
            // A response cannot be represented as audited if the completion record failed to commit.
            throw new IllegalStateException("Execution outcome could not be recorded; inspect MCP call " + plan.callId() + " before any new execution");
        }
        Map<String, Object> security = new LinkedHashMap<>(plan.security());
        security.put("status", status); security.put("errorCode", code); security.put("auditEventId", event);
        security.put("executionPossible", Set.of("SUCCEEDED", "TOOL_ERROR", "UNKNOWN").contains(status));
        ((ObjectNode) filtered).set("_meta", mapper.valueToTree(Map.of("zt.security", security)));
        return filtered;
    }
    private JsonNode blocked(McpInvocationService.Plan plan, String status, String reason, UUID event) {
        ObjectNode result = mapper.createObjectNode().put("isError", true);
        result.putArray("content").addObject().put("type", "text").put("text", "MCP call " + status + ": " + reason);
        Map<String, Object> security = new LinkedHashMap<>(plan.security());
        security.put("executed", false); security.put("auditEventId", event);
        result.set("_meta", mapper.valueToTree(Map.of("zt.security", security)));
        return result;
    }
    private JsonNode error(JsonNode id, int code, String message) {
        ObjectNode result = mapper.createObjectNode().put("jsonrpc", "2.0");
        result.set("id", id == null ? mapper.getNodeFactory().nullNode() : id);
        result.putObject("error").put("code", code).put("message", message == null ? "MCP request rejected" : message);
        return result;
    }
}
