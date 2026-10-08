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
     if(td.blocked()) d=new PolicyEvaluator.Result("DENY",
     String.join("; ",td.signals().stream().map(RuntimeThreatDetectionService.Signal::reason).toList()),
     List.of());
     else if("DENY".equals(bd.decision())) d=new PolicyEvaluator.Result("DENY",
     bd.reason(),List.of());
     else if("STEP_UP".equals(bd.decision()) || "STEP_UP".equals(td.decision())) d=
     new PolicyEvaluator.Result("STEP_UP",
     td.signals().isEmpty()?bd.reason():td.signals().get(0).reason(),List.of());
     else d=evaluator.evaluate(policies.forEvaluation(tenant,rid),policyReq);
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
     if("STEP_UP".equals(d.decision())){
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
 new EvaluateModels.Audit(al.getId()),(System.nanoTime()-st)/1_000_000);
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
