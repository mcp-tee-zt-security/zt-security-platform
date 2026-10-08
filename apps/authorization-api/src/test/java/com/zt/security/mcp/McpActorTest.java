package com.zt.security.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class McpActorTest {
    final UUID tenant = UUID.randomUUID();
    final McpGatewayProperties properties = new McpGatewayProperties();
    JwtAuthenticationToken token(String audience, String tokenTenant, String subject) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("verified-by-security-filter")
            .header("alg", "RS256").issuer("https://issuer.example").subject(subject)
            .claim("aud", List.of(audience)).claim("tenant_id", tokenTenant).build(), List.of());
    }
    @Test void wrongAudienceAndTenantCannotUseGateway() {
        assertThrows(AccessDeniedException.class, () -> McpActor.from(token("another-service", tenant.toString(), "agent-1"), tenant, null, properties));
        assertThrows(AccessDeniedException.class, () -> McpActor.from(token("zt-mcp-gateway", UUID.randomUUID().toString(), "agent-1"), tenant, null, properties));
    }
    @Test void principalComesFromVerifiedTokenSubject() {
        var actor = McpActor.from(token("zt-mcp-gateway", tenant.toString(), "agent-1"), tenant, null, properties);
        assertEquals("agent-1", actor.subject());
        assertTrue(actor.key().startsWith("jwt:"));
    }
}
