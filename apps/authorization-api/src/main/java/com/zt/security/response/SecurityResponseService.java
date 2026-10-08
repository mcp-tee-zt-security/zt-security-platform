package com.zt.security.response;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.agent.*;
import com.zt.security.blast.*;
import com.zt.security.common.TenantSession;
import com.zt.security.event.SecurityEventService;
import com.zt.security.runtime.AgentRuntimeSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service public class SecurityResponseService {
    @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.critical-threshold:85.0}")
    private double criticalThreshold;
    @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.high-threshold:65.0}")
    private double highThreshold;
    @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.elevated-threshold:40.0}")
    private double elevatedThreshold;

 private final SecurityResponseActionRepository repo;
 private final BlastRadiusAssessmentRepository assessments;
 private final AgentRuntimeSessionRepository sessions;
 private final AgentToolRepository tools;
 private final SecurityEventService events;
 private final TenantSession tenant;
 private final ObjectMapper mapper;
 SecurityResponseService(SecurityResponseActionRepository r,BlastRadiusAssessmentRepository a,
 AgentRuntimeSessionRepository s,AgentToolRepository t,SecurityEventService e,
 TenantSession ts,ObjectMapper m){
     repo=r;
     assessments=a;
     sessions=s;
     tools=t;
     events=e;
     tenant=ts;
     mapper=m;
     }
 @Transactional public List<Map<String,Object>> propose(UUID t,UUID assessmentId,
 String requestedBy){
     tenant.set(t);
     var a=assessments.findById(assessmentId).filter(x->
     t.equals(x.getTenantId())).orElseThrow(()->new SecurityException("assessment not found"));
     Map<String,Object> result=read(a.getResult());
     List<Map<String,Object>> recs=asList(result.get("recommendations"));
     List<Map<String,Object>> out=new ArrayList<>();
     for(Map<String,Object> r:recs){
         String type=mapType(String.valueOf(r.get("type")),String.valueOf(r.getOrDefault("title",
         "")),String.valueOf(r.getOrDefault("control","")));
         if(type==null) continue;
         String target=String.valueOf(r.getOrDefault("target","agent:"+result.getOrDefault("source",
         "")));
         if(repo.findTop100ByTenantIdOrderByCreatedAtDesc(t).stream().anyMatch(x->
         x.getAssessmentId()!=null&&x.getAssessmentId().equals(assessmentId)&&x.getTarget().equals(target)&&
         x.getStatus().equals("PENDING"))) continue;
         SecurityResponseAction x=new SecurityResponseAction();
         x.setTenantId(t);
         x.setAssessmentId(assessmentId);
         x.setActionType(type);
         x.setTarget(target);
         x.setPriority(String.valueOf(r.getOrDefault("priority","MEDIUM")));
         x.setReason(String.valueOf(r.getOrDefault("control",
         r.getOrDefault("title","Blast radius remediation"))));
         x.setRequestedBy(requestedBy);
         x.setRequiresApproval(true);
         repo.save(x);
         out.add(view(x));
         }
         return out;
 }
 @Transactional public Map<String,Object> approve(UUID t,UUID id,String approver){
     tenant.set(t);
     SecurityResponseAction x=get(t,id);
     if(!"PENDING".equals(x.getStatus()))throw new
IllegalStateException("action is not pending");
     if(approver==null||approver.isBlank())throw new IllegalArgumentException("approver is required");
     if(approver.equalsIgnoreCase(x.getRequestedBy()))throw new SecurityException("self-approval is not allowed");
     x.setApprovedBy(approver);
     x.setApprovedAt(Instant.now());
     x.setStatus("APPROVED");
     repo.save(x);
     audit(t,x,"SECURITY_RESPONSE_APPROVED",Map.of("approver",
     approver));
     return view(x);
     }
 @Transactional public Map<String,Object> reject(UUID t,UUID id,String approver,
 String reason){
     tenant.set(t);
     SecurityResponseAction x=get(t,id);
     if(!"PENDING".equals(x.getStatus()))throw
new IllegalStateException("action is not pending");
     if(approver.equalsIgnoreCase(x.getRequestedBy()))throw new SecurityException("self-rejection is not allowed");
     x.setApprovedBy(approver);
     x.setStatus("REJECTED");
     x.setReason((reason==null?
     "Rejected":reason)+" | original: "+x.getReason());
     repo.save(x);
     audit(t,x,"SECURITY_RESPONSE_REJECTED",Map.of("approver",
     approver));
     return view(x);
     }
 @Transactional public Map<String,Object> execute(UUID t,UUID id,String executor){
     tenant.set(t);
     SecurityResponseAction x=get(t,id);
     if(!"APPROVED".equals(x.getStatus()))throw new
IllegalStateException("action must be approved before execution");
     Map<String,Object> r=new LinkedHashMap<>();
     switch(x.getActionType()){
  case "ISOLATE_AGENT" -> {
      String agent=strip(x.getTarget(),"agent:");
      var rows=sessions.findTop50ByTenantIdOrderByLastSeenAtDesc(t).stream().filter(s->
      agent.equals(s.getAgentExternalId())&&"ACTIVE".equals(s.getStatus())).toList();
      rows.forEach(s->{
          s.setStatus("ENDED");s.setEndedAt(Instant.now());s.setLastSeenAt(Instant.now());
          sessions.save(s);
          }
          );
          r.put("endedSessions",rows.size());
          r.put("agent",agent);
  }
  case "REVOKE_MCP_TOOL" -> {
      UUID tid=UUID.fromString(strip(x.getTarget(),
      "tool:"));
      AgentTool tool=tools.findById(tid).filter(v->t.equals(v.getTenantId())).orElseThrow(()->
      new SecurityException("tool not found"));
      tool.setEnabled(false);
      tools.save(tool);
      r.put("tool",tool.getName());
      r.put("enabled",
      false);
      }
  case "CREATE_DENY_POLICY" -> {
      r.put("status","POLICY_DRAFT_REQUIRED");
      r.put("message","A policy draft must be reviewed/published through Policy Control Plane");
  }
  case "STEP_UP_POLICY" -> {
      r.put("status","POLICY_DRAFT_REQUIRED");
      r.put("message",
      "A STEP_UP policy draft must be reviewed/published through Policy Control Plane");
  }
  case "ROTATE_CREDENTIAL" -> {
      r.put("status","EXTERNAL_ROTATION_REQUIRED");
      r.put("message","Credential rotation must be executed by the configured secret manager");
  }
  default -> throw new IllegalArgumentException("unsupported response action: "+x.getActionType());
  }
 x.setExecutionResult(json(r));
 x.setExecutedAt(Instant.now());
 x.setStatus("EXECUTED");
 repo.save(x);
 audit(t,x,"SECURITY_RESPONSE_EXECUTED",r);
 return view(x);
 }
 @Transactional public List<Map<String,Object>> proposeFromForecast(UUID t,
 String agent,String requestedBy,double forecastScore,double probability,
 String recommendation){
  tenant.set(t);
  String actionType = forecastScore >= criticalThreshold || probability >= .85 ? "ISOLATE_AGENT" :
  (forecastScore >= highThreshold || probability >= .60 ? "STEP_UP_POLICY" : "CREATE_DENY_POLICY");
  String target = "agent:" + agent;
  String reason = "3.15 forecast control loop: score=" + forecastScore + ", highRiskProbability=" +
  probability + ", recommendation=" + recommendation;
  if(repo.findTop100ByTenantIdOrderByCreatedAtDesc(t).stream().anyMatch(x -> target.equals(x.getTarget()) &&
  actionType.equals(x.getActionType()) && "PENDING".equals(x.getStatus()))) return List.of();
  SecurityResponseAction x=new SecurityResponseAction();
  x.setTenantId(t);
  x.setAssessmentId(null);
  x.setActionType(actionType);
  x.setTarget(target);
  x.setPriority(forecastScore>=criticalThreshold?"CRITICAL":forecastScore>=highThreshold?"HIGH":"MEDIUM");
  x.setReason(reason);
  x.setRequestedBy(requestedBy);
  x.setRequiresApproval(true);
  repo.save(x);
  audit(t,x,"SECURITY_CONTROL_LOOP_PROPOSED",Map.of("forecastScore",forecastScore,
  "highRiskProbability",probability,"recommendation",recommendation));
  return List.of(view(x));
 }
 @Transactional(readOnly=true) public List<Map<String,Object>> list(UUID t){
     tenant.set(t);
     return repo.findTop100ByTenantIdOrderByCreatedAtDesc(t).stream().map(this::view).toList();
 }
 private SecurityResponseAction get(UUID t,UUID id){
     return repo.findByIdAndTenantId(id,
     t).orElseThrow(()->new SecurityException("response action not found"));
 }
 private void audit(UUID t,SecurityResponseAction x,String type,Map<String,
 Object> payload){
     try{
         events.enqueue(t,null,type,"SECURITY_RESPONSE",x.getId().toString(),
         mapper.writeValueAsString(payload));
         }
         catch(Exception ignored){
         }
         }
 private String mapType(String t,String title,String control){
     if("RUNTIME_CONTAINMENT".equals(t))return "ISOLATE_AGENT";
     if("MCP_CONTAINMENT".equals(t))return "REVOKE_MCP_TOOL";
     if("CREDENTIAL_ROTATION".equals(t))return
"ROTATE_CREDENTIAL";
     if("POLICY_CHANGE".equals(t))return (title+" "+control).toUpperCase(Locale.ROOT).contains("STEP_UP")?
     "STEP_UP_POLICY":"CREATE_DENY_POLICY";
     return null;
     }
 private String strip(String s,String p){
     return s.startsWith(p)?s.substring(p.length()):s;
 }
 private Map<String,Object> view(SecurityResponseAction x){
     return new LinkedHashMap<>(Map.ofEntries(
        Map.entry("id", x.getId()),
        Map.entry("assessmentId", String.valueOf(x.getAssessmentId())),
        Map.entry("actionType", x.getActionType()),
        Map.entry("target", x.getTarget()),
        Map.entry("priority", x.getPriority()),
        Map.entry("reason", x.getReason()),
        Map.entry("requestedBy", x.getRequestedBy()),
        Map.entry("approvedBy", String.valueOf(x.getApprovedBy())),
        Map.entry("status", x.getStatus()),
        Map.entry("requiresApproval", x.isRequiresApproval()),
        Map.entry("executionResult", read(x.getExecutionResult())),
        Map.entry("createdAt", x.getCreatedAt()),
        Map.entry("approvedAt", String.valueOf(x.getApprovedAt())),
        Map.entry("executedAt", String.valueOf(x.getExecutedAt()))
    ));
 }
 private Map<String,Object> read(String s){
     try{
         return mapper.readValue(s,
         Map.class);
         }
         catch(Exception e){
             return Map.of();
         }
         }
 @SuppressWarnings("unchecked") private List<Map<String,Object>> asList(Object x){
     return x instanceof List<?> l?(List<Map<String,Object>>)(List<?>)l:List.of();
 }
 private String json(Object x){
     try{
         return mapper.writeValueAsString(x);
     }
 catch(Exception e){
     return "{}";
 }
 }
}
