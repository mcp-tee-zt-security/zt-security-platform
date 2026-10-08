package com.zt.security.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.common.TenantSession;
import com.zt.security.common.WorkspaceSession;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;

/** Durable, tenant/workspace-scoped storage for the integrated SDK lifecycle. */
@Service
@Transactional
public class GovernanceStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TenantSession tenantSession;
    private final WorkspaceSession workspaceSession;
    private final com.zt.security.event.SecurityEventService events;

    public GovernanceStore(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper,
            TenantSession tenantSession, WorkspaceSession workspaceSession,com.zt.security.event.SecurityEventService events) {
        this.jdbc=jdbc; this.mapper=mapper; this.tenantSession=tenantSession; this.workspaceSession=workspaceSession;
        this.events=events;
    }

    public void scope(UUID tenant, UUID workspace) {
        tenantSession.set(tenant); workspaceSession.set(workspace);
    }

    private MapSqlParameterSource params(UUID tenant, UUID workspace) {
        return new MapSqlParameterSource("tenant",tenant).addValue("workspace",workspace);
    }

    private static final String SCOPE = "tenant_id=:tenant AND (workspace_id IS NULL OR workspace_id=:workspace)";

    public Map<String,Object> create(UUID tenant, UUID workspace, String kind, Map<String,Object> body) {
        scope(tenant,workspace);
        UUID id=UUID.randomUUID();
        Map<String,Object> record=new LinkedHashMap<>(body);
        record.put("id",id.toString()); record.put("tenantId",tenant.toString());
        record.put("createdAt",Instant.now().toString());
        if("EVENT".equals(kind)){
            record.put("eventId",id.toString());record.put("occurredAt",record.get("createdAt"));
            record.put("subject",record.getOrDefault("subject","governed-lifecycle"));
            record.put("workspaceId",workspace==null?null:workspace.toString());
            record.put("correlationId",record.get("traceId"));
            Object eventPayload=record.get("payload");
            if(eventPayload instanceof Map<?,?> payload){
                if(payload.get("subject")!=null)record.put("subject",payload.get("subject"));
                record.put("evidenceRefs",payload.get("evidenceIds") instanceof List<?> ids?ids:List.of());
            }else record.put("evidenceRefs",List.of());
        }
        jdbc.update("INSERT INTO governance_records(id,tenant_id,workspace_id,kind,payload) VALUES (:id,:tenant,:workspace,:kind,cast(:payload as jsonb))",
            params(tenant,workspace).addValue("id",id).addValue("kind",kind).addValue("payload",json(record)));
        if("EVENT".equals(kind))events.enqueue(tenant,workspace,String.valueOf(record.get("eventType")),"GOVERNANCE",id.toString(),json(record));
        return record;
    }

    public Map<String,Object> decision(UUID tenant, UUID workspace,
            com.zt.security.action.EvaluateModels.EvaluateRequest request,
            com.zt.security.action.EvaluateModels.EvaluateResponse response) {
        scope(tenant,workspace);
        var old=jdbc.query("SELECT payload::text FROM governance_records WHERE "+SCOPE+
            " AND kind='DECISION' AND payload->>'requestId'=:request",
            params(tenant,workspace).addValue("request",response.requestId().toString()),(rs,n)->parse(rs.getString(1)));
        if(!old.isEmpty())return old.get(0);
        Map<String,Object> value=new LinkedHashMap<>();
        value.put("requestId",response.requestId().toString());value.put("decision",response.decision());
        value.put("reason",response.reason());value.put("risk",response.risk());value.put("audit",response.audit());
        value.put("matchedPolicies",response.matchedPolicies());value.put("request",request);
        value.put("traceId",response.requestId().toString());value.put("decidedAt",Instant.now().toString());
        var result=create(tenant,workspace,"DECISION",value);
        create(tenant,workspace,"EVENT",Map.of("eventType","GOVERNANCE_POLICY_DECISION",
            "traceId",response.requestId().toString(),"subject",request.principal().id(),
            "payload",Map.of("decisionId",result.get("id"),"decision",response.decision())));
        return result;
    }

    public Map<String,Object> get(UUID tenant, UUID workspace, String kind, String id, boolean lock) {
        scope(tenant,workspace);
        var rows=jdbc.query("SELECT payload::text FROM governance_records WHERE "+SCOPE+" AND kind=:kind AND id=:id"+(lock?" FOR UPDATE":""),
            params(tenant,workspace).addValue("kind",kind).addValue("id",uuid(id)),(rs,n)->parse(rs.getString(1)));
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,kind+" not found");
        return rows.get(0);
    }

    public void update(UUID tenant, UUID workspace, String kind, Map<String,Object> record) {
        scope(tenant,workspace);
        int count=jdbc.update("UPDATE governance_records SET payload=cast(:payload as jsonb),updated_at=now() WHERE "+SCOPE+" AND id=:id AND kind=:kind",
            params(tenant,workspace).addValue("id",uuid(record.get("id"))).addValue("kind",kind).addValue("payload",json(record)));
        if(count!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,kind+" not found");
    }

    public List<Map<String,Object>> list(UUID tenant, UUID workspace, String kind, int limit) {
        scope(tenant,workspace);
        return jdbc.query("SELECT payload::text FROM governance_records WHERE "+SCOPE+" AND kind=:kind ORDER BY created_at DESC,id DESC LIMIT :limit",
            params(tenant,workspace).addValue("kind",kind).addValue("limit",Math.max(1,Math.min(limit,500))),
            (rs,n)->parse(rs.getString(1)));
    }

    public List<Map<String,Object>> events(UUID tenant, UUID workspace, String traceId, int limit) {
        scope(tenant,workspace);
        return jdbc.query("SELECT payload::text FROM governance_records WHERE "+SCOPE+
            " AND kind='EVENT' AND (:trace IS NULL OR payload->>'traceId'=:trace) ORDER BY created_at DESC,id DESC LIMIT :limit",
            params(tenant,workspace).addValue("trace",traceId,java.sql.Types.VARCHAR).addValue("limit",Math.max(1,Math.min(limit,500))),
            (rs,n)->parse(rs.getString(1)));
    }

    public List<Map<String,Object>> listByField(UUID tenant,UUID workspace,String kind,String field,String value,int limit){
        scope(tenant,workspace);
        return jdbc.query("SELECT payload::text FROM governance_records WHERE "+SCOPE+
            " AND kind=:kind AND payload->>:field=:value ORDER BY created_at DESC,id DESC LIMIT :limit",
            params(tenant,workspace).addValue("kind",kind).addValue("field",field).addValue("value",value)
                .addValue("limit",Math.max(1,Math.min(limit,500))),(rs,n)->parse(rs.getString(1)));
    }

    public Map<String,Long> counts(UUID tenant, UUID workspace) {
        scope(tenant,workspace);
        Map<String,Long> result=new LinkedHashMap<>();
        jdbc.query("SELECT kind,count(*) AS total FROM governance_records WHERE "+SCOPE+" GROUP BY kind",
            params(tenant,workspace),(org.springframework.jdbc.core.RowCallbackHandler)rs->result.put(rs.getString("kind"),rs.getLong("total")));
        return result;
    }

    public Map<String,Object> metrics(UUID tenant,UUID workspace){
        var counts=counts(tenant,workspace);
        Long failures=jdbc.queryForObject("SELECT count(*) FROM governance_records WHERE "+SCOPE+" AND kind='EXECUTION' AND payload->>'status'='FAILED'",params(tenant,workspace),Long.class);
        long total=counts.getOrDefault("EXECUTION",0L),failed=failures==null?0:failures;
        return Map.of("policyDecisions",counts.getOrDefault("DECISION",0L),"executions",total,
            "executionFailures",failed,"executionFailureRate",total==0?0.0:(double)failed/total,
            "verifications",counts.getOrDefault("VERIFICATION",0L),"eventCount",counts.getOrDefault("EVENT",0L),"recordCounts",counts);
    }

    public String json(Object value) {
        try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Invalid JSON payload",e);}
    }
    public Map<String,Object> parse(String value) {
        try{return mapper.readValue(value,new TypeReference<Map<String,Object>>(){});}catch(Exception e){throw new IllegalStateException("Invalid stored JSON",e);}
    }
    public static UUID uuid(Object value) {
        try{return UUID.fromString(String.valueOf(value));}catch(Exception e){throw new IllegalArgumentException("Valid UUID required");}
    }
}
