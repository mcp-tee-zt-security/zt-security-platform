package com.zt.security.lifecycle;
import com.zt.security.common.TenantSession;
import com.zt.security.tenant.TenantRepository;
import com.zt.security.runtime.AgentRuntimeEvent;
import com.zt.security.runtime.AgentRuntimeEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service public class CanaryGuardService {
 @org.springframework.beans.factory.annotation.Value("${security.canary.guard-enabled:true}") boolean guardEnabled;
 final PolicyDeploymentRepository deployments;
 final CanaryObservationRepository observations;
 final TenantRepository tenants;
 final AgentRuntimeEventRepository events;
 final PolicyLifecycleService lifecycle;
 final TenantSession tenant;
 CanaryGuardService(PolicyDeploymentRepository d,CanaryObservationRepository o,
 TenantRepository tr,AgentRuntimeEventRepository e,PolicyLifecycleService l,
 TenantSession t){
     deployments=d;
     observations=o;
     tenants=tr;
     events=e;
     lifecycle=l;
     tenant=t;
     }
 @Scheduled(fixedDelayString="${security.canary.guard-delay-ms:60000}") @Transactional public void guard(){
     if(!guardEnabled)return;
     for(var t:tenants.findAll()){
         tenant.set(t.getId());
         for(PolicyDeployment d:
         deployments.findByTenantIdOrderByStartedAtDesc(t.getId())){
             if(!"ACTIVE".equals(d.getStatus())||!"CANARY".equals(d.getStage())||!d.getAutoRollbackEnabled()) continue;
             try{
                 evaluate(d);
             }
             catch(Exception ignored){
             }
             }
             }
             }
 @Transactional public Map<String,Object> evaluateNow(UUID t,UUID id){
     tenant.set(t);
     PolicyDeployment d=deployments.findByIdAndTenantId(id,t).orElseThrow();
     return evaluate(d);
     }
 @Transactional public List<Map<String,Object>> history(UUID t,UUID id){
     tenant.set(t);
     return observations.findTop50ByTenantIdAndDeploymentIdOrderByObservedAtDesc(t,
     id).stream().map(this::view).toList();
     }
 private Map<String,Object> evaluate(PolicyDeployment d){
     UUID t=d.getTenantId();
     tenant.set(t);
     Instant now=Instant.now();
     Instant start=d.getCanaryStartedAt()==
     null?d.getStartedAt():d.getCanaryStartedAt();
     Duration age=Duration.between(start,now);
     Instant baselineFrom=start.minus(Duration.ofHours(Math.min(24,
     Math.max(1,age.toHours()+1))));
     List<AgentRuntimeEvent> all=events.findTop10000ByTenantIdOrderByCreatedAtDesc(t).
     stream().filter(e->!e.getCreatedAt().isBefore(baselineFrom)&&e.getCreatedAt().isBefore(now)).toList();
   List<AgentRuntimeEvent> canary=all.stream().filter(e->e.getCreatedAt().compareTo(start)>=0 && isCanary(e,
   d.getCanaryPercent())).toList();
   List<AgentRuntimeEvent> baseline=all.stream().filter(e->e.getCreatedAt().isBefore(start)).toList();
   Metrics c=metrics(canary), b=metrics(baseline);
   double riskDelta=c.avgRisk-b.avgRisk;
   double decisionDelta=c.denyRate-b.denyRate;
   String status="HEALTHY";
   String reason="Canary within guard thresholds";
   if(canary.size()>=d.getMinCanaryEvents() && (riskDelta>d.getAutoStopThreshold() ||
   decisionDelta>(d.getAutoStopThreshold()/100.0))){
       status="AUTO_ROLLBACK";
       reason="Canary degradation exceeded configured safety threshold";
       lifecycle.abortCanary(t,d.getId(),reason);
       }
   CanaryObservation o=new CanaryObservation();
   o.setTenantId(t);
   o.setDeploymentId(d.getId());
   o.setCanaryEvents(canary.size());
   o.setBaselineEvents(baseline.size());
   o.setCanaryDenyRate(c.denyRate);
   o.setBaselineDenyRate(b.denyRate);
   o.setCanaryHighRiskRate(c.highRiskRate);
   o.setBaselineHighRiskRate(b.highRiskRate);
   o.setCanaryAvgRisk(c.avgRisk);
   o.setBaselineAvgRisk(b.avgRisk);
   o.setRiskDelta(riskDelta);
   o.setDecisionDelta(decisionDelta);
   o.setStatus(status);
   o.setReason(reason);
   observations.save(o);
   return view(o);
   }
 private boolean isCanary(AgentRuntimeEvent e,int pct){
     return e.getRequestId()!=
     null && Math.floorMod(e.getRequestId().hashCode(),
     100)<pct;
     }
 private Metrics metrics(List<AgentRuntimeEvent> x){
     if(x.isEmpty())return new Metrics(0, 0, 0, 0);
     long deny=x.stream().filter(e->"DENY".equalsIgnoreCase(e.getDecision())).count();
     long high=x.stream().filter(e->e.getRiskScore()!=null&&e.getRiskScore()>=70).count();
     double avg=x.stream().filter(e->e.getRiskScore()!=null).mapToDouble(e->e.getRiskScore()).average().orElse(0);
     return new Metrics(x.size(),deny/(double)x.size(),high/(double)x.size(),
     avg);
     }
 private record Metrics(int count,double denyRate,double highRiskRate,
 double avgRisk) {

         }
 private Map<String,Object> view(CanaryObservation o){
     return Map.ofEntries(
        Map.entry("observationId", o.getId()),
        Map.entry("deploymentId", o.getDeploymentId()),
        Map.entry("observedAt", o.getObservedAt()),
        Map.entry("canaryEvents", o.getCanaryEvents()),
        Map.entry("baselineEvents", o.getBaselineEvents()),
        Map.entry("canaryDenyRate", o.getCanaryDenyRate()),
        Map.entry("baselineDenyRate", o.getBaselineDenyRate()),
        Map.entry("canaryHighRiskRate", o.getCanaryHighRiskRate()),
        Map.entry("baselineHighRiskRate", o.getBaselineHighRiskRate()),
        Map.entry("canaryAvgRisk", o.getCanaryAvgRisk()),
        Map.entry("baselineAvgRisk", o.getBaselineAvgRisk()),
        Map.entry("riskDelta", o.getRiskDelta()),
        Map.entry("decisionDelta", o.getDecisionDelta()),
        Map.entry("status", o.getStatus()),
        Map.entry("reason", o.getReason())
    );
     }
}
