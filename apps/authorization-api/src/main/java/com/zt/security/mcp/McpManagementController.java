package com.zt.security.mcp;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Provisioning and read-only diagnostics are separate from policy-governed tools/call. */
@RestController
@RequestMapping("/v1/mcp")
public class McpManagementController {
    private final McpServerRegistry servers;
    private final McpToolRegistry tools;
    private final McpUpstreamClient upstream;
    private final McpInvocationService calls;
    private final McpGatewayProperties properties;
    private final ObjectMapper mapper;

    McpManagementController(McpServerRegistry servers, McpToolRegistry tools, McpUpstreamClient upstream,
            McpInvocationService calls, McpGatewayProperties properties, ObjectMapper mapper) {
        this.servers = servers; this.tools = tools; this.upstream = upstream;
        this.calls = calls; this.properties = properties; this.mapper = mapper;
    }
    private McpActor actor(Authentication auth, UUID tenant, UUID workspace) { return McpActor.from(auth, tenant, workspace, properties); }
    private static boolean hasRole(Authentication auth, String... roles) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> Arrays.asList(roles).contains(a.getAuthority()));
    }
    private JsonNode body(HttpServletRequest request, Set<String> allowed) {
        JsonNode value = McpJson.read(request, mapper, properties.getMaxRequestBytes());
        if (value == null || !value.isObject()) throw new IllegalArgumentException("JSON object required");
        value.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) throw new IllegalArgumentException("Unsupported field: " + field); });
        return value;
    }
    private <T> T decode(JsonNode value, Class<T> type) {
        try { return mapper.treeToValue(value, type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalArgumentException("Invalid registration fields"); }
    }
    @GetMapping("/management/context")
    public Map<String, Object> context(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace, Authentication auth) {
        var caller = actor(auth, tenant, workspace);
        return Map.of("subject", caller.subject(), "canManage", hasRole(auth, "ROLE_PLATFORM", "ROLE_ADMIN"),
            "canApprove", hasRole(auth, "ROLE_PLATFORM", "ROLE_ADMIN", "ROLE_APPROVER"));
    }
    @GetMapping("/servers")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Map<String, Object> servers(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace, Authentication auth) {
        actor(auth, tenant, workspace);
        return Map.of("servers", servers.list(tenant, workspace), "registrationPolicy", servers.registrationPolicy());
    }
    @PutMapping("/servers/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Map<String, Object> save(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace, @PathVariable String id,
            HttpServletRequest request, Authentication auth) {
        var caller = actor(auth, tenant, workspace);
        var value = decode(body(request, Set.of("displayName", "endpoint", "bearerTokenEnv", "enabled", "developmentHttp")), McpServerRegistry.Registration.class);
        return servers.save(tenant, workspace, id, value, caller.subject());
    }
    @PostMapping("/servers/{id}/discover")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public JsonNode discover(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace, @PathVariable String id, Authentication auth) {
        actor(auth, tenant, workspace);
        // The short registry transaction ends before the network connection starts.
        var server = servers.resolve(tenant, workspace, id);
        JsonNode result;
        try { result = upstream.discover(server); }
        catch (McpUpstreamClient.Failure ex) { throw new IllegalStateException("MCP tool discovery failed: " + ex.code()); }
        if (!result.isObject() || !result.path("tools").isArray() || result.path("tools").size() > 500) {
            throw new IllegalStateException("Invalid or oversized MCP tools/list result");
        }
        // Only registration metadata is returned; discovery never invokes tools/call.
        var safe = mapper.createObjectNode(); var rows = safe.putArray("tools");
        for (JsonNode tool : result.path("tools")) {
            if (!tool.path("name").isTextual() || !tool.path("name").asText().matches("[A-Za-z0-9_.:-]{1,128}")
                || !tool.path("inputSchema").isObject()) throw new IllegalStateException("Invalid upstream tool definition");
            var row = rows.addObject(); row.put("name", tool.path("name").asText());
            String description = tool.path("description").asText("");
            row.put("description", description.substring(0, Math.min(description.length(), 2000)));
            row.set("inputSchema", tool.get("inputSchema"));
        }
        safe.put("hasMore", result.hasNonNull("nextCursor"));
        return safe;
    }
    @GetMapping("/management/tools")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Map<String, Object> tools(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace, Authentication auth) {
        actor(auth, tenant, workspace); return tools.management(tenant, workspace);
    }
    @PostMapping("/management/tools")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Map<String, Object> provision(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,
            HttpServletRequest request, Authentication auth) {
        actor(auth, tenant, workspace);
        JsonNode value = body(request, Set.of("toolId", "name", "description", "riskLevel", "config"));
        if (!value.path("config").isObject()) throw new IllegalArgumentException("Tool config is required");
        Set<String> fields = Set.of("serverId", "upstreamTool", "allowedSubjects", "inputSchema", "redactResultPaths", "redactTextLiterals", "requireApproval");
        value.path("config").fieldNames().forEachRemaining(f -> { if (!fields.contains(f)) throw new IllegalArgumentException("Unsupported binding field: " + f); });
        var result = tools.provision(tenant, workspace, decode(value, McpToolRegistry.Provision.class));
        return Map.of("toolId", result.toolId(), "name", result.name(), "config", result.config());
    }
    @DeleteMapping("/tools/{id}/binding")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Map<String, Object> remove(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace, @PathVariable UUID id, Authentication auth) {
        actor(auth, tenant, workspace); tools.remove(tenant, workspace, id); return Map.of("removed", true);
    }
    @GetMapping("/calls")
    public List<Map<String, Object>> history(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace, Authentication auth) {
        return calls.history(tenant, workspace, actor(auth, tenant, workspace), hasRole(auth, "ROLE_PLATFORM", "ROLE_ADMIN"));
    }
    @GetMapping("/approvals")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','APPROVER')")
    public List<Map<String, Object>> approvals(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace, Authentication auth) {
        actor(auth, tenant, workspace); return calls.approvalQueue(tenant, workspace);
    }
    @GetMapping("/approvals/{callId}")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','APPROVER')")
    public Map<String, Object> approvalDetail(@RequestHeader("X-Tenant-Id") UUID tenant,
            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,
            @PathVariable UUID callId, Authentication auth) {
        actor(auth, tenant, workspace); return calls.approvalDetail(tenant, workspace, callId);
    }
}
