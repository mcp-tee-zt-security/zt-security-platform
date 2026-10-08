package com.zt.security.decision;
import org.springframework.stereotype.Service;
import java.util.*;
@Service public class SecurityDecisionExplainabilityService {
 private final SecurityDecisionEvidenceRepository evidence;
 private final SecurityDecisionRepository decisions;
 SecurityDecisionExplainabilityService(SecurityDecisionEvidenceRepository e,
 SecurityDecisionRepository d){
     evidence=e;
     decisions=d;
 }
 public List<SecurityDecisionEvidence> evidence(UUID tenant,UUID id){
     return
evidence.findByTenantIdAndDecisionIdOrderByScoreDesc(tenant,
     id);
     }
 public Map<String,Object> explain(UUID tenant,UUID id){
     SecurityDecision d=decisions.findById(id).orElseThrow();
if(!tenant.equals(d.getTenantId())) throw new org.springframework.security.access.AccessDeniedException("decision tenant mismatch");
     var ev=evidence(tenant,id);
     return Map.ofEntries(
        Map.entry("decision", d.getDecision()),
        Map.entry("compositeScore", d.getCompositeScore()),
        Map.entry("reason", d.getReason()),
        Map.entry("principal", d.getPrincipalId()),
        Map.entry("action", d.getAction()),
        Map.entry("resource", d.getResourceType()+"/"+d.getResourceId()),
        Map.entry("baseRisk", d.getBaseRisk()),
        Map.entry("behaviorRisk", d.getBehaviorRisk()),
        Map.entry("assetCriticality", d.getAssetCriticality()),
        Map.entry("attackPathRisk", d.getAttackPathRisk()),
        Map.entry("policyRisk", d.getPolicyRisk()),
        Map.entry("evidence", ev),
        Map.entry("explanation", humanSummary(d,ev))
    );
     }
 private String humanSummary(SecurityDecision d,List<SecurityDecisionEvidence> ev){
     if(ev.isEmpty()) return d.getReason();
     return "Decision "+d.getDecision()+" at risk "+
     d.getCompositeScore()+". Top signals: "+ev.stream().limit(3).map(SecurityDecisionEvidence::getExplanation).reduce((a,
     b)->a+"; "+b).orElse(d.getReason());
     }
}
