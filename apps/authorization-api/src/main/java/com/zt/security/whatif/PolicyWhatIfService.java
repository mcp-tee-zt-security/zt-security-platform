package com.zt.security.whatif;

import com.zt.security.action.EvaluateModels;
import com.zt.security.policy.Policy;
import com.zt.security.policy.PolicyDsl;
import com.zt.security.policy.PolicyEvaluator;
import com.zt.security.policy.PolicyRepository;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class PolicyWhatIfService {
    private final PolicyRepository policies;
    private final PolicyEvaluator evaluator;

    public PolicyWhatIfService(PolicyRepository policies, PolicyEvaluator evaluator) {
        this.policies = policies;
        this.evaluator = evaluator;
    }

    public Map<String,Object> simulate(UUID tenant, String policyText, List<Scenario> requested) {
        PolicyDsl d = PolicyDsl.parse(policyText);
        if (d.name() == null || d.effect() == null) throw new IllegalArgumentException("Invalid policy DSL");
        List<Policy> active = policies.findByTenantIdAndStatusOrderByPriorityAsc(tenant, "ACTIVE");
        Policy draft = new Policy();
        draft.setId(UUID.randomUUID());
        draft.setTenantId(tenant);
        draft.setName(d.name());
        draft.setVersion(0);
        draft.setPriority(d.priority());
        draft.setEffect(d.effect());
        draft.setPolicyText(policyText);

        List<Scenario> cases = requested == null || requested.isEmpty() ? defaults() : requested;
        List<Map<String,Object>> results = new ArrayList<>();
        int changed=0, newlyDenied=0, newlyStepUp=0, newlyAllowed=0;
        Set<String> impactedResources = new LinkedHashSet<>();
        for (Scenario s : cases) {
            var req = request(s);
            var before = evaluator.evaluate(active, req);
            List<Policy> afterPolicies = new ArrayList<>(active);
            afterPolicies.add(draft);
            afterPolicies.sort(Comparator.comparingInt(Policy::getPriority));
            var after = evaluator.evaluate(afterPolicies, req);
            boolean change = !before.decision().equals(after.decision());
            if(change) changed++;
            if(!before.decision().equals("DENY") && after.decision().equals("DENY")) newlyDenied++;
            if(!before.decision().equals("STEP_UP") && after.decision().equals("STEP_UP")) newlyStepUp++;
            if(!before.decision().equals("ALLOW") && after.decision().equals("ALLOW")) newlyAllowed++;
            if(change) impactedResources.add(s.resourceType()+":"+s.resourceId());
            results.add(Map.of("id",s.id(),"description",s.description(),
            "action",s.action(),"resource",s.resourceType()+":"+s.resourceId(),
                    "before",before.decision(),"after",after.decision(),"changed",change,
                    "beforeReason",before.reason(),"afterReason",after.reason()));
        }
        int baselineDeny = (int) results.stream().filter(x -> "DENY".equals(x.get("before"))).count();
        int projectedDeny = (int) results.stream().filter(x -> "DENY".equals(x.get("after"))).count();
        double riskReduction = cases.isEmpty()?0:Math.min(100.0, Math.max(0, (newlyDenied*100.0)/cases.size()));
        String impact = newlyDenied >= 3 ? "HIGH" : newlyDenied > 0 ? "MEDIUM" : "LOW";
        return new LinkedHashMap<>(Map.ofEntries(
        Map.entry("policyName", d.name()),
        Map.entry("effect", d.effect()),
        Map.entry("priority", d.priority()),
        Map.entry("scenarioCount", cases.size()),
        Map.entry("changedDecisions", changed),
        Map.entry("newlyDenied", newlyDenied),
        Map.entry("newlyStepUp", newlyStepUp),
        Map.entry("newlyAllowed", newlyAllowed),
        Map.entry("baselineDenied", baselineDeny),
        Map.entry("projectedDenied", projectedDeny),
        Map.entry("estimatedRiskReductionPct", round(riskReduction)),
        Map.entry("blastRadiusImpact", impact),
        Map.entry("impactedResources", impactedResources),
        Map.entry("results", results),
        Map.entry("guardrail", "SIMULATION_ONLY: no policy publish or runtime mutation is performed")
    ));
    }

    private EvaluateModels.EvaluateRequest request(Scenario s) {
        return new EvaluateModels.EvaluateRequest(
                new EvaluateModels.Principal(s.principalId(), s.principalType(), Map.of()),
                new EvaluateModels.Action(s.action()),
                new EvaluateModels.Resource(s.resourceType(), s.resourceId(),
                Map.of("classification",s.classification())),
                s.context()==null?Map.of():s.context());
    }
    private List<Scenario> defaults(){
        return List.of(
                new Scenario("payment-high","High value transfer","agent-payment",
                "AI_AGENT","payment.transfer","bank_account","ACC-1001","CONFIDENTIAL",
                Map.of("amount",15000000,"risk.score",75)),
                new Scenario("payment-low","Normal transfer","agent-payment",
                "AI_AGENT","payment.transfer","bank_account","ACC-1001","CONFIDENTIAL",
                Map.of("amount",100000,"risk.score",20)),
                new Scenario("export","External restricted export","agent-payment",
                "AI_AGENT","data.export","dataset","CUSTOMER-PII","RESTRICTED",Map.of("destination.type",
                "external")),
                new Scenario("refund","High value refund","agent-refund",
                "AI_AGENT","refund.create","order","ORDER-1001","CONFIDENTIAL",Map.of("amount",
                800000,"risk.score",68))
        );
    }
    private double round(double x){
        return Math.round(x*10.0)/10.0;
    }
    public record Scenario(String id,String description,String principalId,
    String principalType,String action,String resourceType,String resourceId,
    String classification,Map<String,Object> context){
    }
}
