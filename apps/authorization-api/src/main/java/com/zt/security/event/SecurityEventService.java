package com.zt.security.event;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.namedparam.*;
import java.time.*;
import java.util.*;
/** Domain observations and delivery messages commit in the same transaction. */
@Service @Transactional
public class SecurityEventService {
 private final SecurityEventOutboxRepository repo; private final SecurityActivityLedger ledger;
 private final ObjectMapper json; private final NamedParameterJdbcTemplate jdbc;
 public SecurityEventService(SecurityEventOutboxRepository repo,SecurityActivityLedger ledger,ObjectMapper json,NamedParameterJdbcTemplate jdbc){this.repo=repo;this.ledger=ledger;this.json=json;this.jdbc=jdbc;}
 public UUID publish(CommonSecurityEvent event){var write=ledger.record(event);if(write.inserted())outbox(event.tenantId(),event.workspaceId(),event.eventType(),event.source(),event.originId(),ledger.envelope(write.id(),event));return write.id();}
 public UUID enqueue(UUID tenant,UUID workspace,String type,String aggregateType,String aggregateId,String payload){
  Map<String,Object> body;
  try{body=json.readValue(payload==null?"{}":payload,new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception ex){throw new IllegalArgumentException("Invalid security event payload",ex);}
  String source="MCP_CALL".equals(aggregateType)?"MCP":"GOVERNANCE".equals(aggregateType)?"WORKFLOW":Set.of("CONTROL","SECURITY_RESPONSE","SECURITY_CASE").contains(aggregateType)?"CONTROL":"OTHER";
  var authenticated=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
  String caller=authenticated==null?null:authenticated.getName(),ai=null,human=null,correlation=value(body,"traceId");UUID request=null;
  String status=value(body,"status"),action=type,resourceType=aggregateType,resource=aggregateId;
  Map<String,Object> metadata=new LinkedHashMap<>(body);
  if("MCP".equals(source)){
   var rows=jdbc.queryForList("SELECT actor_subject,policy_subject,decision_request_id,tool_id,status,approval_id FROM mcp_invocations WHERE tenant_id=:tenant AND workspace_id=:workspace AND id=CAST(:id AS uuid)",new MapSqlParameterSource("tenant",tenant).addValue("workspace",workspace).addValue("id",aggregateId));
   if(!rows.isEmpty()){var row=rows.get(0);caller=value(row,"actor_subject");ai=value(row,"policy_subject");request=(UUID)row.get("decision_request_id");status=value(row,"status");resource=value(row,"tool_id");metadata.put("callId",aggregateId);metadata.put("approvalId",row.get("approval_id"));metadata.put("toolId",resource);correlation=aggregateId;action="mcp.tool.call";resourceType="mcp_tool";}
  }
  var event=new CommonSecurityEvent(tenant,workspace,source,type,"MCP".equals(source)?"EXECUTION":"LIFECYCLE",aggregateId+":"+UUID.randomUUID(),Instant.now(),correlation,request,caller,human,ai,action,resourceType,resource,null,status,null,false,metadata);
  var write=ledger.record(event);
  return outbox(tenant,workspace,type,aggregateType,aggregateId,ledger.envelope(write.id(),event));
 }
 private static String value(Map<String,?> body,String key){Object value=body.get(key);return value==null?null:String.valueOf(value);}
 private UUID outbox(UUID tenant,UUID workspace,String type,String aggregateType,String id,String payload){var e=new SecurityEventOutbox();e.setId(UUID.randomUUID());e.setTenantId(tenant);e.setWorkspaceId(workspace);e.setEventType(type);e.setAggregateType(aggregateType);e.setAggregateId(id);e.setPayload(payload);return repo.save(e).getId();}
 public List<SecurityEventOutbox> pending(){return repo.findTop100ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc("PENDING",Instant.now());}
}
