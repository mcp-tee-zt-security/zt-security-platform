package com.zt.security.behavior;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.action.EvaluateModels;
import com.zt.security.risk.RiskEngine;
import com.zt.security.common.TenantSession;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service public class AgentBehaviorService {
 public record Result(boolean anomalous,String decision,String reason,
 double score,List<String> signals,Map<String,Object> evidence){
     static Result clean(){
         return new Result(false,"","",0,List.of(),Map.of());
         }
         }
 final ProfileRepo profiles;
 final EventRepo events;
 final AnomalyRepo anomalies;
 final AdaptiveDecisionRepository adaptive;
 final ObjectMapper mapper;
 final TenantSession tenant;
 AgentBehaviorService(ProfileRepo p,EventRepo e,AnomalyRepo a,AdaptiveDecisionRepository ad,
 ObjectMapper m,TenantSession t){
     profiles=p;
     events=e;
     anomalies=a;
     adaptive=ad;
     mapper=m;
     tenant=t;
     }
 @Transactional public Result before(UUID t,EvaluateModels.EvaluateRequest r,RiskEngine.Result risk,UUID requestId){
  if(!"AI_AGENT".equals(r.principal().type()))return Result.clean();
  tenant.set(t);
  Optional<AgentBehaviorProfile> op=profiles.findByTenantIdAndAgentExternalId(t,
  r.principal().id());
  if(op.isEmpty()||!op.get().isEnabled())return Result.clean();
AgentBehaviorProfile p=op.get();
Instant now=Instant.now();
Instant window=now.minusSeconds(p.getBaselineWindowDays()*86400L);
  List<AgentBehaviorEvent> mine=events.findByTenantIdAndAgentExternalIdAndCreatedAtAfterOrderByCreatedAtAsc(t,
  r.principal().id(),window);
  List<String> sig=new ArrayList<>();
  double baselineScore=0,
  sequenceScore=0, peerScore=0;
  Map<String,Object> ev=new LinkedHashMap<>();
  long recent=events.countByTenantIdAndAgentExternalIdAndCreatedAtAfter(t,
  r.principal().id(),now.minusSeconds(60));
  if(p.getMaxActionsPerMinute()>0 && recent>=p.getMaxActionsPerMinute()){
      sig.add("ACTION_RATE");
      baselineScore+=25;
      }
  double amount=num(r.context()==null?null:r.context().get("amount"));
  Double mean=meanAmount(mine), sd=stdAmount(mine,mean);
  if(amount>=0 && mean!=null && sd!=null && sd>0){
      double z=(amount-mean)/sd;
      if(z>=2){
          sig.add("BASELINE_AMOUNT_DEVIATION");
          baselineScore+=Math.min(30,10+z*5);
          ev.put("amount_z",round(z));
          }
          }
  if(amount>=0 && p.getMaxAmount()!=null && amount>p.getMaxAmount()){
      sig.add("AMOUNT_THRESHOLD");
      baselineScore+=30;
  }
  if(risk.score()>p.getMaxRiskScore()){
      sig.add("RISK_THRESHOLD");
      baselineScore+=20;
  }
  if(!allowed(p.getAllowedActions(),r.action().name()) && !empty(p.getAllowedActions())){
      sig.add("ACTION_NOT_ALLOWED");
      baselineScore+=35;
      }
  if(!allowed(p.getAllowedResourceTypes(),r.resource().type()) && !empty(p.getAllowedResourceTypes())){
      sig.add("RESOURCE_NOT_ALLOWED");
      baselineScore+=35;
      }
  sequenceScore=sequenceRisk(mine,r.action().name(),p.getDenyBurstThreshold());
  if(sequenceScore>0){
      sig.add("SEQUENCE_ANOMALY");
  }
  List<AgentBehaviorEvent> peerEvents=peerEvents(t,p,window);
  peerScore=peerRisk(peerEvents,
  r,mean);
  if(peerScore>=15)sig.add("PEER_DEVIATION");
  double score=Math.min(100,baselineScore+sequenceScore+peerScore);
  String adaptiveDecision=
  score>=p.getAdaptiveDenyScore()?"DENY":score>=p.getAdaptiveStepUpScore()?"STEP_UP":
  "DEFER_TO_POLICY";
  ev.put("baseline_mean_amount",mean);
  ev.put("baseline_event_count",mine.size());
  ev.put("peer_event_count",peerEvents.size());
  ev.put("sequence_score",
  round(sequenceScore));
  ev.put("peer_score",round(peerScore));
  ev.put("adaptive_decision",
  adaptiveDecision);
  if(score>0){
      AdaptiveDecision d=new AdaptiveDecision();
      d.setId(UUID.randomUUID());
      d.setTenantId(t);
      d.setAgentExternalId(r.principal().id());
      d.setRequestId(requestId);
      d.setPeerGroup(p.getPeerGroup());
      d.setBehaviorScore(score);
      d.setBaselineScore(Math.min(100,
      baselineScore));
      d.setSequenceScore(sequenceScore);
      d.setPeerScore(peerScore);
      d.setDecision(adaptiveDecision);
      d.setSignals(toJson(sig));
      d.setEvidence(toJson(ev));
      adaptive.save(d);
      }
  if(score<40)return new Result(false,"DEFER_TO_POLICY","Behavior score below adaptive threshold",score,sig,ev);
  String reason="Adaptive agent behavior: "+String.join(", ",sig);
  String sev=score>=80?"HIGH":score>=60?"MEDIUM":"LOW";
  if(!sig.isEmpty()){
      AgentAnomaly a=new AgentAnomaly();
      a.setId(UUID.randomUUID());
      a.setTenantId(t);
      a.setAgentExternalId(r.principal().id());
      a.setRequestId(requestId);
      a.setAnomalyType(sig.get(0));
      a.setSeverity(sev);
      a.setScore(score);
      a.setReason(reason);
      a.setEvidence(toJson(ev));
      anomalies.save(a);
      }
  return new Result(true,adaptiveDecision,reason,score,sig,ev);
 }
 @Transactional public void after(UUID t,EvaluateModels.EvaluateRequest r,
 UUID requestId,String decision,double risk){
     if(!"AI_AGENT".equals(r.principal().type()))return;
     tenant.set(t);
     AgentBehaviorEvent e=new AgentBehaviorEvent();
     e.setId(UUID.randomUUID());
     e.setTenantId(t);
     e.setAgentExternalId(r.principal().id());
     e.setRequestId(requestId);
     Object toolId=r.context()==null?null:r.context().get("tool_id");
     if(toolId!=null) try{
         e.setToolId(UUID.fromString(String.valueOf(toolId)));
         }
         catch(Exception ignored){
     }
     e.setAction(r.action().name());
     e.setResourceType(r.resource().type());
 e.setDecision(decision);
 e.setRiskScore(risk);
 e.setAmount(number(r.context()==null?null:r.context().get("amount")));
 events.save(e);
 }
 @Transactional public AgentBehaviorProfile save(UUID t,AgentBehaviorProfile p){
     tenant.set(t);
     p.setId(p.getId()==null?UUID.randomUUID():p.getId());
     p.setTenantId(t);
     return profiles.save(p);
     }
     public List<AgentAnomaly> anomalies(UUID t,String agent){
     tenant.set(t);
     return agent==null?anomalies.findByTenantIdOrderByCreatedAtDesc(t):
     anomalies.findByTenantIdAndAgentExternalIdOrderByCreatedAtDesc(t,
     agent);
     }
     public List<AgentBehaviorProfile> profiles(UUID t){
         tenant.set(t);
     return profiles.findAll();
     }
     public List<AdaptiveDecision> decisions(UUID t,
 String agent){
     tenant.set(t);
     return adaptive.findTop50ByTenantIdAndAgentExternalIdOrderByCreatedAtDesc(t,
     agent);
     }
 List<AgentBehaviorEvent> peerEvents(UUID t,AgentBehaviorProfile p,Instant w){
     List<AgentBehaviorEvent> all=events.findByTenantIdAndCreatedAtAfterOrderByCreatedAtAsc(t,
     w);
     Set<String> agents=new HashSet<>();
     for(AgentBehaviorProfile x:profiles.findAll()){
         if(p.getPeerGroup().equals(x.getPeerGroup()))agents.add(x.getAgentExternalId());
     }
     return all.stream().filter(e->agents.contains(e.getAgentExternalId())).toList();
 }
 double sequenceRisk(List<AgentBehaviorEvent> mine,String action,int denyBurst){
     if(mine.isEmpty())return 0;
     List<AgentBehaviorEvent> recent=mine.size()>8?mine.subList(mine.size()-8,
     mine.size()):mine;
     long denies=recent.stream().filter(e->"DENY".equals(e.getDecision())).count();
     boolean newAction=recent.stream().noneMatch(e->action.equals(e.getAction()));
     double s=0;
     if(denies>=Math.max(2,denyBurst-2))s+=25;
     if(newAction&&recent.size()>=5)s+=15;
     if(recent.size()>=6&&recent.stream().map(AgentBehaviorEvent::getAction).distinct().count()>=5)s+=15;
     return Math.min(40,s);
     }
 double peerRisk(List<AgentBehaviorEvent> peers,EvaluateModels.EvaluateRequest r,
 Double mean){
     if(peers.size()<5)return 0;
     double s=0;
     long actionPeers=peers.stream().filter(e->
     r.action().name().equals(e.getAction())).count();
     if(actionPeers==0)s+=15;
     double peerMean=peers.stream().map(AgentBehaviorEvent::getAmount).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);
     double amount=num(r.context()==null?null:r.context().get("amount"));
     if(amount>0&&
     peerMean>0&&amount>peerMean*3)s+=20;
     return Math.min(35,s);
     }
 Double meanAmount(List<AgentBehaviorEvent> x){
     double[] a=x.stream().map(AgentBehaviorEvent::getAmount).filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
     return a.length==0?null:Arrays.stream(a).average().orElse(0);
     }
 Double stdAmount(List<AgentBehaviorEvent> x,Double mean){
     if(mean==null)return null;
     double[] a=x.stream().map(AgentBehaviorEvent::getAmount).filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
     if(a.length<3)return null;
     return Math.sqrt(Arrays.stream(a).map(v->(v-mean)*(v-mean)).average().orElse(0));
 }
 boolean empty(String s){
     return s==null||s.equals("[]")||s.isBlank();
 }
 boolean allowed(String json,String val){
     try{
         return mapper.readValue(json,
         new TypeReference<List<String>>(){
         }
         ).contains(val);
         }
         catch(Exception e){
         return false;
         }
         }
         double num(Object o){
             return o instanceof Number n?n.doubleValue():
         o==null?0:tryDouble(String.valueOf(o));
 }
 double tryDouble(String s){
     try{
         return Double.parseDouble(s);
     }
 catch(Exception e){
     return 0;
     }
     }
     Double number(Object o){
         return o instanceof Number n?n.doubleValue():
     o==null?null:tryD(String.valueOf(o));
 }
 Double tryD(String s){
     try{
         return Double.valueOf(s);
     }
 catch(Exception e){
     return null;
     }
     }
     double round(double x){
         return Math.round(x*100.0)/100.0;
 }
 String toJson(Object x){
     try{
         return mapper.writeValueAsString(x);
     }
 catch(Exception e){
     return "{}";
     }
     }
}
