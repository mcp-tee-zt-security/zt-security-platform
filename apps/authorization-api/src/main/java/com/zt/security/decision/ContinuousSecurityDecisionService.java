package com.zt.security.decision;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.action.EvaluateModels;
import com.zt.security.attack.AttackPathService;
import com.zt.security.behavior.AgentBehaviorService;
import com.zt.security.common.Jsons;
import com.zerotrust.security.config.RiskScoringProperties;
import com.zt.security.policy.PolicyEvaluator;
import com.zt.security.risk.RiskEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service public class ContinuousSecurityDecisionService {
    @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.critical-threshold:85.0}")
    private double criticalThreshold;
    @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.high-threshold:65.0}")
    private double highThreshold;
    @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.elevated-threshold:40.0}")
    private double elevatedThreshold;

 private final SecurityAssetRepository assets;
 private final SecurityDecisionRepository decisions;
 private final SecurityDecisionEvidenceRepository evidence;
 private final AttackPathService attackPaths;
 private final ObjectMapper mapper;
 private final RiskScoringProperties riskConfig;
 ContinuousSecurityDecisionService(SecurityAssetRepository a,SecurityDecisionRepository d,
 SecurityDecisionEvidenceRepository ev,AttackPathService ap,ObjectMapper m,RiskScoringProperties riskConfig){
     assets=a;
     decisions=d;
     evidence=ev;
     attackPaths=ap;
     mapper=m;
     this.riskConfig=riskConfig;
     }
 public Result decide(UUID tenant,UUID requestId,EvaluateModels.EvaluateRequest req,
 PolicyEvaluator.Result policy,RiskEngine.Result base,AgentBehaviorService.Result behavior){
   double asset=assets.findByTenantIdAndResourceTypeAndResourceId(tenant,
   req.resource().type(),req.resource().id()).map(x->(double)x.getCriticality()).orElse(defaultCriticality(req.
   resource().type()));
   double behaviorScore=behavior==null?0:behavior.score();
   double attack=0;
   if("AI_AGENT".equals(req.principal().type())) {
       try {
           Map<String,Object> x=attackPaths.assess(tenant,
           req.principal().id(),60,8,false);
           attack=num(x.get("riskScore"));
           }
           catch(Exception ignored){
       }
       }
   double policyRisk="DENY".equals(policy.decision())?100:"STEP_UP".equals(policy.decision())?70:10;
   double composite=Math.min(100,base.score()*riskConfig.getDecisionBaseRiskWeight()+behaviorScore*riskConfig.getDecisionBehaviorWeight()+asset*riskConfig.getDecisionAssetWeight()+attack*riskConfig.getDecisionAttackWeight()+policyRisk*riskConfig.getDecisionPolicyWeight());
   String decision=policy.decision();
   String reason=policy.reason();
   if("DENY".equals(decision)) reason=policy.reason()+"; continuous security score "+round(composite);
   else if(composite>=criticalThreshold){
       decision="DENY";
       reason="Continuous security decision: composite risk "+
       round(composite)+" exceeded deny threshold";
   }
   else if(composite>=highThreshold){
       decision="STEP_UP";
       reason="Continuous security decision: composite risk "+
       round(composite)+" requires human step-up";
   }
   else if("ALLOW".equals(decision)) reason="ALLOW; continuous risk "+round(composite);
   SecurityDecision row=new SecurityDecision();
   row.setTenantId(tenant);
   row.setRequestId(requestId);
   row.setPrincipalId(req.principal().id());
   row.setPrincipalType(req.principal().type());
   row.setAction(req.action().name());
   row.setResourceType(req.resource().type());
   row.setResourceId(req.resource().id());
   row.setBaseRisk(base.score());
   row.setBehaviorRisk(behaviorScore);
   row.setAssetCriticality(asset);
   row.setAttackPathRisk(attack);
   row.setPolicyRisk(policyRisk);
   row.setCompositeScore(composite);
   row.setDecision(decision);
   row.setReason(reason);
   row.setSignals(Jsons.string(mapper,Map.of("baseRisk",base.score(),"behaviorRisk",
   behaviorScore,"assetCriticality",asset,"attackPathRisk",attack,"policyDecision",
   policy.decision())));
   decisions.save(row);
   saveEvidence(tenant,row,"BASE_RISK","Base transaction risk",base.score(),.25);
   saveEvidence(tenant,row,"BEHAVIOR","Agent behavior risk",behaviorScore,.30);
   saveEvidence(tenant,row,"ASSET_CRITICALITY","Protected resource criticality",asset,.20);
   saveEvidence(tenant,row,"ATTACK_PATH","Reachability / blast-radius risk",attack,.15);
   saveEvidence(tenant,row,"POLICY","Policy decision risk",policyRisk,.10);
   return new Result(decision,reason,composite,asset,attack);
 }
 public List<SecurityDecision> recent(UUID tenant){
     return decisions.findTop100ByTenantIdOrderByCreatedAtDesc(tenant);
 }
 public List<SecurityAsset> assets(UUID tenant){
     return assets.findByTenantIdOrderByCriticalityDesc(tenant);
 }
public SecurityAsset upsertAsset(UUID tenant,SecurityAsset in){
    var x=assets.findByTenantIdAndResourceTypeAndResourceId(tenant,
     in.getResourceType(),in.getResourceId()).orElseGet(SecurityAsset::new);
     x.setTenantId(tenant);
     x.setResourceType(in.getResourceType());
     x.setResourceId(in.getResourceId());
x.setCriticality(Math.max(0,Math.min(100,in.getCriticality())));
x.setDataClassification(in.getDataClassification());
     x.setOwner(in.getOwner());
     x.setMetadata(in.getMetadata());
     return assets.save(x);
 }

 private void saveEvidence(UUID tenant,SecurityDecision row,String signal,
 String label,double score,double weight){
     SecurityDecisionEvidence e=new SecurityDecisionEvidence();
     e.setTenantId(tenant);
     e.setDecisionId(row.getId());
     e.setCategory("CONTINUOUS_DECISION");
     e.setSignal(signal);
     e.setScore(score);
     e.setWeight(weight);
     e.setExplanation(label+" contributed "+
     round(score*weight)+" points (signal "+round(score)+", weight "+Math.round(weight*100)+"%).");
     evidence.save(e);
     }
 private double defaultCriticality(String t){
     return switch(t.toLowerCase(Locale.ROOT)){
         case "bank_account","production_db","payment_account"->90;
         case "customer_record",
         "order","pii"->75;
         default->50;
         }
         ;
         }
 private double num(Object o){
     if(o instanceof Number n)return n.doubleValue();
     try{
         return Double.parseDouble(String.valueOf(o));
     }
     catch(Exception e){
         return 0;
     }
     }
     private double round(double x){
         return Math.round(x*100.0)/100.0;
     }
 public record Result(String decision,String reason,double compositeScore,
 double assetCriticality,double attackPathRisk){
 }
}
