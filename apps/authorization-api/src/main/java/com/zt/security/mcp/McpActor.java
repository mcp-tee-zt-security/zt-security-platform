package com.zt.security.mcp;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.UUID;

/** Caller identity comes from verified authentication, never tool arguments or forwarded headers. */
public record McpActor(String key, String subject, String requestedAgent) {
    public McpActor(String key,String subject) { this(key,subject,null); }
    public boolean serviceClient() { return key.startsWith("service:") && subject.startsWith("client:"); }
    public McpActor selecting(String requested) {
        if(requested!=null && !requested.matches("[A-Za-z0-9][A-Za-z0-9._:@-]{0,254}"))throw new IllegalArgumentException("Invalid X-ZT-Agent-Id");
        return new McpActor(key,subject,requested);
    }
    public static McpActor from(Authentication auth, UUID tenant, UUID workspace, McpGatewayProperties config) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || auth.getName().isBlank() || auth.getName().length() > 512) {
            throw new AccessDeniedException("Authenticated MCP caller required");
        }
        if (auth instanceof JwtAuthenticationToken jwt) {
            var token = jwt.getToken();
            if (token.getIssuer() == null || token.getSubject() == null || token.getSubject().isBlank()
                || !token.getAudience().contains(config.getAudience())
                || !tenant.toString().equalsIgnoreCase(token.getClaimAsString("tenant_id"))) {
                throw new AccessDeniedException("MCP token requires matching audience, subject and tenant");
            }
            String scope = token.getClaimAsString("workspace_id");
            if (scope != null && (workspace == null || !scope.equalsIgnoreCase(workspace.toString()))) {
                throw new AccessDeniedException("MCP workspace mismatch");
            }
            String issuer = token.getIssuer().toString();
            return new McpActor("jwt:" + McpJson.digest(issuer.length() + ":" + issuer + token.getSubject()), token.getSubject());
        }
        boolean service = auth.getName().startsWith("client:") && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE_CLIENT"));
        boolean master = auth.getName().equals("api-key") && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM"));
        if (!service && !master) throw new AccessDeniedException("MCP requires an OIDC or registered service-client identity");
        return new McpActor("service:" + McpJson.digest(auth.getName()), auth.getName());
    }
}
