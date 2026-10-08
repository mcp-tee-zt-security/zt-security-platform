package com.zt.security.simulation;

import com.zt.security.action.EvaluateModels.EvaluateRequest;
import com.zt.security.policy.*;
import com.zt.security.risk.RiskEngine;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.*;

@Service public class PolicySimulator {
    final PolicyRepository repo;
    final PolicyEvaluator evaluator;
    final RiskEngine risk;
    PolicySimulator(PolicyRepository r,PolicyEvaluator e,RiskEngine risk){
        repo=r;
        evaluator=e;
        this.risk=risk;
    }

    @Cacheable(cacheNames="policy-simulation", key="#tenant.toString()+':'+#policyId.toString()+':'+T(java." +
"util.Objects).hash(#req)")
    public Object simulate(UUID tenant,EvaluateRequest req,UUID policyId){
        Policy p=repo.findByIdAndTenantId(policyId,tenant).orElseThrow();
        return result(p,req,tenant);
    }
    public Object simulateDraft(UUID tenant,EvaluateRequest req,String text){
        PolicyDsl d=PolicyDsl.parse(text);
        if(d.name()==null||d.effect()==null)throw new
IllegalArgumentException("Invalid policy DSL");
        Policy p=new Policy();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenant);
        p.setName(d.name());
        p.setVersion(0);
        p.setEffect(d.effect());
        p.setPolicyText(text);
        return result(p,req,tenant);
    }
    public Map<String,Object> compare(UUID tenant,EvaluateRequest req,List<UUID> policyIds){
        List<Map<String,Object>> rows=new ArrayList<>();
        for(UUID id:policyIds){
            Policy p=repo.findByIdAndTenantId(id,tenant).orElseThrow();
            rows.add(cast(result(p,
            req,tenant)));
            }
        return Map.of("tenantId",tenant,"policyCount",rows.size(),"results",
        rows,"risk",risk.score(tenant,risk.fingerprint(req.context()),req.context()));
    }
    private Object result(Policy p,EvaluateRequest req,UUID tenant){
        var r=evaluator.evaluate(List.of(p),req);
        var rr=risk.score(tenant,
        risk.fingerprint(req.context()),req.context());
        return Map.of("policyId",p.getId(),"policyName",p.getName(),"version",
        p.getVersion(),"decision",r.decision(),"reason",r.reason(),"matched",r.matched(),
        "riskScore",rr.score(),"riskLevel",rr.level(),"evidence",rr.evidence());
    }
    @SuppressWarnings("unchecked") private Map<String,Object> cast(Object x){
        return (Map<String,Object>)x;
    }
}
