package com.zt.security.mcp;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

@Component
@ConfigurationProperties(prefix = "zt.security.mcp")
public class McpGatewayProperties {
    private Map<String, Server> servers = new LinkedHashMap<>();
    private String audience = "zt-mcp-gateway";
    private int timeoutSeconds = 15;
    private int maxRequestBytes = 65536;
    private int maxResponseBytes = 2097152;
    private int approvalTtlSeconds = 900;
    private int maxConcurrentCalls = 32;
    private boolean allowHttp = false;
    private boolean allowDevelopmentHttpRegistration = true;
    private Set<String> registrationHosts = Set.of("host.docker.internal", "localhost", "127.0.0.1");
    private Set<String> credentialEnvAllowlist = Set.of();
    private Set<String> allowedOrigins = Set.of("http://localhost:3000", "http://127.0.0.1:3000");

    public static class Server {
        private URI endpoint;
        private String bearerTokenEnv;
        public URI getEndpoint() { return endpoint; }
        public void setEndpoint(URI value) { endpoint = value; }
        public String getBearerTokenEnv() { return bearerTokenEnv; }
        public void setBearerTokenEnv(String value) { bearerTokenEnv = value; }
    }
    @PostConstruct
    void validate() {
        if (timeoutSeconds < 1 || timeoutSeconds > 120 || maxRequestBytes < 1024 || maxRequestBytes > 1048576
            || maxResponseBytes < 1024 || maxResponseBytes > 16777216
            || approvalTtlSeconds < 30 || approvalTtlSeconds > 3600 || maxConcurrentCalls < 1
            || maxConcurrentCalls > 256 || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("Invalid MCP gateway limits/audience");
        }
        servers.forEach((id, server) -> {
            URI endpoint = server.getEndpoint();
            if (!id.matches("[A-Za-z0-9_-]{1,64}") || endpoint == null || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !("https".equals(endpoint.getScheme()) || allowHttp && "http".equals(endpoint.getScheme()))) {
                throw new IllegalArgumentException("MCP servers require a fixed HTTP(S) endpoint; HTTP must be explicitly enabled");
            }
            String env = server.getBearerTokenEnv();
            if (env != null && !env.matches("[A-Z][A-Z0-9_]{0,127}")) {
                throw new IllegalArgumentException("MCP server credential must reference an environment variable");
            }
        });
        for (String value : allowedOrigins) {
            URI origin = URI.create(value);
            if (!Set.of("http", "https").contains(origin.getScheme()) || origin.getHost() == null
                || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null
                || origin.getPath() != null && !origin.getPath().isEmpty()) {
                throw new IllegalArgumentException("MCP allowed origins must be exact HTTP(S) origins without paths");
            }
        }
    }
    public Server server(String id) {
        Server server = servers.get(id);
        if (server == null) throw new IllegalArgumentException("MCP upstream server is not configured");
        return server;
    }
    public Map<String, Server> getServers() { return servers; }
    public void setServers(Map<String, Server> value) { servers = value; }
    public String getAudience() { return audience; }
    public void setAudience(String value) { audience = value; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int value) { timeoutSeconds = value; }
    public int getMaxRequestBytes() { return maxRequestBytes; }
    public void setMaxRequestBytes(int value) { maxRequestBytes = value; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int value) { maxResponseBytes = value; }
    public int getApprovalTtlSeconds() { return approvalTtlSeconds; }
    public void setApprovalTtlSeconds(int value) { approvalTtlSeconds = value; }
    public int getMaxConcurrentCalls() { return maxConcurrentCalls; }
    public void setMaxConcurrentCalls(int value) { maxConcurrentCalls = value; }
    public boolean isAllowHttp() { return allowHttp; }
    public void setAllowHttp(boolean value) { allowHttp = value; }
    public boolean isAllowDevelopmentHttpRegistration() { return allowDevelopmentHttpRegistration; }
    public void setAllowDevelopmentHttpRegistration(boolean value) { allowDevelopmentHttpRegistration = value; }
    public Set<String> getRegistrationHosts() { return registrationHosts; }
    public void setRegistrationHosts(Set<String> value) { registrationHosts = value; }
    public Set<String> getCredentialEnvAllowlist() { return credentialEnvAllowlist; }
    public void setCredentialEnvAllowlist(Set<String> value) { credentialEnvAllowlist = value; }
    public Set<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(Set<String> value) { allowedOrigins = value; }
}
