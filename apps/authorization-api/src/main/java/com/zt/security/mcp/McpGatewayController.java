package com.zt.security.mcp;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1/mcp")
public class McpGatewayController {
    private final McpGatewayService service;
    private final McpToolRegistry registry;
    private final McpInvocationService invocations;
    private final McpGatewayProperties properties;
    private final ObjectMapper mapper;

    McpGatewayController(McpGatewayService service, McpToolRegistry registry,
            McpInvocationService invocations, McpGatewayProperties properties, ObjectMapper mapper) {
        this.service = service; this.registry = registry; this.invocations = invocations;
        this.properties = properties; this.mapper = mapper;
    }
    private McpActor actor(Authentication auth, UUID tenant, UUID workspace) {
        return McpActor.from(auth, tenant, workspace, properties);
    }
    @GetMapping("/capabilities")
    public Map<String, Object> capabilities(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value = "X-Workspace-Id", required = false) UUID workspace, Authentication auth) {
        actor(auth, tenant, workspace);
        return service.capabilities(tenant, workspace);
    }
    @GetMapping("/tools")
    public List<Map<String, Object>> tools(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value = "X-Workspace-Id", required = false) UUID workspace, Authentication auth) {
        return service.listTools(tenant, workspace, actor(auth, tenant, workspace));
    }
    @PutMapping("/tools/{id}/binding")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Map<String, Object> binding(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value = "X-Workspace-Id", required = false) UUID workspace,
            @PathVariable UUID id, HttpServletRequest request, Authentication auth) {
        actor(auth, tenant, workspace);
        JsonNode body = McpJson.read(request, mapper, properties.getMaxRequestBytes());
        if (body == null || !body.isObject()) throw new IllegalArgumentException("MCP binding must be an object");
        Set<String> fields = Set.of("serverId", "upstreamTool", "allowedSubjects", "inputSchema",
            "redactResultPaths", "redactTextLiterals", "requireApproval");
        body.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field)) throw new IllegalArgumentException("Unsupported MCP binding field: " + field);
        });
        McpToolConfig config;
        try { config = mapper.treeToValue(body, McpToolConfig.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalArgumentException("Invalid MCP binding configuration"); }
        var result = registry.register(tenant, workspace, id, config);
        return Map.of("toolId", result.toolId(), "bindingHash", result.hash(), "config", result.config());
    }
    @PostMapping("/authorize")
    public Map<String, Object> authorize(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value = "X-Workspace-Id", required = false) UUID workspace,
            HttpServletRequest request, Authentication auth) {
        McpActor actor = actor(auth, tenant, workspace);
        JsonNode body = McpJson.read(request, mapper, properties.getMaxRequestBytes());
        UUID tool = UUID.fromString(McpJson.text(body, "toolId"));
        // Legacy context is treated only as tool input, never an identity assertion.
        JsonNode arguments = body.has("arguments") ? body.get("arguments")
            : body.has("context") ? body.get("context") : mapper.createObjectNode();
        return service.authorize(tenant, workspace, actor, tool, arguments);
    }
    @PostMapping("/json-rpc")
    public ResponseEntity<?> jsonRpc(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value = "X-Workspace-Id", required = false) UUID workspace,
            HttpServletRequest request, Authentication auth) {
        McpActor actor = actor(auth, tenant, workspace);
        JsonNode body = McpJson.read(request, mapper, properties.getMaxRequestBytes());
        String protocol = request.getHeader("MCP-Protocol-Version");
        if (protocol != null && !McpGatewayService.PROTOCOLS.contains(protocol)) {
            throw new IllegalArgumentException("Unsupported MCP-Protocol-Version");
        }
        if (body != null && body.isObject() && "2.0".equals(body.path("jsonrpc").asText()) && !body.has("id")
            && "notifications/initialized".equals(body.path("method").asText())) {
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.ok(service.jsonRpc(tenant, workspace, actor, body));
    }
    @GetMapping("/calls/{id}")
    public Map<String, Object> inspect(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value = "X-Workspace-Id", required = false) UUID workspace,
            @PathVariable UUID id, Authentication auth) {
        return invocations.inspect(tenant, workspace, actor(auth, tenant, workspace), id);
    }
    @PostMapping("/calls/{id}/resume")
    public JsonNode resume(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value = "X-Workspace-Id", required = false) UUID workspace,
            @PathVariable UUID id, Authentication auth) {
        return service.resume(tenant, workspace, actor(auth, tenant, workspace), id);
    }
}
