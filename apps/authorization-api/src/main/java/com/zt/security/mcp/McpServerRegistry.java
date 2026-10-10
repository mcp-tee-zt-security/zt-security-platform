package com.zt.security.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.common.TenantSession;
import com.zt.security.common.WorkspaceSession;
import com.zt.security.event.SecurityEventService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.*;

/** Scoped registration; secrets remain in deployment-managed environment variables. */
@Service
@Transactional
public class McpServerRegistry {
    public record Registration(String displayName, String endpoint, String bearerTokenEnv,
        boolean enabled, boolean developmentHttp) {}
    private static final String SCOPE = "tenant_id=:tenant AND workspace_id IS NOT DISTINCT FROM CAST(:workspace AS uuid)";
    private final NamedParameterJdbcTemplate jdbc;
    private final TenantSession tenants;
    private final WorkspaceSession workspaces;
    private final McpGatewayProperties properties;
    private final SecurityEventService events;
    private final ObjectMapper mapper;

    McpServerRegistry(NamedParameterJdbcTemplate jdbc, TenantSession tenants, WorkspaceSession workspaces,
            McpGatewayProperties properties, SecurityEventService events, ObjectMapper mapper) {
        this.jdbc = jdbc; this.tenants = tenants; this.workspaces = workspaces;
        this.properties = properties; this.events = events; this.mapper = mapper;
    }
    private void scope(UUID tenant, UUID workspace) {
        tenants.set(tenant); workspaces.set(workspace);
        if (workspace != null && !Integer.valueOf(1).equals(jdbc.queryForObject(
                "SELECT count(*) FROM workspaces WHERE id=:workspace AND tenant_id=:tenant",
                McpToolRegistry.params(tenant, workspace), Integer.class))) {
            throw new AccessDeniedException("Workspace does not belong to the tenant");
        }
    }
    private List<Map<String, Object>> rows(UUID tenant, UUID workspace, String id) {
        scope(tenant, workspace);
        return jdbc.queryForList("SELECT * FROM mcp_upstream_servers WHERE " + SCOPE +
            (id == null ? " ORDER BY server_id" : " AND server_id=:server"),
            McpToolRegistry.params(tenant, workspace).addValue("server", id));
    }
    public McpGatewayProperties.Server resolve(UUID tenant, UUID workspace, String id) {
        var records = rows(tenant, workspace, id);
        if (records.isEmpty()) return properties.server(id);
        var row = records.get(0);
        if (!Boolean.TRUE.equals(row.get("enabled"))) throw new AccessDeniedException("MCP upstream is disabled");
        Registration registration = new Registration((String) row.get("display_name"), (String) row.get("endpoint"),
            (String) row.get("bearer_token_env"), true, Boolean.TRUE.equals(row.get("development_http")));
        validate(id, registration); // Changed deployment allowlists fail closed on existing registrations too.
        return server(registration.endpoint(), registration.bearerTokenEnv());
    }
    private static McpGatewayProperties.Server server(String endpoint, String env) {
        var value = new McpGatewayProperties.Server();
        value.setEndpoint(URI.create(endpoint)); value.setBearerTokenEnv(env);
        return value;
    }
    private void validate(String id, Registration value) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}") || value == null
            || value.displayName() == null || value.displayName().isBlank() || value.displayName().length() > 128
            || value.endpoint() == null || value.endpoint().length() > 2048) {
            throw new IllegalArgumentException("MCP server ID, display name and endpoint are required");
        }
        URI endpoint;
        try { endpoint = URI.create(value.endpoint()); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid MCP endpoint"); }
        String host = endpoint.getHost();
        if (host == null || endpoint.getUserInfo() != null || endpoint.getQuery() != null
            || endpoint.getFragment() != null || endpoint.getPort() == 0 || endpoint.getPort() > 65535
            || properties.getRegistrationHosts().stream().noneMatch(h -> h.equalsIgnoreCase(host))) {
            throw new IllegalArgumentException("Endpoint must use a host allowed by ZT_MCP_REGISTRATION_HOSTS, without credentials, query or fragment");
        }
        boolean localDevelopment = value.developmentHttp() && properties.isAllowDevelopmentHttpRegistration()
            && Set.of("localhost", "127.0.0.1", "host.docker.internal").contains(host.toLowerCase(Locale.ROOT));
        if (!("https".equals(endpoint.getScheme()) || "http".equals(endpoint.getScheme())
                && (properties.isAllowHttp() || localDevelopment))) {
            throw new IllegalArgumentException("HTTPS is required; local development HTTP must be explicitly selected");
        }
        String env = value.bearerTokenEnv();
        if (env != null && (!env.matches("[A-Z][A-Z0-9_]{0,127}") || !properties.getCredentialEnvAllowlist().contains(env))) {
            throw new IllegalArgumentException("Credential reference must be in ZT_MCP_CREDENTIAL_ENV_ALLOWLIST");
        }
    }
    public Map<String, Object> save(UUID tenant, UUID workspace, String id, Registration value, String actor) {
        validate(id, value); scope(tenant, workspace);
        var p = McpToolRegistry.params(tenant, workspace).addValue("server", id);
        String lock = McpJson.digest(tenant + ":" + workspace + ":server:" + id);
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(:lock)",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("lock", Long.parseUnsignedLong(lock.substring(0, 16), 16)), Object.class);
        var existing = rows(tenant, workspace, id);
        p.addValue("name", value.displayName()).addValue("endpoint", value.endpoint())
            .addValue("env", value.bearerTokenEnv()).addValue("enabled", value.enabled()).addValue("dev", value.developmentHttp());
        if (existing.isEmpty()) {
            jdbc.update("INSERT INTO mcp_upstream_servers(id,tenant_id,workspace_id,server_id,display_name,endpoint,bearer_token_env,enabled,development_http) " +
                "VALUES (:id,:tenant,:workspace,:server,:name,:endpoint,:env,:enabled,:dev)", p.addValue("id", UUID.randomUUID()));
        } else {
            jdbc.update("UPDATE mcp_upstream_servers SET display_name=:name,endpoint=:endpoint,bearer_token_env=:env,enabled=:enabled," +
                "development_http=:dev,updated_at=now() WHERE " + SCOPE + " AND server_id=:server", p);
        }
        events.enqueue(tenant, workspace, "MCP_SERVER_REGISTERED", "MCP_SERVER", id,
            mapper.valueToTree(Map.of("actor", actor, "serverId", id, "endpoint", value.endpoint(), "enabled", value.enabled())).toString());
        return view(id, value.displayName(), value.endpoint(), value.bearerTokenEnv(), value.enabled(), value.developmentHttp(), "DATABASE");
    }
    public List<Map<String, Object>> list(UUID tenant, UUID workspace) {
        Map<String, Map<String, Object>> result = new TreeMap<>();
        properties.getServers().forEach((id, server) -> result.put(id, view(id, id, server.getEndpoint().toString(),
            server.getBearerTokenEnv(), true, false, "CONFIGURATION")));
        for (var row : rows(tenant, workspace, null)) {
            String id = (String) row.get("server_id");
            result.put(id, view(id, (String) row.get("display_name"), (String) row.get("endpoint"),
                (String) row.get("bearer_token_env"), Boolean.TRUE.equals(row.get("enabled")),
                Boolean.TRUE.equals(row.get("development_http")), "DATABASE"));
        }
        return new ArrayList<>(result.values());
    }
    private Map<String, Object> view(String id, String name, String endpoint, String env, boolean enabled, boolean dev, String source) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("serverId", id); value.put("displayName", name); value.put("endpoint", endpoint);
        value.put("bearerTokenEnv", env); value.put("enabled", enabled); value.put("developmentHttp", dev); value.put("source", source);
        value.put("credentialAvailable", env == null || System.getenv(env) != null && !System.getenv(env).isBlank());
        return value;
    }
    public Map<String, Object> registrationPolicy() {
        return Map.of("allowedHosts", properties.getRegistrationHosts(), "credentialEnvReferences", properties.getCredentialEnvAllowlist(),
            "developmentHttpAvailable", properties.isAllowDevelopmentHttpRegistration(), "httpEnabled", properties.isAllowHttp());
    }
}
