package com.zt.security.action;
import com.zt.security.audit.AuditService;
import com.zt.security.approval.Approval;
import com.zt.security.approval.ApprovalRepository;
import com.zt.security.common.TenantSession;
import com.zt.security.policy.*;
import com.zt.security.risk.RiskEngine;
import com.zt.security.behavior.AgentBehaviorService;
import com.zt.security.identity.Identity;
import com.zt.security.identity.IdentityRepository;
import com.zt.security.decision.ContinuousSecurityDecisionService;
import com.zt.security.event.SecurityEventService;
import com.zt.security.runtime.RuntimeThreatDetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service public class ActionEvaluationService {
    final PolicyService policies;
    final PolicyEvaluator evaluator;
    final RiskEngine risk;
    final AuditService audit;
    final TenantSession tenantSession;
    final ApprovalRepository approvals;
    final com.zt.security.agent.AgentTaskRepository tasks;
    final IdentityRepository identities;
    final com.zt.security.agent.TaskToolRepository taskTools;
    final com.zt.security.siem.SiemService siem;
    final AgentBehaviorService behavior;
    final ContinuousSecurityDecisionService continuous;
    final SecurityEventService securityEvents;
    final RuntimeThreatDetectionService threats;
    final ObjectMapper mapper;
    ActionEvaluationService(PolicyService p,PolicyEvaluator e,
    RiskEngine r,AuditService a,TenantSession ts,ApprovalRepository ar,com.zt.security.agent.AgentTaskRepository tr,
    IdentityRepository ir,com.zt.security.agent.TaskToolRepository tl,com.zt.security.siem.SiemService ss,
    AgentBehaviorService bs,ContinuousSecurityDecisionService cs,SecurityEventService se,
    RuntimeThreatDetectionService th,ObjectMapper om){
        policies=p;
        evaluator=e;
        risk=r;
        audit=a;
        tenantSession=ts;
        approvals=ar;
        tasks=tr;
        identities=ir;
        taskTools=tl;
        siem=ss;
        behavior=bs;
        continuous=cs;
        securityEvents=se;
        threats=th;
        mapper=om;
    }
 @Transactional public EvaluateModels.EvaluateResponse evaluate(UUID tenant,
 EvaluateModels.EvaluateRequest req){
     return evaluate(tenant,req,true);
 }
 @Transactional public EvaluateModels.EvaluateResponse evaluate(UUID tenant,
 EvaluateModels.EvaluateRequest req,boolean createApproval){
     return evaluateScoped(tenant,req,createApproval,null,false,false);
 }
 @Transactional public EvaluateModels.EvaluateResponse evaluate(UUID tenant,UUID workspace,
 EvaluateModels.EvaluateRequest req,boolean createApproval){
     return evaluateScoped(tenant,req,createApproval,workspace,true,false);
 }
 /** Built-in allowance covers a public conversation only; external access stays separately governed. */
 @Transactional public EvaluateModels.EvaluateResponse evaluateConversation(UUID tenant,UUID workspace,EvaluateModels.EvaluateRequest req){
     if(!"chat.respond".equals(req.action().name())||!"public_answer".equals(req.resource().type()))throw new IllegalArgumentException("Public conversation evaluation only");
     return evaluateScoped(tenant,req,false,workspace,true,true);
 }
 private EvaluateModels.EvaluateResponse evaluateScoped(UUID tenant,EvaluateModels.EvaluateRequest req,
 boolean createApproval,UUID workspace,boolean scoped,boolean publicConversation){
     tenantSession.set(tenant);
     long st=System.nanoTime();
     UUID rid=UUID.randomUUID();
     enforceAgentBoundary(tenant,req);
     var rr=risk.score(tenant,
     risk.fingerprint(req.context()), req.context());
     var bd=behavior.before(tenant,
     req,rr,rid);
     var td=threats.detect(req);
     EvaluateModels.EvaluateRequest policyReq=withBehaviorContext(req,
     bd,td);
     PolicyEvaluator.Result d;
     boolean policyEvaluated=false;
     if(td.blocked()) d=new PolicyEvaluator.Result("DENY",
     String.join("; ",td.signals().stream().map(RuntimeThreatDetectionService.Signal::reason).toList()),
     List.of());
     else if("DENY".equals(bd.decision())) d=new PolicyEvaluator.Result("DENY",
     bd.reason(),List.of());
     else if("STEP_UP".equals(bd.decision()) || "STEP_UP".equals(td.decision())) d=
     new PolicyEvaluator.Result("STEP_UP",
     td.signals().isEmpty()?bd.reason():td.signals().get(0).reason(),List.of());
     else {
         policyEvaluated=true;
         var applicable=policies.forEvaluation(tenant,rid);
         if(scoped)applicable=applicable.stream().filter(p->p.getWorkspaceId()==null||
             p.getWorkspaceId().equals(workspace)).toList();
         if(publicConversation){
             var baseline=new Policy();baseline.setId(UUID.nameUUIDFromBytes("zt-builtin-public-conversation-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8)));baseline.setName("builtin_public_conversation");baseline.setVersion(1);baseline.setEffect("allow");baseline.setWorkspaceId(workspace);
             baseline.setPolicyText("policy \"builtin_public_conversation\" { effect allow principal.type == \"AI_AGENT\" action == \"chat.respond\" resource.type == \"public_answer\" }");
             applicable=new ArrayList<>(applicable);applicable.add(baseline);
         }
         d=evaluator.evaluate(applicable,policyReq);
     }
     var policyResult=d;
     var cd=continuous.decide(tenant,rid,req,d,rr,bd);
     d=new PolicyEvaluator.Result(cd.decision(),
     cd.reason(),d.matched());
     var finalRisk=new RiskEngine.Result(cd.compositeScore(),
     cd.compositeScore()>=70?"HIGH":cd.compositeScore()>=40?"MEDIUM":"LOW", rr.evidence());
     var al=audit.record(tenant,rid,req,d,finalRisk);
     behavior.after(tenant,
     req,rid,d.decision(),finalRisk.score());
     siem.publish(al);
     emitRuntimeEvents(tenant,
     req,rid,d,finalRisk,td);
     if(createApproval && "STEP_UP".equals(d.decision())){
         Approval ap=new Approval();
         ap.setId(UUID.randomUUID());
         ap.setTenantId(tenant);
         ap.setRequestId(rid);
         ap.setApproverId("security-approver");
         ap.setReason(d.reason());
         approvals.save(ap);
     }
     return new EvaluateModels.EvaluateResponse(rid,d.decision(),d.reason(),
 d.matched(),new EvaluateModels.Risk(finalRisk.score(),finalRisk.level()),
 new EvaluateModels.Audit(al.getId()),(System.nanoTime()-st)/1_000_000,Map.of(
     "delegation",Map.of("taskVerified","AI_AGENT".equals(req.principal().type())&&req.context()!=null&&req.context().containsKey("task_id"),"toolVerified","AI_AGENT".equals(req.principal().type())&&req.context()!=null&&req.context().containsKey("task_id")&&req.context().containsKey("tool_id"),"taskId",String.valueOf((req.context()==null?Map.<String,Object>of():req.context()).getOrDefault("task_id","")),"toolId",String.valueOf((req.context()==null?Map.<String,Object>of():req.context()).getOrDefault("tool_id",""))),
     "baseRisk",rr,"behavior",bd,"threat",td,"policy",Map.of("evaluated",policyEvaluated,"decision",policyResult.decision(),"reason",policyResult.reason(),"matched",d.matched()),"scope",Map.of("tenantId",tenant.toString(),"workspaceId",workspace==null?"tenant-wide":workspace.toString())));
 }
 private EvaluateModels.EvaluateRequest withBehaviorContext(EvaluateModels.EvaluateRequest req,
 AgentBehaviorService.Result bd, RuntimeThreatDetectionService.Result td){
  Map<String,Object> c=new LinkedHashMap<>();
  if(req.context()!=null)c.putAll(req.context());
  var rr=risk.score(tenantSession.get(), risk.fingerprint(req.context()), req.context());
  c.put("behavior_score",bd.score());
  c.put("behavior_anomalous",bd.anomalous());
  c.put("behavior_signals",String.join(",",bd.signals()));
  c.put("behavior_adaptive_decision",bd.decision());
  c.put("risk.score",rr.score());
  c.put("risk.level",rr.level());
  c.put("agent.behavior",bd.anomalous()?"ANOMALOUS":"NORMAL");
  c.put("runtime.threat.score",
  td.score());
  c.put("runtime.threat.decision",td.decision());
  c.put("runtime.threat.signals",
  td.signals().stream().map(RuntimeThreatDetectionService.Signal::type).toList());
  return new EvaluateModels.EvaluateRequest(req.principal(),req.action(),req.resource(),c);
}
 private void emitRuntimeEvents(UUID tenant,EvaluateModels.EvaluateRequest req,
 UUID rid,PolicyEvaluator.Result d,RiskEngine.Result finalRisk,RuntimeThreatDetectionService.Result td){
  if(!"AI_AGENT".equalsIgnoreCase(req.principal().type())) return;
  try {
      Map<String,Object> p=new LinkedHashMap<>();
      p.put("requestId",
      rid);
      p.put("agent",req.principal().id());
      p.put("action",req.action().name());
      p.put("resource",req.resource().type()+":"+req.resource().id());
      p.put("decision",
      d.decision());
      p.put("riskScore",finalRisk.score());
      p.put("threatScore",
      td.score());
      p.put("threatSignals",td.signals());
      p.put("context",req.context());
      String type=td.signals().isEmpty()?"AI_AGENT_ACTION":"AI_AGENT_THREAT";
      securityEvents.enqueue(tenant, null, type, "AI_AGENT", req.principal().id(),
      mapper.writeValueAsString(p));
      }
      catch(Exception ignored) {
      }
 }

 private void enforceAgentBoundary(UUID tenant,EvaluateModels.EvaluateRequest req){
     if(!"AI_AGENT".equals(req.principal().type())||req.context()==null)return;
     Object taskId=req.context().get("task_id"),toolId=req.context().get("tool_id");
     if(taskId==null)return;
     var task=tasks.findByTenantIdAndExternalTaskId(tenant,
String.valueOf(taskId)).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("unknown agent task"));
     var owner=tasksIdentity(tenant, task.getAgentIdentityId());
     if(!"ACTIVE".equals(task.getStatus())||owner!=null&&!"ACTIVE".equals(owner.getStatus()))throw new org.springframework.security.access.AccessDeniedException("Agent task or identity is inactive");
     if(owner==null ||
     !(owner.getId().toString().equals(req.principal().id()) || String.valueOf(owner.getExternalId()).equals(req.
principal().id())))throw new org.springframework.security.access.AccessDeniedException("agent task owner mismatch");
     if(task.getExpiresAt()!=null&&task.getExpiresAt().isBefore(java.time.Instant.now()))throw new org.
     springframework.security.access.AccessDeniedException("agent task expired");
     if(toolId!=null){
         boolean allowed=taskTools.toolIds(task.getId()).stream().anyMatch(x->
         x.toString().equalsIgnoreCase(String.valueOf(toolId)));
         if(!allowed)throw new org.springframework.security.access.AccessDeniedException("tool not delegated to task");
     }
     }
 private Identity tasksIdentity(UUID tenant, UUID id){
     return identities.findByTenantId(tenant).stream().
     filter(x->x.getId().equals(id)).findFirst().orElse(null);
 }
}
