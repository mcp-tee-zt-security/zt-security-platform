package com.zt.security.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.common.TenantSession;
import com.zt.security.common.WorkspaceSession;
import com.zt.security.event.SecurityEventService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GovernanceWorkflowQueryTest {
    @Test void contractFiltersAreBoundAndBothContractAndDecisionAreScoped() {
        var jdbc=mock(NamedParameterJdbcTemplate.class);
        var tenants=mock(TenantSession.class);var workspaces=mock(WorkspaceSession.class);
        var store=new GovernanceStore(jdbc,new ObjectMapper(),tenants,workspaces,mock(SecurityEventService.class));
        UUID tenant=UUID.randomUUID(),workspace=UUID.randomUUID(),request=UUID.randomUUID();
        String subject="agent' OR TRUE --";
        store.workflowContracts(tenant,workspace,subject,request);
        var sql=ArgumentCaptor.forClass(String.class);var parameters=ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(),parameters.capture(),any(RowMapper.class));
        assertFalse(sql.getValue().contains(subject));
        assertTrue(sql.getValue().contains("g.tenant_id=:tenant"));
        assertTrue(sql.getValue().contains("g.workspace_id=:workspace"));
        assertTrue(sql.getValue().contains("d.tenant_id=:tenant"));
        assertTrue(sql.getValue().contains("d.workspace_id=:workspace"));
        assertTrue(sql.getValue().endsWith("LIMIT 100"));
        assertEquals(subject,parameters.getValue().getValue("subject"));
        assertEquals(request.toString(),parameters.getValue().getValue("requestId"));
        assertEquals(tenant,parameters.getValue().getValue("tenant"));
        assertEquals(workspace,parameters.getValue().getValue("workspace"));
        verify(tenants).set(tenant);verify(workspaces).set(workspace);
    }
}
