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
    private static final Set<String> KINDS=Set.of("ALL","MCP","FILE","WORKFLOW","DECISION","SYNC");
    private static final String PROJECTION="""
      WITH events AS (
        SELECT 'MCP' AS kind,i.id,i.updated_at AS event_at,jsonb_build_object(
          'caller',i.actor_subject,'humanSubject',null,
          'aiSubject',CASE WHEN a.identity_type='AI_AGENT' THEN coalesce(i.policy_subject,i.actor_subject) END,'operation','mcp.tool.call','targetId',i.tool_id,
          'targetTitle',t.name,'decision',coalesce(a.decision,CASE WHEN i.status='DENIED' THEN 'DENY' WHEN i.status='PENDING_APPROVAL' THEN 'STEP_UP' WHEN i.status IN ('SUCCEEDED','TOOL_ERROR','EXECUTING') THEN 'ALLOW' END),
          'status',i.status,'reason',coalesce(a.reason,i.error_code,i.status),'returnedCount',null,
          'auditEventId',a.id,'requestId',i.decision_request_id,'approvalId',i.approval_id,
          'approvalStatus',p.status,'approvedBy',p.decided_by,'riskScore',a.risk_score,
          'matchedPolicies',coalesce(a.metadata->'matchedPolicies','[]'::jsonb),'resultHash',i.result_hash,
          'executed',CASE WHEN i.status IN ('SUCCEEDED','TOOL_ERROR') THEN true WHEN i.status IN ('DENIED','PENDING_APPROVAL','NOT_EXECUTED') THEN false ELSE null END
        ) AS data FROM mcp_invocations i
        LEFT JOIN agent_tools t ON t.tenant_id=i.tenant_id AND t.id=i.tool_id
        LEFT JOIN LATERAL(SELECT * FROM audit_logs a WHERE a.tenant_id=i.tenant_id AND a.request_id=i.decision_request_id AND (a.workspace_id=i.workspace_id OR a.workspace_id IS NULL) ORDER BY a.created_at DESC,a.id DESC LIMIT 1) a ON true
        LEFT JOIN approvals p ON p.tenant_id=i.tenant_id AND p.id=i.approval_id
        WHERE i.tenant_id=:tenant AND i.workspace_id=:workspace
        UNION ALL
        SELECT CASE WHEN f.operation IN ('sync','syncSubject','delete','checkpoint','syncAcl') THEN 'SYNC' ELSE 'FILE' END,
          f.id,f.created_at,jsonb_build_object(
          'caller',f.subject,'humanSubject',f.human_subject,'aiSubject',coalesce(f.ai_subject,CASE WHEN f.subject LIKE 'client:%' OR f.subject LIKE 'workflow-chat-%' THEN f.subject END),
          'operation',f.operation,'targetId',f.resource_id,'targetTitle',CASE WHEN :allAccess THEN r.title END,'decision',f.decision,
          'status',CASE WHEN f.decision='ALLOW' THEN 'SUCCEEDED' ELSE 'DENIED' END,'reason',f.policy_reason,
          'returnedCount',f.returned_count,'auditEventId',f.id,'matchedPolicies',f.matched_policies,
          'aclVersion',f.acl_version,'sessionId',f.session_id,'sourceAclRequired',true
        ) FROM stitch_integration_audit f LEFT JOIN stitch_resources r ON r.tenant_id=f.tenant_id AND r.workspace_id=f.workspace_id AND r.resource_id=f.resource_id
        WHERE f.tenant_id=:tenant AND f.workspace_id=:workspace
        UNION ALL
        SELECT CASE WHEN a.action IN ('chat.respond','simulation.transfer') THEN 'WORKFLOW' ELSE 'DECISION' END,
          a.id,a.created_at,jsonb_build_object(
          'caller',coalesce(a.metadata->'context'->>'requestedBy',a.metadata->>'principal'),
          'humanSubject',a.metadata->'context'->>'requestedBy','aiSubject',a.metadata->>'principal',
          'operation',a.action,'targetId',a.resource_id,'targetTitle',a.resource_type,
          'decision',a.decision,'status',CASE WHEN a.decision='DENY' THEN 'DENIED' WHEN a.decision='STEP_UP' THEN 'PENDING_APPROVAL' ELSE 'EVALUATED' END,
          'reason',a.reason,'riskScore',a.risk_score,'auditEventId',a.id,'requestId',a.request_id,
          'matchedPolicies',coalesce(a.metadata->'matchedPolicies','[]'::jsonb),'executed',false
        ) FROM audit_logs a WHERE a.tenant_id=:tenant AND a.workspace_id=:workspace
          AND NOT EXISTS(SELECT 1 FROM mcp_invocations i WHERE i.tenant_id=a.tenant_id AND i.workspace_id=a.workspace_id AND i.decision_request_id=a.request_id)
        UNION ALL
        SELECT 'WORKFLOW',e.id,e.created_at,jsonb_build_object(
          'caller',e.payload->>'executedBy','humanSubject',d.payload->'request'->'context'->>'requestedBy',
          'aiSubject',d.payload->'request'->'principal'->>'id','operation','workflow.execute',
          'targetId',e.payload->>'contractId','targetTitle','Saved execution contract',
          'decision',d.payload->>'decision','status',e.payload->>'status','reason',coalesce(e.payload->>'reason',d.payload->>'reason'),
          'contractId',e.payload->>'contractId','executionId',e.id,'executedBy',e.payload->>'executedBy',
          'requestId',d.payload->>'requestId','approvalId',c.payload->>'approvalId','approvalStatus',p.status,'approvedBy',p.decided_by,
          'matchedPolicies',coalesce(d.payload->'matchedPolicies','[]'::jsonb),'executed',CASE WHEN e.payload->>'status'='SUCCEEDED' THEN true WHEN e.payload->>'status'='UNSUPPORTED' THEN false ELSE null END
        ) FROM governance_records e
        LEFT JOIN governance_records c ON c.tenant_id=e.tenant_id AND c.workspace_id=e.workspace_id AND c.kind='CONTRACT' AND c.id::text=e.payload->>'contractId'
        LEFT JOIN governance_records d ON d.tenant_id=e.tenant_id AND d.workspace_id=e.workspace_id AND d.kind='DECISION' AND d.id::text=c.payload->>'decisionId'
        LEFT JOIN approvals p ON p.tenant_id=e.tenant_id AND p.id::text=c.payload->>'approvalId'
        WHERE e.tenant_id=:tenant AND e.workspace_id=:workspace AND e.kind='EXECUTION'
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
