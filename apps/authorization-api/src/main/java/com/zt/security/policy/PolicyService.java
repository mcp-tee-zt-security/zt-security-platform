package com.zt.security.policy;
import org.springframework.cache.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import com.zt.security.common.TenantSession;
import org.springframework.stereotype.*;
import java.util.*;
import com.zt.security.lifecycle.PolicyDeploymentRepository;
@Service public class PolicyService {
    final PolicyRepository repo;
    final PolicyEvaluator evaluator;
    final TenantSession tenantSession;
    final PolicyDeploymentRepository deployments;
    PolicyService(PolicyRepository r,PolicyEvaluator e,TenantSession ts,PolicyDeploymentRepository dr){
        repo=r;
        evaluator=e;
        tenantSession=ts;
        deployments=dr;
        }
 @Transactional(readOnly=true) @Cacheable(cacheNames="activePolicies",
 key="#tenant") public List<Policy> active(UUID tenant){
     tenantSession.set(tenant);
     return repo.findByTenantIdAndStatusOrderByPriorityAsc(tenant,"ACTIVE");
 }
 @Transactional @CacheEvict(cacheNames="activePolicies",key="#tenant") public Policy save(UUID tenant,
 Policy p){
     tenantSession.set(tenant);
     p.setTenantId(tenant);
     return repo.save(p);
 }
 @Transactional @CacheEvict(cacheNames="activePolicies",key="#tenant") public Policy publish(UUID tenant,
 UUID id){
     tenantSession.set(tenant);
     Policy p=repo.findByIdAndTenantId(id,
     tenant).orElseThrow();
     p.setStatus("ACTIVE");
     return repo.save(p);
     }
 @Transactional(readOnly=true) public List<Policy> forEvaluation(UUID tenant,
UUID requestId){
    tenantSession.set(tenant);
    List<Policy> base=new ArrayList<>(repo.findByTenantIdAndStatusOrderByPriorityAsc(tenant,
     "ACTIVE"));
     int bucket=Math.floorMod(requestId.hashCode(),100);
     for(var dep: deployments.findAll()){
         if(!tenant.equals(dep.getTenantId()))continue;
         if(!"ACTIVE".equals(dep.getStatus())||
         dep.getCanaryPercent()<=0||bucket>=dep.getCanaryPercent())continue;
         for(Policy p: repo.findByTenantIdAndNameOrderByVersionDesc(tenant,dep.getPolicyName())) if(p.
         getId().equals(dep.getPolicyId())){
             base.removeIf(x->x.getName().equals(p.getName()));
             base.add(p);
             }
             }
             base.sort(Comparator.comparingInt(Policy::getPriority));
     return base;
     }
 @Transactional @CacheEvict(cacheNames="activePolicies",key="#tenant") public void rollback(UUID tenant,
 String name,int version){
     tenantSession.set(tenant);
     repo.findByTenantIdAndNameOrderByVersionDesc(tenant,
     name).forEach(p->p.setStatus(p.getVersion()==version?"ACTIVE":"INACTIVE"));
     repo.findByTenantIdAndNameOrderByVersionDesc(tenant,name).forEach(repo::save);
 }
}
