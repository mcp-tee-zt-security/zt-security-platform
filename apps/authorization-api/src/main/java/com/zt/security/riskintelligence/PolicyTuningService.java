package com.zt.security.riskintelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.action.EvaluateModels.EvaluateRequest;
import com.zt.security.common.TenantSession;
import com.zt.security.policy.Policy;
import com.zt.security.policy.PolicyDsl;
import com.zt.security.policy.PolicyRepository;
import com.zt.security.simulation.PolicySimulator;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class PolicyTuningService {
  @Value("${zt.security.risk-scoring.policy-confidence-threshold:0.70}")
  private double confidenceThreshold;
  private static final Pattern PRIORITY=Pattern.compile("(?m)(\\bpriority\\s+)\\d+");
  private final SecurityControlEffectivenessProfileRepository profiles;
  private final PolicyTuningProposalRepository proposals;
  private final PolicyRepository policies;
  private final PolicySimulator simulator;
  private final TenantSession tenant;
  private final ObjectMapper mapper=new ObjectMapper();

  PolicyTuningService(SecurityControlEffectivenessProfileRepository p, PolicyTuningProposalRepository pp,
                      PolicyRepository policies, PolicySimulator simulator, TenantSession tenant){
    this.profiles=p;
    this.proposals=pp;
    this.policies=policies;
    this.simulator=simulator;
    this.tenant=tenant;
  }

  @Transactional(readOnly=true)
  public List<Map<String,Object>> candidates(UUID t){
    tenant.set(t);
    return profiles.findTop100ByTenantIdOrderByEffectivenessScoreDescUpdatedAtDesc(t).stream()
      .filter(p -> p.getEvidenceCount() >= 2 && p.getConfidence() >= confidenceThreshold)
      .map(this::candidate).toList();
  }

  @Transactional(readOnly=true)
  public List<Map<String,Object>> list(UUID t){
    tenant.set(t);
    return proposals.findTop100ByTenantIdOrderByCreatedAtDesc(t).stream().map(this::view).toList();
  }

  @Transactional
  public Map<String,Object> propose(UUID t, UUID profileId, UUID basePolicyId, String requestedBy){
    tenant.set(t);
    SecurityControlEffectivenessProfile p=profiles.findById(profileId).filter(x->
    t.equals(x.getTenantId())).orElseThrow(()->new SecurityException("effectiveness profile not found"));
    Policy base=policies.findByIdAndTenantId(basePolicyId,t).orElseThrow(()->
    new SecurityException("base policy not found"));
    if(!"PREFERRED".equals(recommendation(p))) throw new IllegalArgumentException("Only high-confidence PREFERRED controls can create a"
+
" tuning proposal");
    if(!"PUBLISHED".equalsIgnoreCase(base.getStatus()) && !"ACTIVE".equalsIgnoreCase(base.getStatus()))
throw new IllegalArgumentException("Base policy must be published/active");
    int delta=-5;
    int proposed=Math.max(1,base.getPriority()+delta);
    PolicyTuningProposal x=new PolicyTuningProposal();
    x.setTenantId(t);
    x.setProfileId(profileId);
    x.setBasePolicyId(basePolicyId);
    x.setBasePolicyVersion(base.getVersion());
    x.setPriorityDelta(proposed-base.getPriority());
    x.setProposedPriority(proposed);
    x.setRequestedBy(requestedBy==null?"unknown":requestedBy);
    x.setReason("Historical control effectiveness is "+p.getEffectivenessScore()+"% with "+
    p.getConfidence()+" confidence for "+p.getActionType()+" on "+p.getTarget()+".");
    proposals.save(x);
    return view(x);
  }

  @Transactional
  public Map<String,Object> simulate(UUID t, UUID id, EvaluateRequest req){
    tenant.set(t);
    PolicyTuningProposal x=find(t,id);
    Policy base=policies.findByIdAndTenantId(x.getBasePolicyId(),
    t).orElseThrow();
    String draft=retune(base.getPolicyText(),x.getProposedPriority());
    PolicyDsl.parse(draft);
    Object result=simulator.simulateDraft(t,req,draft);
    try{
        x.setSimulationResult(mapper.writeValueAsString(result));
    }
    catch(Exception e){
        throw new IllegalStateException("simulation serialization failed",e);
        }
    x.setSimulationStatus("PASSED");
    x.setUpdatedAt(Instant.now());
    proposals.save(x);
    Map<String,Object> out=view(x);
    out.put("simulation",result);
    return out;
  }

  @Transactional
  public Map<String,Object> approve(UUID t, UUID id, String actor){
    tenant.set(t);
    PolicyTuningProposal x=find(t,id);
    if(!"PENDING".equals(x.getStatus())) throw new IllegalArgumentException("proposal is not pending");
if(!"PASSED".equals(x.getSimulationStatus())) throw new IllegalArgumentException("simulation must pass before approval");
    Policy base=policies.findByIdAndTenantId(x.getBasePolicyId(),t).orElseThrow();
    String draftText=retune(base.getPolicyText(),x.getProposedPriority());
    PolicyDsl d=PolicyDsl.parse(draftText);
    Policy draft=new Policy();
    draft.setId(UUID.randomUUID());
    draft.setTenantId(t);
    draft.setName(d.name());
    draft.setVersion(base.getVersion()+1);
    draft.setPriority(x.getProposedPriority());
    draft.setStatus("DRAFT");
    draft.setEffect(d.effect());
    draft.setPolicyText(draftText);
    draft.setWorkspaceId(base.getWorkspaceId());
    draft.setCreatedBy(actor==null?"approval":actor);
    policies.save(draft);
    x.setStatus("APPROVED_TO_DRAFT");
    x.setApprovedPolicyId(draft.getId());
    x.setApprovedBy(actor);
    x.setApprovedAt(Instant.now());
    x.setUpdatedAt(Instant.now());
    proposals.save(x);
    Map<String,Object> out=view(x);
    out.put("approvedPolicy",policyView(draft));
    out.put("automationBoundary","Approval creates a DRAFT only. Publishing remains a" +
" separate human-controlled operation.");
    return out;
  }

  @Transactional
  public Map<String,Object> reject(UUID t, UUID id, String actor){
    tenant.set(t);
    PolicyTuningProposal x=find(t,id);
    if(!"PENDING".equals(x.getStatus())) throw new
IllegalArgumentException("proposal is not pending");
    x.setStatus("REJECTED");
    x.setApprovedBy(actor);
    x.setUpdatedAt(Instant.now());
    proposals.save(x);
    return view(x);
  }

  private PolicyTuningProposal find(UUID t,UUID id){
      return proposals.findByIdAndTenantId(id,
      t).orElseThrow(()->new SecurityException("tuning proposal not found"));
  }
  private Map<String,Object> candidate(SecurityControlEffectivenessProfile p){
    Map<String,Object> o=new LinkedHashMap<>();
    o.put("profileId",p.getId());
    o.put("actionType",p.getActionType());
    o.put("target",p.getTarget());
    o.put("effectivenessScore",
    p.getEffectivenessScore());
    o.put("confidence",p.getConfidence());
    o.put("evidenceCount",
    p.getEvidenceCount());
    o.put("recommendation",recommendation(p));
    o.put("tuningType",
    "PRIORITY_ADJUSTMENT");
    o.put("suggestedPriorityDelta",-5);
    o.put("requiresSimulation",
    true);
    o.put("requiresHumanApproval",true);
    return o;
  }
  private String recommendation(SecurityControlEffectivenessProfile p){
      return p.getEffectivenessScore()>=80&&p.getConfidence() >= confidenceThreshold?"PREFERRED":
      p.getEffectivenessScore()>=60?"CONSIDER":"REVIEW";
  }
  private String retune(String text,int priority){
    if(text==null||text.isBlank()) throw new IllegalArgumentException("base policy has no DSL text");
    String out=PRIORITY.matcher(text).find()?PRIORITY.matcher(text).replaceFirst("$1"+priority):text.replaceFirst("\\{",
        "{\\n  priority "+priority);
    PolicyDsl.parse(out);
    return out;
  }
  private Map<String,Object> view(PolicyTuningProposal x){
      Map<String,Object> o=new LinkedHashMap<>();
      o.put("proposalId",x.getId());
      o.put("profileId",x.getProfileId());
      o.put("basePolicyId",
      x.getBasePolicyId());
      o.put("basePolicyVersion",x.getBasePolicyVersion());
      o.put("proposedPriority",x.getProposedPriority());
      o.put("priorityDelta",
      x.getPriorityDelta());
      o.put("status",x.getStatus());
      o.put("reason",x.getReason());
      o.put("simulationStatus",x.getSimulationStatus());
      o.put("approvedPolicyId",
      x.getApprovedPolicyId());
      o.put("requestedBy",x.getRequestedBy());
      o.put("approvedBy",
      x.getApprovedBy());
      o.put("createdAt",x.getCreatedAt());
      o.put("approvedAt",
      x.getApprovedAt());
      o.put("requiresHumanApproval",true);
      o.put("publishAutomatically",
      false);
      return o;
      }
  private Map<String,Object> policyView(Policy p){
      return Map.of("id",p.getId(),
      "name",p.getName(),"version",p.getVersion(),"priority",p.getPriority(),
      "status",p.getStatus());
      }
}
