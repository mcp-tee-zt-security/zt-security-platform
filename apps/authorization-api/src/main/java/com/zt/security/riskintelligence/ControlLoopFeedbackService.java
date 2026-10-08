package com.zt.security.riskintelligence;

import com.zt.security.common.TenantSession;
import com.zerotrust.security.config.RiskScoringProperties;
import com.zt.security.response.SecurityResponseAction;
import com.zt.security.response.SecurityResponseActionRepository;
import com.zt.security.runtime.AgentRuntimeEvent;
import com.zt.security.runtime.AgentRuntimeEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class ControlLoopFeedbackService {
  private static final int MIN_EVENTS = 2;
  private final SecurityResponseActionRepository responses;
  private final AgentRuntimeEventRepository events;
  private final SecurityControlFeedbackRepository feedback;
  private final SecurityControlEffectivenessProfileRepository profiles;
  private final TenantSession tenant;
  private final RiskScoringProperties riskConfig;

  ControlLoopFeedbackService(SecurityResponseActionRepository r, AgentRuntimeEventRepository e,
                             SecurityControlFeedbackRepository f, SecurityControlEffectivenessProfileRepository p,
                             TenantSession t, RiskScoringProperties riskConfig){
    responses=r;
    events=e;
    feedback=f;
    profiles=p;
    tenant=t;
    this.riskConfig=riskConfig;
  }

  @Transactional(readOnly=true)
  public List<Map<String,Object>> overview(UUID t, int hours){
    tenant.set(t);
    return responses.findTop100ByTenantIdOrderByCreatedAtDesc(t).stream()
      .filter(x -> "EXECUTED".equals(x.getStatus()))
      .map(x -> {
        Optional<SecurityControlFeedback> saved=feedback.findByTenantIdAndResponseId(t,x.getId());
        return saved.map(this::view).orElseGet(() -> verifyInternal(t,x,hours));
      }
      ).toList();
  }

  @Transactional(readOnly=true)
  public Map<String,Object> verify(UUID t, UUID id, int hours){
    tenant.set(t);
    SecurityResponseAction x=responses.findByIdAndTenantId(id,t)
      .orElseThrow(()->new SecurityException("response action not found"));
    return verifyInternal(t,x,hours);
  }

  @Transactional
  public Map<String,Object> verifyAndPersist(UUID t, UUID id, int hours){
    tenant.set(t);
    SecurityResponseAction x=responses.findByIdAndTenantId(id,t)
      .orElseThrow(()->new SecurityException("response action not found"));
    Map<String,Object> result=verifyInternal(t,x,hours);
    SecurityControlFeedback row=feedback.findByTenantIdAndResponseId(t,id).orElseGet(SecurityControlFeedback::new);
    row.setTenantId(t);
    row.setResponseId(id);
    row.setActionType(x.getActionType());
    row.setTarget(x.getTarget());
    row.setVerification(String.valueOf(result.get("verification")));
    row.setBeforeRisk(number(result.get("beforeRisk")));
    row.setAfterRisk(number(result.get("afterRisk")));
    row.setRiskDelta(number(result.get("riskDelta")));
row.setBeforeDenyRate(number(result.get("beforeDenyRate")));
row.setAfterDenyRate(number(result.get("afterDenyRate")));
    row.setDenyRateDelta(number(result.get("denyRateDelta")));
    row.setBeforeEvents((Integer)result.get("beforeEvents"));
    row.setAfterEvents((Integer)result.get("afterEvents"));
    row.setBeforeRiskSamples((Integer)result.get("beforeRiskSamples"));
    row.setAfterRiskSamples((Integer)result.get("afterRiskSamples"));
    row.setObservationHours(hours);
    row.setObservationStartedAt((Instant)result.get("observationStartedAt"));
    row.setObservationCompletedAt((Instant)result.get("observationCompletedAt"));
    row.setEvaluatedAt(Instant.now());
    row.setConfidence(((Number)result.get("confidence")).doubleValue());
    row.setLearningSummary(String.valueOf(result.get("learning")));
    feedback.save(row);
    if (isLearnable(row.getVerification())) updateProfile(row);
    return view(row);
  }

  private Map<String,Object> verifyInternal(UUID t, SecurityResponseAction x, int hours){
    String target=x.getTarget()==null?"":x.getTarget();
    String agent=target.startsWith("agent:")?target.substring(6):null;
    Instant executed=x.getExecutedAt();
    Map<String,Object> out=new LinkedHashMap<>();
    out.put("responseId",x.getId());
    out.put("actionType",x.getActionType());
    out.put("target",target);
    out.put("status",x.getStatus());
    out.put("executedAt",executed);
    out.put("observationHours",hours);

    if(agent==null||executed==null){
      out.put("verification","INSUFFICIENT_DATA");
      out.put("learning","WAIT_FOR_RUNTIME_EVENTS");
      out.put("confidence",0.0);
      return out;
    }

    Instant beforeFrom=executed.minus(Duration.ofHours(hours));
    Instant afterTo=executed.plus(Duration.ofHours(hours));
    Instant now=Instant.now();
List<AgentRuntimeEvent> before=events.findByTenantIdAndAgentExternalIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(t,
    agent,beforeFrom,executed);
List<AgentRuntimeEvent> after=events.findByTenantIdAndAgentExternalIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(t,
    agent,executed,afterTo);

    int beforeRiskSamples=(int)before.stream().filter(e->e.getRiskScore()!=null).count();
    int afterRiskSamples=(int)after.stream().filter(e->e.getRiskScore()!=null).count();
    double br=avgRisk(before), ar=avgRisk(after);
    double bd=denyRate(before), ad=denyRate(after);
    boolean observationComplete=!now.isBefore(afterTo);
    boolean enough=observationComplete && before.size()>=MIN_EVENTS && after.size()>=MIN_EVENTS;
    double riskDelta=ar-br, denyDelta=ad-bd;

    String outcome;
    if(!observationComplete || after.size()<MIN_EVENTS) outcome="PENDING_OBSERVATION";
    else if(beforeRiskSamples<MIN_EVENTS || afterRiskSamples<MIN_EVENTS) outcome="INSUFFICIENT_BASELINE";
    else if(riskDelta<=-5 || denyDelta<=-0.10) outcome="EFFECTIVE";
    else if(riskDelta>=5 || denyDelta>=0.10) outcome="INEFFECTIVE";
    else outcome="NEUTRAL";

    double confidence=confidence(before.size(),after.size(),beforeRiskSamples,afterRiskSamples,observationComplete);
    out.put("beforeEvents",before.size());
    out.put("afterEvents",after.size());
    out.put("beforeRiskSamples",beforeRiskSamples);
    out.put("afterRiskSamples",afterRiskSamples);
    out.put("beforeRisk",beforeRiskSamples==0?null:round(br));
    out.put("afterRisk",afterRiskSamples==0?null:round(ar));
    out.put("riskDelta",beforeRiskSamples==0||afterRiskSamples==0?null:round(riskDelta));
    out.put("beforeDenyRate",round(bd));
    out.put("afterDenyRate",round(ad));
    out.put("denyRateDelta",round(denyDelta));
    out.put("verification",outcome);
    out.put("confidence",round(confidence));
    out.put("observationStartedAt",executed);
    out.put("observationCompletedAt",afterTo);
    out.put("learning",learning(outcome,x.getActionType()));
    out.put("explainability","Outcome is inferred from bounded pre/post runtime" +
" evidence; it is not a causal guarantee.");
    return out;
  }

  @Transactional(readOnly=true)
  public List<Map<String,Object>> effectiveness(UUID t){
    tenant.set(t);
    return profiles.findTop100ByTenantIdOrderByEffectivenessScoreDescUpdatedAtDesc(t).stream()
      .map(this::profileView).toList();
  }

  @Transactional(readOnly=true)
  public List<Map<String,Object>> recommendations(UUID t, String target){
    tenant.set(t);
    return profiles.findTop100ByTenantIdOrderByEffectivenessScoreDescUpdatedAtDesc(t).stream()
      .filter(p -> target == null || target.isBlank() || p.getTarget().equals(target))
      .filter(p -> p.getEvidenceCount() > 0)
      .map(this::recommendationView).toList();
  }

  private void updateProfile(SecurityControlFeedback x){
    SecurityControlEffectivenessProfile p=profiles.findByTenantIdAndActionTypeAndTarget(x.getTenantId(),
    x.getActionType(),x.getTarget())
      .orElseGet(SecurityControlEffectivenessProfile::new);
    p.setTenantId(x.getTenantId());
    p.setActionType(x.getActionType());
    p.setTarget(x.getTarget());
    p.setEvidenceCount(p.getEvidenceCount()+1);
    switch(x.getVerification()){
      case "EFFECTIVE" -> p.setEffectiveCount(p.getEffectiveCount()+1);
      case "INEFFECTIVE" -> p.setIneffectiveCount(p.getIneffectiveCount()+1);
      case "NEUTRAL" -> p.setNeutralCount(p.getNeutralCount()+1);
      default -> {
          return;
      }
    }
    int n=p.getEvidenceCount();
    double raw=(p.getEffectiveCount()+riskConfig.getControlNeutralWeight()*p.getNeutralCount())/Math.max(1.0,n);
    double sample=Math.min(1.0,n/riskConfig.getControlSampleDivisor());
    p.setEffectivenessScore(round(raw*100.0));
    p.setConfidence(round(Math.min(riskConfig.getControlConfidenceCap(),riskConfig.getControlConfidenceBase()+riskConfig.getControlConfidenceSampleWeight()*sample+riskConfig.getControlConfidenceEvidenceWeight()*x.getConfidence())));
    p.setLastVerification(x.getEvaluatedAt());
    p.setUpdatedAt(Instant.now());
    profiles.save(p);
  }

  private boolean isLearnable(String verification){
    return "EFFECTIVE".equals(verification)||"INEFFECTIVE".equals(verification)||"NEUTRAL".equals(verification);
  }

  private Map<String,Object> profileView(SecurityControlEffectivenessProfile p){
    Map<String,Object> out=new LinkedHashMap<>();
    out.put("profileId",p.getId());
    out.put("actionType",p.getActionType());
    out.put("target",p.getTarget());
    out.put("evidenceCount",p.getEvidenceCount());
    out.put("effectiveCount",p.getEffectiveCount());
    out.put("ineffectiveCount",p.getIneffectiveCount());
    out.put("neutralCount",p.getNeutralCount());
    out.put("effectivenessScore",p.getEffectivenessScore());
    out.put("confidence",p.getConfidence());
    out.put("lastVerification",p.getLastVerification());
    out.put("mode","ADVISORY");
    return out;
  }

  private Map<String,Object> recommendationView(SecurityControlEffectivenessProfile p){
    Map<String,Object> out=profileView(p);
    out.put("recommendation",p.getEffectivenessScore() >= 80 && p.getConfidence() >=
    0.70 ? "PREFERRED" : p.getEffectivenessScore() >= 60 ? "CONSIDER" : "REVIEW");
    out.put("reason",p.getEffectivenessScore() >= 80 ? "Strong historical verification evidence for this target." :
    "Evidence is not strong enough to prefer this control automatically.");
    out.put("automationBoundary","Advisory only; production policy is never changed automatically.");
    return out;
  }

  private Map<String,Object> view(SecurityControlFeedback x){
    Map<String,Object> out=new LinkedHashMap<>();
    out.put("feedbackId",x.getId());
    out.put("responseId",x.getResponseId());
    out.put("actionType",x.getActionType());
    out.put("target",x.getTarget());
    out.put("verification",x.getVerification());
    out.put("beforeEvents",x.getBeforeEvents());
    out.put("afterEvents",x.getAfterEvents());
    out.put("beforeRiskSamples",x.getBeforeRiskSamples());
    out.put("afterRiskSamples",x.getAfterRiskSamples());
    out.put("beforeRisk",x.getBeforeRisk());
    out.put("afterRisk",x.getAfterRisk());
    out.put("riskDelta",x.getRiskDelta());
    out.put("beforeDenyRate",x.getBeforeDenyRate());
    out.put("afterDenyRate",
    x.getAfterDenyRate());
    out.put("denyRateDelta",x.getDenyRateDelta());
    out.put("observationHours",x.getObservationHours());
    out.put("observationStartedAt",x.getObservationStartedAt());
    out.put("observationCompletedAt",x.getObservationCompletedAt());
    out.put("evaluatedAt",x.getEvaluatedAt());
    out.put("confidence",x.getConfidence());
    out.put("learning",x.getLearningSummary());
    out.put("explainability","Outcome is inferred from bounded pre/post runtime" +
" evidence; it is not a causal guarantee.");
    return out;
  }

  private String learning(String outcome,String action){
    return switch(outcome){
      case "EFFECTIVE" -> "Increase evidence confidence for preventive controls of type "+action+
      "; do not auto-change enforcement.";
      case "INEFFECTIVE" -> "Escalate for human review of policy scope, forecast drivers and response coverage.";
      case "NEUTRAL" -> "Keep control confidence unchanged and continue observation.";
      case "INSUFFICIENT_BASELINE" -> "Collect more risk-scored runtime evidence before learning from this control.";
      default -> "Collect more post-execution runtime evidence.";
    }
    ;
  }

  private double avgRisk(List<AgentRuntimeEvent> r){
      return r.stream().filter(e->
      e.getRiskScore()!=null).mapToDouble(AgentRuntimeEvent::getRiskScore).average().orElse(0);
  }
  private double denyRate(List<AgentRuntimeEvent> r){
      return r.isEmpty()?0:r.stream().filter(e->
      "DENY".equalsIgnoreCase(e.getDecision())).count()/(double)r.size();
  }
  private double confidence(int before,int after,int beforeRisk,int afterRisk,boolean complete){
    if(!complete) return 0.25;
    double sample=Math.min(1.0,(before+after)/riskConfig.getControlConfidenceSampleDivisor());
    double risk=Math.min(1.0,(beforeRisk+afterRisk)/riskConfig.getControlConfidenceRiskDivisor());
    return Math.min(riskConfig.getControlConfidenceCap(),riskConfig.getControlConfidenceBase()+riskConfig.getControlConfidenceSampleWeight()*sample+riskConfig.getControlConfidenceEvidenceWeight()*risk);
  }
  private Double number(Object x){
      return x==null?null:((Number)x).doubleValue();
  }
  private double round(double x){
      return Math.round(x*100.0)/100.0;
  }
}
