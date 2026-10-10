package com.zt.security.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.common.TenantSession;
import com.zt.security.common.WorkspaceSession;
import com.zt.security.event.SecurityEventService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpServerRegistryTest {
    final McpGatewayProperties properties = new McpGatewayProperties();
    final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    final McpServerRegistry registry = new McpServerRegistry(jdbc, mock(TenantSession.class), mock(WorkspaceSession.class),
        properties, mock(SecurityEventService.class), new ObjectMapper());
    final UUID tenant = UUID.randomUUID();

    @Test void registrationCannotTargetAnUnapprovedHost() {
        assertThrows(IllegalArgumentException.class, () -> registry.save(tenant, null, "orders",
            new McpServerRegistry.Registration("Orders", "https://unapproved.example/mcp", null, true, false), "api-key"));
        verifyNoInteractions(jdbc);
    }
    @Test void localHttpRequiresAnExplicitDevelopmentChoice() {
        assertThrows(IllegalArgumentException.class, () -> registry.save(tenant, null, "orders",
            new McpServerRegistry.Registration("Orders", "http://host.docker.internal:9998/mcp", null, true, false), "api-key"));
        verifyNoInteractions(jdbc);
    }
    @Test void developmentChoiceCannotEnableHttpForAnotherHost() {
        properties.setRegistrationHosts(Set.of("orders.internal"));
        assertThrows(IllegalArgumentException.class, () -> registry.save(tenant, null, "orders",
            new McpServerRegistry.Registration("Orders", "http://orders.internal/mcp", null, true, true), "api-key"));
        verifyNoInteractions(jdbc);
    }
    @Test void administratorCannotReferenceAnArbitraryServerSecret() {
        assertThrows(IllegalArgumentException.class, () -> registry.save(tenant, null, "orders",
            new McpServerRegistry.Registration("Orders", "https://localhost/mcp", "PATH", true, false), "api-key"));
        verifyNoInteractions(jdbc);
    }
    @Test void deploymentCanDisableDevelopmentHttpRegistration() {
        properties.setAllowDevelopmentHttpRegistration(false);
        assertThrows(IllegalArgumentException.class, () -> registry.save(tenant, null, "orders",
            new McpServerRegistry.Registration("Orders", "http://localhost:9998/mcp", null, true, true), "api-key"));
        verifyNoInteractions(jdbc);
    }
}
