package com.zt.security.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.event.SecurityEventService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpAgentBindingServiceTest {
    final NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
    final McpToolRegistry registry=mock(McpToolRegistry.class);
    final McpAgentBindingService service=new McpAgentBindingService(jdbc,registry,mock(SecurityEventService.class),new ObjectMapper());
    final UUID tenant=UUID.randomUUID(),workspace=UUID.randomUUID();
    final McpActor actor=new McpActor("service:verified","client:commerce");
    Map<String,Object> row(String agent,boolean enabled,boolean defaultAgent) {
        return new HashMap<>(Map.of("external_id",agent,"identity_type","AI_AGENT","agent_status","ACTIVE",
            "client_status","ACTIVE","client_valid",true,"workspace_id",workspace,"enabled",enabled,"is_default",defaultAgent,"revision",UUID.randomUUID()));
    }
    void rows(Map<String,Object>... rows) { when(jdbc.queryForList(anyString(),any(SqlParameterSource.class))).thenReturn(List.of(rows)); }
    @Test void unconfiguredServiceRetainsLegacySubject() { rows();assertEquals("client:commerce",service.resolve(tenant,workspace,actor).subject()); }
    @Test void explicitAgentWithoutBindingIsRejected() { rows();assertThrows(AccessDeniedException.class,()->service.resolve(tenant,workspace,actor.selecting("orders"))); }
    @Test void multiAgentServiceRequiresSelectionWhenThereIsNoDefault() {
        rows(row("orders",true,false),row("refunds",true,false));
        assertThrows(AccessDeniedException.class,()->service.resolve(tenant,workspace,actor));
        assertEquals("refunds",service.resolve(tenant,workspace,actor.selecting("refunds")).subject());
    }
    @Test void defaultIsUsedAndExplicitSelectionCanOverrideIt() {
        rows(row("orders",true,true),row("refunds",true,false));
        assertEquals("orders",service.resolve(tenant,workspace,actor).subject());
        assertEquals("refunds",service.resolve(tenant,workspace,actor.selecting("refunds")).subject());
    }
    @Test void singleEnabledAgentIsSelectedWithoutAHeader() {
        rows(row("orders",true,false),row("refunds",false,false));
        assertEquals("orders",service.resolve(tenant,workspace,actor).subject());
    }
    @Test void disabledAgentNeverFallsBackToLegacy() {
        rows(row("orders",false,false));
        assertThrows(AccessDeniedException.class,()->service.resolve(tenant,workspace,actor));
        assertThrows(AccessDeniedException.class,()->service.resolve(tenant,workspace,actor.selecting("orders")));
    }
    @Test void inactiveIdentityAndForeignWorkspaceAreRejected() {
        var row=row("orders",true,true);row.put("agent_status","DISABLED");rows(row);
        assertThrows(AccessDeniedException.class,()->service.resolve(tenant,workspace,actor));
        row.put("agent_status","ACTIVE");row.put("workspace_id",UUID.randomUUID());
        assertThrows(AccessDeniedException.class,()->service.resolve(tenant,workspace,actor));
    }
    @Test void jwtSubjectCannotImpersonateAServiceClientBinding() {
        assertThrows(AccessDeniedException.class,()->service.resolve(tenant,workspace,new McpActor("jwt:verified","client:commerce","orders")));
        verifyNoInteractions(jdbc);
    }
    @Test void registrationRejectsForeignOrMissingRecordsBeforeWriting() {
        rows();
        assertThrows(AccessDeniedException.class,()->service.save(tenant,workspace,new McpAgentBindingService.Registration(UUID.randomUUID(),UUID.randomUUID(),true,false),"admin"));
        verify(jdbc,never()).update(anyString(),any(SqlParameterSource.class));
    }
}
