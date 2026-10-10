package com.zt.security.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.event.SecurityEventService;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional
public class McpAgentBindingService {
    public record Principal(String subject, String revision) {
        static Principal legacy(McpActor actor) { return new Principal(actor.subject(), "legacy"); }
    }
    public record Registration(UUID clientId, UUID agentId, boolean enabled, boolean isDefault) {}
    private final NamedParameterJdbcTemplate jdbc;
    private final McpToolRegistry registry;
    private final SecurityEventService events;
    private final ObjectMapper mapper;
    private static final String SCOPE = "b.tenant_id=:tenant AND b.workspace_id IS NOT DISTINCT FROM CAST(:workspace AS uuid)";
    private static final String JOIN = " FROM service_agent_bindings b JOIN api_clients c ON c.id=b.client_id AND c.tenant_id=b.tenant_id"
        + " JOIN identities a ON a.id=b.agent_id AND a.tenant_id=b.tenant_id ";

    McpAgentBindingService(NamedParameterJdbcTemplate jdbc, McpToolRegistry registry, SecurityEventService events, ObjectMapper mapper) {
        this.jdbc=jdbc;this.registry=registry;this.events=events;this.mapper=mapper;
    }
    public Principal resolve(UUID tenant, UUID workspace, McpActor actor) {
        registry.scope(tenant,workspace);
        if (!actor.serviceClient()) {
            if(actor.requestedAgent()!=null)throw new AccessDeniedException("Agent selection requires a registered service client");
            return Principal.legacy(actor);
        }
        var rows=jdbc.queryForList("SELECT a.external_id,a.identity_type,a.status AS agent_status,c.status AS client_status,"
            + "(c.expires_at IS NULL OR c.expires_at>now()) AS client_valid,c.workspace_id,b.enabled,b.is_default,b.revision" + JOIN
            + " WHERE " + SCOPE + " AND c.client_id=:client FOR SHARE OF b,c,a",
            McpToolRegistry.params(tenant,workspace).addValue("client",actor.subject().substring(7)));
        if (rows.isEmpty()) {
            if(actor.requestedAgent()!=null)throw new AccessDeniedException("Agent is not linked to this service in this workspace");
            return Principal.legacy(actor);
        }
        var candidates=actor.requestedAgent()!=null
            ? rows.stream().filter(r->actor.requestedAgent().equals(r.get("external_id"))).toList()
            : rows.stream().filter(r->Boolean.TRUE.equals(r.get("is_default"))).toList();
        if(actor.requestedAgent()==null && candidates.isEmpty())candidates=rows.stream().filter(r->Boolean.TRUE.equals(r.get("enabled"))).toList();
        if(candidates.size()!=1)throw new AccessDeniedException("Select a linked Agent using X-ZT-Agent-Id or configure one default Agent");
        var row=candidates.get(0);
        if (!Boolean.TRUE.equals(row.get("enabled")) || !"ACTIVE".equals(row.get("agent_status"))
            || !"AI_AGENT".equals(row.get("identity_type")) || !"ACTIVE".equals(row.get("client_status"))
            || !Boolean.TRUE.equals(row.get("client_valid"))
            || row.get("workspace_id")!=null && !row.get("workspace_id").equals(workspace))
            throw new AccessDeniedException("Service Agent binding is disabled or unavailable");
        return new Principal((String)row.get("external_id"),row.get("revision").toString());
    }
    public List<Map<String,Object>> list(UUID tenant,UUID workspace) {
        registry.scope(tenant,workspace);
        return jdbc.queryForList("SELECT b.id,b.client_id AS \"clientId\",c.client_id AS \"clientExternalId\","
            + "b.agent_id AS \"agentId\",a.external_id AS \"agentExternalId\",a.name AS \"agentName\","
            + "b.enabled,b.is_default AS \"isDefault\",b.revision,b.updated_at AS \"updatedAt\"" + JOIN + " WHERE " + SCOPE + " ORDER BY c.client_id",
            McpToolRegistry.params(tenant,workspace));
    }
    public List<Map<String,Object>> options(UUID tenant,UUID workspace,McpActor actor) {
        registry.scope(tenant,workspace);
        if(!actor.serviceClient())return List.of();
        return jdbc.queryForList("SELECT a.external_id AS \"agentExternalId\",a.name AS \"agentName\",b.enabled,b.is_default AS \"isDefault\""+JOIN
            +" WHERE "+SCOPE+" AND c.client_id=:client AND c.status='ACTIVE' AND (c.expires_at IS NULL OR c.expires_at>now())"
            +" AND (c.workspace_id IS NULL OR c.workspace_id=:workspace) AND a.status='ACTIVE' AND a.identity_type='AI_AGENT' ORDER BY a.external_id",
            McpToolRegistry.params(tenant,workspace).addValue("client",actor.subject().substring(7)));
    }
    public void save(UUID tenant,UUID workspace,Registration value,String changedBy) {
        registry.scope(tenant,workspace);
        if(value==null || value.clientId()==null || value.agentId()==null || value.isDefault()&&!value.enabled())throw new IllegalArgumentException("Client and Agent are required");
        var p=McpToolRegistry.params(tenant,workspace).addValue("client",value.clientId()).addValue("agent",value.agentId()).addValue("enabled",value.enabled());
        var clients=jdbc.queryForList("SELECT id FROM api_clients WHERE id=:client AND tenant_id=:tenant"
            + " AND (:enabled=false OR (status='ACTIVE' AND (expires_at IS NULL OR expires_at>now())))"
            + " AND (workspace_id IS NULL OR workspace_id=:workspace) FOR SHARE",p);
        var agents=jdbc.queryForList("SELECT id FROM identities WHERE id=:agent AND tenant_id=:tenant AND identity_type='AI_AGENT' AND (:enabled=false OR status='ACTIVE') FOR SHARE",p);
        if(clients.isEmpty()||agents.isEmpty())throw new AccessDeniedException("Active client and AI Agent must belong to this tenant/scope");
        // Serialize first registration as well as replacement; all changes invalidate pending approvals.
        String key=McpJson.digest(tenant+":"+workspace+":agent-binding:"+value.clientId());
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(:lock)",new MapSqlParameterSource("lock",Long.parseUnsignedLong(key.substring(0,16),16)),Object.class);
        var ids=jdbc.queryForList("SELECT b.id FROM service_agent_bindings b WHERE "+SCOPE+" AND b.client_id=:client AND b.agent_id=:agent FOR UPDATE",p);
        if(value.isDefault())jdbc.update("UPDATE service_agent_bindings b SET is_default=false WHERE "+SCOPE+" AND b.client_id=:client",p);
        p.addValue("revision",UUID.randomUUID()).addValue("enabled",value.enabled()).addValue("default",value.isDefault());
        if(ids.isEmpty())jdbc.update("INSERT INTO service_agent_bindings(id,tenant_id,workspace_id,client_id,agent_id,revision,enabled,is_default) VALUES (:id,:tenant,:workspace,:client,:agent,:revision,:enabled,:default)",p.addValue("id",UUID.randomUUID()));
        else jdbc.update("UPDATE service_agent_bindings b SET agent_id=:agent,revision=:revision,enabled=:enabled,is_default=:default,updated_at=now() WHERE "+SCOPE+" AND b.id=:id",p.addValue("id",ids.get(0).get("id")));
        String affected="i.tenant_id=:tenant AND i.workspace_id IS NOT DISTINCT FROM CAST(:workspace AS uuid)"
            +" AND i.actor_subject=(SELECT 'client:'||client_id FROM api_clients WHERE id=:client AND tenant_id=:tenant)"
            +" AND (i.policy_subject=(SELECT external_id FROM identities WHERE id=:agent AND tenant_id=:tenant)"
            +" OR i.agent_binding_revision IS NULL OR i.agent_binding_revision='legacy')";
        jdbc.update("UPDATE mcp_invocations i SET status='DENIED',error_code='AGENT_BINDING_CHANGED',updated_at=now() WHERE "
            +affected+" AND i.status='PENDING_APPROVAL'",p);
        jdbc.update("UPDATE approvals SET status='EXPIRED',expires_at=now() WHERE tenant_id=:tenant AND status='PENDING'"
            +" AND approval_type='MCP_EXECUTION' AND id IN (SELECT i.approval_id FROM mcp_invocations i WHERE "
            +affected+" AND i.status='DENIED' AND i.error_code='AGENT_BINDING_CHANGED')",p);
        events.enqueue(tenant,workspace,"SERVICE_AGENT_BINDING_CHANGED","SERVICE_CLIENT",value.clientId().toString(),
            mapper.valueToTree(Map.of("clientId",value.clientId(),"agentId",value.agentId(),"enabled",value.enabled(),"isDefault",value.isDefault(),"changedBy",changedBy)).toString());
    }
}
