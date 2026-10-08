package com.zt.security.mcp;

import com.fasterxml.jackson.databind.*;
import com.zt.security.agent.*;
import com.zt.security.common.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional
public class McpToolRegistry {
    public record Binding(UUID toolId, String name, String description, String riskLevel, McpToolConfig config, String hash) {}
    private final NamedParameterJdbcTemplate jdbc;
    private final AgentToolRepository tools;
    private final TenantSession tenants;
    private final WorkspaceSession workspaces;
    private final ObjectMapper mapper;
    private final McpGatewayProperties properties;
    private static final String SCOPE = "tenant_id=:tenant AND workspace_id IS NOT DISTINCT FROM CAST(:workspace AS uuid)";

    McpToolRegistry(NamedParameterJdbcTemplate jdbc, AgentToolRepository tools, TenantSession tenants,
            WorkspaceSession workspaces, ObjectMapper mapper, McpGatewayProperties properties) {
        this.jdbc = jdbc; this.tools = tools; this.tenants = tenants; this.workspaces = workspaces;
        this.mapper = mapper; this.properties = properties;
    }
    void scope(UUID tenant, UUID workspace) { tenants.set(tenant); workspaces.set(workspace); }
    static MapSqlParameterSource params(UUID tenant, UUID workspace) {
        return new MapSqlParameterSource("tenant", tenant).addValue("workspace", workspace, java.sql.Types.OTHER);
    }
    public Binding register(UUID tenant, UUID workspace, UUID toolId, McpToolConfig config) {
        scope(tenant, workspace);
        AgentTool tool = tool(tenant, toolId);
        config.validate(properties);
        String encoded = mapper.valueToTree(config).toString();
        if (encoded.length() > 65536) throw new IllegalArgumentException("MCP binding too large");
        var p = params(tenant, workspace).addValue("tool", toolId).addValue("config", encoded);
        var existing = jdbc.query("SELECT id FROM mcp_tool_bindings WHERE " + SCOPE + " AND tool_id=:tool FOR UPDATE",
            p, (rs, n) -> rs.getObject(1, UUID.class));
        if (existing.isEmpty()) {
            jdbc.update("INSERT INTO mcp_tool_bindings(id,tenant_id,workspace_id,tool_id,config) VALUES (:id,:tenant,:workspace,:tool,cast(:config as jsonb))",
                p.addValue("id", UUID.randomUUID()));
        } else {
            jdbc.update("UPDATE mcp_tool_bindings SET config=cast(:config as jsonb),updated_at=now() WHERE id=:id AND " + SCOPE,
                p.addValue("id", existing.get(0)));
        }
        return binding(tool, config);
    }
    public Binding get(UUID tenant, UUID workspace, UUID toolId, McpActor actor) {
        scope(tenant, workspace);
        AgentTool tool = tool(tenant, toolId);
        var rows = jdbc.query("SELECT config::text FROM mcp_tool_bindings WHERE " + SCOPE + " AND tool_id=:tool",
            params(tenant, workspace).addValue("tool", toolId), (rs, n) -> rs.getString(1));
        if (rows.isEmpty()) throw new AccessDeniedException("MCP tool has no execution binding in this workspace");
        try {
            McpToolConfig config = mapper.readValue(rows.get(0), McpToolConfig.class);
            config.validate(properties);
            if (!config.allowedSubjects().contains(actor.subject())) throw new AccessDeniedException("Caller is not permitted to use this MCP tool");
            return binding(tool, config);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Invalid stored MCP binding");
        }
    }
    public Binding named(UUID tenant, UUID workspace, String name, McpActor actor) {
        scope(tenant, workspace);
        AgentTool tool = tools.findByTenantId(tenant).stream().filter(t -> name.equals(t.getName())).findFirst()
            .orElseThrow(() -> new AccessDeniedException("MCP tool is not registered"));
        return get(tenant, workspace, tool.getId(), actor);
    }
    public List<Map<String, Object>> list(UUID tenant, UUID workspace, McpActor actor) {
        scope(tenant, workspace);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentTool tool : tools.findByTenantId(tenant)) {
            if (!tool.isEnabled()) continue;
            try {
                Binding binding = get(tenant, workspace, tool.getId(), actor);
                result.add(Map.of("name", binding.name(), "description", binding.description(),
                    "riskLevel", binding.riskLevel(), "toolId", binding.toolId().toString(),
                    "inputSchema", binding.config().inputSchema(), "requiresApproval", binding.config().requireApproval()));
            } catch (AccessDeniedException ignored) { /* Unbound/inaccessible tools are not advertised. */ }
        }
        return result;
    }
    private AgentTool tool(UUID tenant, UUID id) {
        AgentTool tool = tools.findById(id).filter(t -> tenant.equals(t.getTenantId()) && t.isEnabled())
            .orElseThrow(() -> new AccessDeniedException("MCP tool is disabled or unavailable in this tenant"));
        return tool;
    }
    private Binding binding(AgentTool tool, McpToolConfig config) {
        String description = Optional.ofNullable(tool.getDescription()).orElse("");
        String risk = Optional.ofNullable(tool.getRiskLevel()).orElse("MEDIUM");
        JsonNode fingerprint = mapper.valueToTree(Map.of("toolId", tool.getId(), "name", tool.getName(),
            "description", description, "riskLevel", risk, "config", config,
            "server", properties.server(config.serverId())));
        return new Binding(tool.getId(), tool.getName(), description, risk, config, McpJson.hash(fingerprint));
    }
}
