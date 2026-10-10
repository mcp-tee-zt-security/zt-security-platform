package com.zt.security.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.event.SecurityEventService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses a disposable PostgreSQL database supplied by the test runner; never the application DB. */
@EnabledIfEnvironmentVariable(named="ZT_TEST_DB_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpAgentBindingPostgresTest {
    final UUID tenant=UUID.randomUUID(),workspace=UUID.randomUUID(),otherWorkspace=UUID.randomUUID(),client=UUID.randomUUID(),orders=UUID.randomUUID(),refunds=UUID.randomUUID();
    final McpActor actor=new McpActor("service:test","client:commerce");
    JdbcTemplate jdbc,admin;
    TransactionTemplate tx;
    McpAgentBindingService service;

    @BeforeAll void setup() {
        var root=new DriverManagerDataSource(System.getenv("ZT_TEST_DB_URL"),"postgres","fixture-password");
        Flyway.configure().dataSource(root).load().migrate();admin=new JdbcTemplate(root);
        admin.execute("CREATE ROLE agent_binding_test LOGIN PASSWORD 'fixture-runtime' NOSUPERUSER NOBYPASSRLS");
        admin.execute("GRANT USAGE ON SCHEMA public TO agent_binding_test");
        admin.execute("GRANT SELECT,INSERT,UPDATE ON ALL TABLES IN SCHEMA public TO agent_binding_test");
        admin.update("INSERT INTO tenants(id,slug,name) VALUES (?,?,?)",tenant,"agent-binding-test","Agent binding test");
        admin.update("INSERT INTO workspaces(id,tenant_id,slug,name) VALUES (?,?,?,?)",workspace,tenant,"main","Main");
        admin.update("INSERT INTO workspaces(id,tenant_id,slug,name) VALUES (?,?,?,?)",otherWorkspace,tenant,"other","Other");
        admin.update("INSERT INTO api_clients(id,tenant_id,workspace_id,client_id,name,secret_hash) VALUES (?,?,?,?,?,?)",client,tenant,workspace,"commerce","Commerce","fixture-only");
        admin.update("INSERT INTO identities(id,tenant_id,external_id,identity_type,name) VALUES (?,?,?,'AI_AGENT',?)",orders,tenant,"order-agent","Orders");
        admin.update("INSERT INTO identities(id,tenant_id,external_id,identity_type,name) VALUES (?,?,?,'AI_AGENT',?)",refunds,tenant,"refund-agent","Refunds");
        var runtime=new DriverManagerDataSource(System.getenv("ZT_TEST_DB_URL"),"agent_binding_test","fixture-runtime");
        jdbc=new JdbcTemplate(runtime);tx=new TransactionTemplate(new DataSourceTransactionManager(runtime));
        var registry=mock(McpToolRegistry.class);
        doAnswer(i->{jdbc.queryForObject("SELECT set_config('app.tenant_id',?,true)",String.class,i.getArgument(0).toString());jdbc.queryForObject("SELECT set_config('app.workspace_id',?,true)",String.class,i.getArgument(1)==null?"":i.getArgument(1).toString());return null;}).when(registry).scope(any(),nullable(UUID.class));
        service=new McpAgentBindingService(new NamedParameterJdbcTemplate(runtime),registry,mock(SecurityEventService.class),new ObjectMapper());
    }
    <T> T inTx(Supplier<T> action) { return tx.execute(status->action.get()); }
    void save(UUID agent,boolean enabled,boolean defaultAgent) { inTx(()->{service.save(tenant,workspace,new McpAgentBindingService.Registration(client,agent,enabled,defaultAgent),"test-admin");return null;}); }
    @BeforeEach void clear() { admin.update("DELETE FROM service_agent_bindings WHERE tenant_id=?",tenant); }

    @Test void multipleAgentsDefaultsAndDisableArePersistent() {
        save(orders,true,true);save(refunds,true,false);
        assertEquals(2,inTx(()->service.list(tenant,workspace)).size());
        assertEquals("order-agent",inTx(()->service.resolve(tenant,workspace,actor)).subject());
        assertEquals("refund-agent",inTx(()->service.resolve(tenant,workspace,actor.selecting("refund-agent"))).subject());
        String oldRevision=inTx(()->service.resolve(tenant,workspace,actor)).revision();
        save(refunds,true,true);
        assertEquals("refund-agent",inTx(()->service.resolve(tenant,workspace,actor)).subject());
        assertEquals(1,inTx(()->service.list(tenant,workspace)).stream().filter(r->Boolean.TRUE.equals(r.get("isDefault"))).count());
        save(orders,false,false);
        assertThrows(AccessDeniedException.class,()->inTx(()->service.resolve(tenant,workspace,actor.selecting("order-agent"))));
        save(orders,true,false);
        assertNotEquals(oldRevision,inTx(()->service.resolve(tenant,workspace,actor.selecting("order-agent"))).revision());
    }
    @Test void workspaceAndTenantIsolationAreEnforcedByQueriesAndRls() {
        save(orders,true,true);
        assertTrue(inTx(()->service.list(tenant,otherWorkspace)).isEmpty());
        assertThrows(AccessDeniedException.class,()->inTx(()->service.resolve(tenant,otherWorkspace,actor.selecting("order-agent"))));
        assertThrows(AccessDeniedException.class,()->inTx(()->{service.save(tenant,otherWorkspace,new McpAgentBindingService.Registration(client,orders,true,false),"admin");return null;}));
        inTx(()->{service.list(tenant,otherWorkspace);assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM service_agent_bindings",Integer.class));return null;});
        inTx(()->{service.list(UUID.randomUUID(),workspace);assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM service_agent_bindings",Integer.class));return null;});
    }
    @Test void revokedClientAndInactiveAgentBlockPreviouslyValidBindings() {
        save(orders,true,true);
        admin.update("UPDATE identities SET status='DISABLED' WHERE id=?",orders);
        try{assertThrows(AccessDeniedException.class,()->inTx(()->service.resolve(tenant,workspace,actor)));}
        finally{admin.update("UPDATE identities SET status='ACTIVE' WHERE id=?",orders);}
        admin.update("UPDATE api_clients SET status='REVOKED' WHERE id=?",client);
        try{assertThrows(AccessDeniedException.class,()->inTx(()->service.resolve(tenant,workspace,actor)));}
        finally{admin.update("UPDATE api_clients SET status='ACTIVE' WHERE id=?",client);}
    }
    @Test void changedConnectionExpiresPendingApprovalsAndBlocksSavedCalls() {
        save(orders,true,true);
        String revision=inTx(()->service.resolve(tenant,workspace,actor)).revision();
        UUID tool=UUID.randomUUID(),approval=UUID.randomUUID(),request=UUID.randomUUID(),call=UUID.randomUUID();
        admin.update("INSERT INTO agent_tools(id,tenant_id,name) VALUES (?,?,?)",tool,tenant,"test-tool");
        admin.update("INSERT INTO approvals(id,tenant_id,request_id,status,approval_type,expires_at) VALUES (?,?,?,'PENDING','MCP_EXECUTION',now()+interval '1 hour')",approval,tenant,request);
        admin.update("INSERT INTO mcp_invocations(id,tenant_id,workspace_id,actor_key,actor_subject,tool_id,binding_hash,arguments,arguments_hash,decision_request_id,policy_hash,approval_id,status,expires_at,policy_subject,agent_binding_revision) VALUES (?,?,?,?,?,?,'binding','{}','args',?,'policy',?,'PENDING_APPROVAL',now()+interval '1 hour',?,?)",call,tenant,workspace,actor.key(),actor.subject(),tool,request,approval,"order-agent",revision);
        save(orders,false,false);
        assertEquals("DENIED",admin.queryForObject("SELECT status FROM mcp_invocations WHERE id=?",String.class,call));
        assertEquals("AGENT_BINDING_CHANGED",admin.queryForObject("SELECT error_code FROM mcp_invocations WHERE id=?",String.class,call));
        assertEquals("EXPIRED",admin.queryForObject("SELECT status FROM approvals WHERE id=?",String.class,approval));
        assertEquals(actor.subject(),admin.queryForObject("SELECT actor_subject FROM mcp_invocations WHERE id=?",String.class,call));
        assertEquals("order-agent",admin.queryForObject("SELECT policy_subject FROM mcp_invocations WHERE id=?",String.class,call));
    }
}
