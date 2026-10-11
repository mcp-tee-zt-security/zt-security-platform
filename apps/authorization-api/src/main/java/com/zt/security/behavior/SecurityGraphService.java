package com.zt.security.behavior;
import com.zt.security.event.SecurityActivityLedger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
/** Graph of observed, scoped activity. Authorization is not evidence of execution. */
@Service
public class SecurityGraphService {
 @org.springframework.beans.factory.annotation.Autowired private AgentBehaviorService behavior;
 private final SecurityActivityLedger ledger;
 public SecurityGraphService(SecurityActivityLedger ledger){this.ledger=ledger;}
 @Transactional(readOnly=true) public Map<String,Object> snapshot(UUID tenant,int minutes){return snapshot(tenant,ledger.selectedWorkspace(),minutes);}
 @Transactional(readOnly=true) public Map<String,Object> snapshot(UUID tenant,UUID workspace,int minutes){
  var rows=ledger.recent(tenant,workspace,Instant.now().minusSeconds(Math.max(5,Math.min(minutes,10080))*60L),1000);
  Map<String,Map<String,Object>> nodes=new LinkedHashMap<>();List<Map<String,Object>> edges=new ArrayList<>();
  for(var row:rows){
   String ai=(String)row.get("aiSubject"),human=(String)row.get("humanSubject"),caller=(String)row.get("caller");
   String actor=ai!=null?"agent:"+ai:caller!=null?"identity:"+caller:null;
   if(actor==null)continue;
   Double score=row.get("riskScore") instanceof Number n?n.doubleValue():null;
   node(nodes,actor,ai!=null?"AGENT":"IDENTITY",ai!=null?ai:caller,"OBSERVED",score);
   if(human!=null&&ai!=null){String h="identity:"+human;node(nodes,h,"IDENTITY",human,"DELEGATOR",null);edges.add(edge(h,actor,"DELEGATED_CONTEXT",row));}
   String action="action:"+workspace+":"+row.get("id");
   String status=String.valueOf(row.get("status"));node(nodes,action,"ACTION",String.valueOf(row.get("action")),status,score);
   String relation="AUTHORIZATION".equals(row.get("stage"))?"EVALUATED":"EXECUTION".equals(row.get("stage"))&&Set.of("SUCCEEDED","TOOL_ERROR").contains(status)?"EXECUTED":"OBSERVED";
   edges.add(edge(actor,action,relation,row));
   if("MCP".equals(row.get("source"))){String gateway="mcp-gateway:"+tenant+":"+workspace;node(nodes,gateway,"MCP_GATEWAY","ZT MCP Gateway","ENFORCED",null);edges.add(edge(actor,gateway,"ROUTES_THROUGH",row));edges.add(edge(gateway,action,"ENFORCES",row));}
   if("FILE".equals(row.get("source"))){String gateway="retrieval-gateway:"+tenant+":"+workspace;node(nodes,gateway,"RETRIEVAL_GATEWAY","ZT Retrieval Access","ENFORCED",null);edges.add(edge(actor,gateway,"ROUTES_THROUGH",row));edges.add(edge(gateway,action,"ENFORCES",row));}
   if(row.get("resourceId")!=null){String resource="resource:"+workspace+":"+row.get("resourceType")+":"+row.get("resourceId");node(nodes,resource,"RESOURCE",String.valueOf(row.get("resourceId")),String.valueOf(row.get("decision")),null);edges.add(edge(action,resource,"TARGETS",row));}
   if(row.get("metadata") instanceof Map<?,?> metadata&&metadata.get("matchedPolicies") instanceof List<?> policies)for(Object item:policies)if(item instanceof Map<?,?> policy&&policy.get("id")!=null){String id="policy:"+policy.get("id");node(nodes,id,"POLICY",String.valueOf(policy.containsKey("name")?policy.get("name"):policy.get("id")),String.valueOf(policy.get("effect")),null);edges.add(edge(id,action,"GOVERNS",row));}
  }
  var requestIds=rows.stream().map(r->r.get("requestId")).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
  var anomalies=behavior.anomalies(tenant,null).stream().filter(a->requestIds.contains(a.getRequestId())).toList();
  for(var anomaly:anomalies){String actor="agent:"+anomaly.getAgentExternalId(),id="anomaly:"+anomaly.getId();node(nodes,actor,"AGENT",anomaly.getAgentExternalId(),"OBSERVED",anomaly.getScore());node(nodes,id,"BEHAVIOR",anomaly.getAnomalyType(),anomaly.getSeverity(),anomaly.getScore());edges.add(edge(actor,id,"ANOMALY",Map.of("requestId",anomaly.getRequestId())));}
  double max=rows.stream().filter(r->r.get("riskScore") instanceof Number).mapToDouble(r->((Number)r.get("riskScore")).doubleValue()).max().orElse(0);
  Map<String,Object> out=new LinkedHashMap<>();out.put("tenantId",tenant);out.put("workspaceId",workspace);out.put("generatedAt",Instant.now());out.put("windowMinutes",minutes);out.put("nodes",nodes.values());out.put("edges",edges);out.put("metrics",Map.of("nodeCount",nodes.size(),"edgeCount",edges.size(),"maxRisk",max,"maxRiskMeasured",rows.stream().anyMatch(r->r.get("riskScore") instanceof Number),"anomalies",anomalies.size(),"activityCount",rows.size(),"unmeasuredEvents",rows.stream().filter(r->r.get("riskScore")==null).count()));out.put("scope","Observed common activity; missing risk is not a measured zero; anomaly records remain in runtime analysis");return out;
 }
 private static void node(Map<String,Map<String,Object>> nodes,String id,String type,String label,String state,Double risk){var row=nodes.computeIfAbsent(id,k->new LinkedHashMap<>());row.put("id",id);row.put("type",type);row.put("label",label);row.put("state",state);row.put("status",state);row.put("risk",risk==null?0:Math.max(risk,((Number)row.getOrDefault("risk",0)).doubleValue()));row.put("riskMeasured",risk!=null||Boolean.TRUE.equals(row.get("riskMeasured")));}
 private static Map<String,Object> edge(String from,String to,String type,Map<String,Object> event){var row=new LinkedHashMap<String,Object>();row.put("from",from);row.put("to",to);row.put("type",type);row.put("relation",type);var meta=new LinkedHashMap<String,Object>();for(String key:List.of("id","decision","stage","status","source","correlationId","requestId"))meta.put(key,event.get(key));row.put("metadata",meta);return row;}
}
