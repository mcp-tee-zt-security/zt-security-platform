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
    public record Binding(UUID toolId, String name, String description, String riskLevel, McpToolConfig config, String hash,
            McpGatewayProperties.Server server) {
        public Binding(UUID toolId, String name, String description, String riskLevel, McpToolConfig config, String hash) {
            this(toolId, name, description, riskLevel, config, hash, null);
        }
    }
    public record Provision(UUID toolId, String name, String description, String riskLevel, McpToolConfig config) {}
    private final NamedParameterJdbcTemplate jdbc;
    private final AgentToolRepository tools;
    private final TenantSession tenants;
    private final WorkspaceSession workspaces;
    private final ObjectMapper mapper;
    private final McpGatewayProperties properties;
    private final McpServerRegistry servers;
    private final com.zt.security.event.SecurityEventService events;
    private static final String SCOPE = "tenant_id=:tenant AND workspace_id IS NOT DISTINCT FROM CAST(:workspace AS uuid)";

    McpToolRegistry(NamedParameterJdbcTemplate jdbc, AgentToolRepository tools, TenantSession tenants,
            WorkspaceSession workspaces, ObjectMapper mapper, McpGatewayProperties properties,
            McpServerRegistry servers, com.zt.security.event.SecurityEventService events) {
        this.jdbc = jdbc; this.tools = tools; this.tenants = tenants; this.workspaces = workspaces;
        this.mapper = mapper; this.properties = properties;
        this.servers = servers; this.events = events;
    }
    void scope(UUID tenant, UUID workspace) {
        tenants.set(tenant); workspaces.set(workspace);
        if (workspace != null && !Integer.valueOf(1).equals(jdbc.queryForObject(
                "SELECT count(*) FROM workspaces WHERE id=:workspace AND tenant_id=:tenant", params(tenant, workspace), Integer.class))) {
            throw new AccessDeniedException("Workspace does not belong to the tenant");
        }
    }
    static MapSqlParameterSource params(UUID tenant, UUID workspace) {
        return new MapSqlParameterSource("tenant", tenant).addValue("workspace", workspace, java.sql.Types.OTHER);
    }
    public Binding register(UUID tenant, UUID workspace, UUID toolId, McpToolConfig config) {
        scope(tenant, workspace);
        AgentTool tool = tool(tenant, toolId);
        config.validate(properties);
        var server = servers.resolve(tenant, workspace, config.serverId());
        String encoded = mapper.valueToTree(config).toString();
        if (encoded.length() > 65536) throw new IllegalArgumentException("MCP binding too large");
        String bindingLock = McpJson.digest(tenant + ":" + workspace + ":binding:" + toolId);
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(:lock)",
            new MapSqlParameterSource("lock", Long.parseUnsignedLong(bindingLock.substring(0, 16), 16)), Object.class);
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
        events.enqueue(tenant, workspace, "MCP_TOOL_BINDING_REGISTERED", "MCP_TOOL", toolId.toString(),
            mapper.valueToTree(Map.of("toolId", toolId, "serverId", config.serverId())).toString());
        return binding(tool, config, server);
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
            return binding(tool, config, servers.resolve(tenant, workspace, config.serverId()));
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
            } catch (AccessDeniedException | IllegalArgumentException ignored) {
                // Disabled or no-longer-allowed upstream registrations are not executable.
                // Administrators can still inspect and repair them through management endpoints.
            }
        }
        return result;
    }
    private AgentTool tool(UUID tenant, UUID id) {
        AgentTool tool = tools.findById(id).filter(t -> tenant.equals(t.getTenantId()) && t.isEnabled())
            .orElseThrow(() -> new AccessDeniedException("MCP tool is disabled or unavailable in this tenant"));
        return tool;
    }
    public Binding provision(UUID tenant, UUID workspace, Provision request) {
        scope(tenant, workspace);
        if (request == null || request.config() == null) throw new IllegalArgumentException("Tool binding is required");
        request.config().validate(properties);
        servers.resolve(tenant, workspace, request.config().serverId());
        UUID id = request.toolId();
        if (id == null) {
            if (request.name() == null || !request.name().matches("[A-Za-z0-9_.:-]{1,128}")
                || request.description() == null || request.description().length() > 2000
                || request.riskLevel() == null || !Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(request.riskLevel())) {
                throw new IllegalArgumentException("Tool name, description and risk level are invalid");
            }
            String lock = McpJson.digest(tenant + ":tool:" + request.name());
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(:lock)",
                new MapSqlParameterSource("lock", Long.parseUnsignedLong(lock.substring(0, 16), 16)), Object.class);
            if (tools.findByTenantId(tenant).stream().anyMatch(t -> request.name().equals(t.getName()))) {
                throw new IllegalStateException("Tool name already exists; select the existing tool");
            }
            AgentTool created = new AgentTool();
            created.setTenantId(tenant); created.setName(request.name()); created.setDescription(request.description());
            created.setRiskLevel(request.riskLevel()); created.setEnabled(true);
            id = tools.saveAndFlush(created).getId();
        }
        return register(tenant, workspace, id, request.config());
    }
    public Map<String, Object> management(UUID tenant, UUID workspace) {
        scope(tenant, workspace);
        List<Map<String, Object>> catalog = new ArrayList<>();
        for (AgentTool tool : tools.findByTenantId(tenant)) {
            catalog.add(Map.of("toolId", tool.getId(), "name", tool.getName(), "description",
                Optional.ofNullable(tool.getDescription()).orElse(""), "riskLevel",
                Optional.ofNullable(tool.getRiskLevel()).orElse("MEDIUM"), "enabled", tool.isEnabled()));
        }
        var bindings = jdbc.queryForList("SELECT tool_id,config::text AS config_json FROM mcp_tool_bindings WHERE " + SCOPE,
            params(tenant, workspace));
        List<Map<String, Object>> configs = new ArrayList<>();
        for (var row : bindings) {
            try { configs.add(Map.of("toolId", row.get("tool_id"), "config", mapper.readTree((String) row.get("config_json")))); }
            catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Invalid binding"); }
        }
        return Map.of("tools", catalog, "bindings", configs);
    }
    public void remove(UUID tenant, UUID workspace, UUID id) {
        scope(tenant, workspace);
        int count = jdbc.update("DELETE FROM mcp_tool_bindings WHERE " + SCOPE + " AND tool_id=:tool",
            params(tenant, workspace).addValue("tool", id));
        if (count != 1) throw new IllegalArgumentException("Binding not found in this workspace");
        events.enqueue(tenant, workspace, "MCP_TOOL_BINDING_REMOVED", "MCP_TOOL", id.toString(), "{}");
    }
    private Binding binding(AgentTool tool, McpToolConfig config, McpGatewayProperties.Server server) {
        String description = Optional.ofNullable(tool.getDescription()).orElse("");
        String risk = Optional.ofNullable(tool.getRiskLevel()).orElse("MEDIUM");
        JsonNode fingerprint = mapper.valueToTree(Map.of("toolId", tool.getId(), "name", tool.getName(),
            "description", description, "riskLevel", risk, "config", config,
            "server", server));
        return new Binding(tool.getId(), tool.getName(), description, risk, config, McpJson.hash(fingerprint), server);
    }
}
