package com.zt.security.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.zt.security.common.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Projects safe fields from existing records. No source event is modified or duplicated. */
@Service @Transactional(readOnly=true)
public class UnifiedAuditService {
    private final NamedParameterJdbcTemplate jdbc;private final TenantSession tenants;private final WorkspaceSession workspaces;private final ObjectMapper json;
    public UnifiedAuditService(NamedParameterJdbcTemplate jdbc,TenantSession tenants,WorkspaceSession workspaces,ObjectMapper json){this.jdbc=jdbc;this.tenants=tenants;this.workspaces=workspaces;this.json=json;}
    private static final Set<String> KINDS=Set.of("ALL","MCP","FILE","WORKFLOW","DECISION","SYNC","AUTH","RUNTIME","CONTROL","OTHER");
    private static final String PROJECTION="""
      WITH events AS (
       SELECT source AS kind,id,occurred_at AS event_at,
        metadata || jsonb_build_object('caller',caller_subject,'humanSubject',human_subject,'aiSubject',ai_subject,
         'operation',action,'targetId',resource_id,'targetTitle',resource_type,'decision',decision,'status',status,
         'riskScore',risk_score,'requestId',request_id,'correlationId',correlation_id,'eventType',event_type,
         'stage',stage,'analysisEligible',analysis_eligible,'sourceEventId',origin_id,
         'executed',CASE WHEN stage='EXECUTION' AND status IN ('SUCCEEDED','TOOL_ERROR') THEN true WHEN stage='AUTHORIZATION' AND decision IN ('DENY','STEP_UP') THEN false ELSE null END) AS data
       FROM security_activities WHERE tenant_id=:tenant AND workspace_id IS NOT DISTINCT FROM CAST(:workspace AS uuid)
      )
      SELECT kind,id,event_at,data::text AS data FROM events
      WHERE (:allAccess OR data->>'caller'=:reader OR data->>'humanSubject'=:reader OR data->>'aiSubject'=:reader)
      """;
    private MapSqlParameterSource scope(UUID t,UUID w,Authentication auth){
        if(auth==null||!auth.isAuthenticated())throw new AccessDeniedException("Authentication required");
        tenants.set(t);workspaces.set(w);
        boolean all=auth.getAuthorities().stream().anyMatch(a->Set.of("ROLE_PLATFORM","ROLE_ADMIN","ROLE_AUDITOR","ROLE_POLICY_MANAGER","ROLE_SOC_ANALYST").contains(a.getAuthority()));
        return new MapSqlParameterSource().addValue("tenant",t).addValue("workspace",w).addValue("reader",auth.getName()).addValue("allAccess",all);
    }
    private void checkKind(String kind){if(!KINDS.contains(kind))throw bad("Invalid audit kind");}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private List<Map<String,Object>> read(String sql,MapSqlParameterSource p){
        return jdbc.query(sql,p,(rs,n)->{
            Map<String,Object> row;try{row=json.readValue(rs.getString("data"),new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalStateException("Invalid stored audit projection",e);}
            row.put("id",rs.getObject("id").toString());row.put("kind",rs.getString("kind"));row.put("createdAt",rs.getTimestamp("event_at").toInstant().toString());return row;
        });
    }
    public Object list(UUID t,UUID w,String kind,String decision,String subject,String cursor,int limit,Authentication auth){
        checkKind(kind);if(!Set.of("ALL","ALLOW","DENY","STEP_UP").contains(decision)||subject.length()>512||subject.chars().anyMatch(Character::isISOControl)||limit<1||limit>100)throw bad("Invalid audit filter or page size");
        var p=scope(t,w,auth).addValue("kind",kind).addValue("decision",decision).addValue("subject",subject).addValue("limit",limit+1);
        String sql=PROJECTION+" AND (:kind='ALL' OR kind=:kind) AND (:decision='ALL' OR data->>'decision'=:decision) AND (:subject='' OR data->>'caller'=:subject OR data->>'humanSubject'=:subject OR data->>'aiSubject'=:subject)";
        if(cursor!=null&&!cursor.isBlank()){
            try{if(cursor.length()>1024)throw new IllegalArgumentException();String[] parts=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8).split("\\|",2);Instant before=Instant.parse(parts[0]);if(parts.length!=2||!parts[1].matches("[A-Z]+:[0-9a-f-]{36}"))throw new IllegalArgumentException();p.addValue("before",before.toString()).addValue("key",parts[1]);sql+=" AND (event_at,kind||':'||id::text)<(CAST(:before AS timestamptz),:key)";}catch(RuntimeException e){throw bad("Invalid audit cursor");}
        }
        var rows=read(sql+" ORDER BY event_at DESC,kind DESC,id::text DESC LIMIT :limit",p);boolean more=rows.size()>limit;var items=rows.subList(0,Math.min(limit,rows.size()));
        String next="";if(more&&!items.isEmpty()){var last=items.get(items.size()-1);next=Base64.getUrlEncoder().withoutPadding().encodeToString((last.get("createdAt")+"|"+last.get("kind")+":"+last.get("id")).getBytes(StandardCharsets.UTF_8));}
        return Map.of("items",items,"nextCursor",next,"workspaceId",w,"visibility",Boolean.TRUE.equals(p.getValue("allAccess"))?"WORKSPACE":"OWN_RECORDS");
    }
    public Object detail(UUID t,UUID w,String kind,UUID id,Authentication auth){checkKind(kind);var rows=read(PROJECTION+" AND kind=:kind AND id=:id",scope(t,w,auth).addValue("kind",kind).addValue("id",id));if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Audit event unavailable in this workspace");return rows.get(0);}
}
