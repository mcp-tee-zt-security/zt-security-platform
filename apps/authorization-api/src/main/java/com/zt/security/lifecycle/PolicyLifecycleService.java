package com.zt.security.lifecycle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.common.TenantSession;
import org.springframework.cache.annotation.CacheEvict;
import com.zt.security.policy.*;
import com.zt.security.simulation.PolicySimulator;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service public class PolicyLifecycleService {
 final PolicyChangeRequestRepository changes;
 final PolicyDeploymentRepository deployments;
 final GitOpsSourceRepository gitops;
 final PolicyRepository policies;
 final TenantSession tenant;
 final PolicyDsl dsl;
 final ObjectMapper mapper;
 final PolicyDiffService diffService;
 PolicyLifecycleService(PolicyChangeRequestRepository c,PolicyDeploymentRepository d,
 GitOpsSourceRepository g,PolicyRepository p,TenantSession t,ObjectMapper m,
 PolicyDiffService ds){
     changes=c;
     deployments=d;
     gitops=g;
     policies=p;
     tenant=t;
     mapper=m;
     diffService=ds;
     dsl=null;
     }
 @Transactional public PolicyChangeRequest propose(UUID t,String source,
 String ref,String name,int version,String text,String by,int canary,String sha){
     tenant.set(t);
     PolicyDsl parsed=PolicyDsl.parse(text);
     if(parsed.name()==
     null||parsed.effect()==null) throw new IllegalArgumentException("Invalid policy DSL");
     PolicyChangeRequest x=new PolicyChangeRequest();
     x.setId(UUID.randomUUID());
     x.setTenantId(t);
     x.setSource(source==null?"API":source);
     x.setSourceRef(ref);
     x.setPolicyName(name==null?parsed.name():name);
     x.setVersion(version);
     x.setPolicyText(text);
     x.setRequestedBy(by);
     x.setCanaryPercent(Math.max(0,Math.min(100,canary)));
     x.setCommitSha(sha);
     x.setStatus("PENDING");
     return changes.save(x);
     }
 @Transactional public PolicyChangeRequest approve(UUID t,UUID id,String approver,
 String comment){
     tenant.set(t);
     PolicyChangeRequest x=changes.findByIdAndTenantId(id,
t).orElseThrow();
if(!"PENDING".equals(x.getStatus())) throw new IllegalStateException("change request not pending");
     if(approver==null||approver.isBlank()) throw new IllegalArgumentException("approver is required");
     if(approver.equalsIgnoreCase(x.getRequestedBy())) throw new IllegalStateException("self-approval is not allowed");
     x.setApprovals(x.getApprovals()+1);
     x.setApprovedBy(approver);
     x.setApprovalComment(comment);
     if(x.getApprovals()>=x.getRequiredApprovals()){
         x.setStatus("APPROVED");
         x.setApprovedAt(Instant.now());
         }
         return changes.save(x);
         }
 @Transactional @CacheEvict(cacheNames="activePolicies",key="#t") public PolicyChangeRequest publish(UUID t,
 UUID id){
     tenant.set(t);
     PolicyChangeRequest x=changes.findByIdAndTenantId(id,
     t).orElseThrow();
     if(!"APPROVED".equals(x.getStatus())) throw new
IllegalStateException("policy change requires approval");
     Policy p=new Policy();
     p.setId(UUID.randomUUID());
     p.setTenantId(t);
     p.setName(x.getPolicyName());
     p.setVersion(x.getVersion());
     p.setStatus(x.getCanaryPercent()>0?"CANARY":"ACTIVE");
     p.setPriority(100);
     p.setEffect(PolicyDsl.parse(x.getPolicyText()).effect());
     p.setPolicyText(x.getPolicyText());
     p.setCreatedBy(x.getRequestedBy());
     p.setUpdatedAt(Instant.now());
     p=policies.save(p);
     x.setPolicyId(p.getId());
     x.setStatus(x.getCanaryPercent()>0?"CANARY":"PUBLISHED");
     x.setPublishedAt(Instant.now());
     PolicyDeployment dep=new PolicyDeployment();
     dep.setId(UUID.randomUUID());
     dep.setTenantId(t);
     dep.setPolicyId(p.getId());
     dep.setPolicyName(p.getName());
     dep.setVersion(p.getVersion());
     dep.setStage(x.getCanaryPercent()>0?"CANARY":"PRODUCTION");
     dep.setCanaryPercent(x.getCanaryPercent());
     dep.setStatus("ACTIVE");
     dep.setCommitSha(x.getCommitSha());
     if(x.getCanaryPercent()>0) dep.setCanaryStartedAt(Instant.now());
     deployments.save(dep);
     return changes.save(x);
     }
 @Transactional @CacheEvict(cacheNames="activePolicies",key="#t") public void promote(UUID t,
 UUID id){
     tenant.set(t);
     PolicyDeployment dep=deployments.findByIdAndTenantId(id,
     t).orElseThrow();
     if(!"CANARY".equals(dep.getStage()) || !"ACTIVE".equals(dep.getStatus())) throw
new IllegalStateException("deployment is not an active canary");
     List<Policy> all=policies.findByTenantIdAndNameOrderByVersionDesc(t,dep.getPolicyName());
     for(Policy p:all){
         p.setStatus(p.getId().equals(dep.getPolicyId())?"ACTIVE":"INACTIVE");
         policies.save(p);
         }
         dep.setCanaryPercent(0);
         dep.setStage("PRODUCTION");
     dep.setStatus("ACTIVE");
     dep.setCompletedAt(Instant.now());
     deployments.save(dep);
 }
 @Transactional @CacheEvict(cacheNames="activePolicies",key="#t") public void rollback(UUID t,
 String name,int version){
     tenant.set(t);
     List<Policy> all=policies.findByTenantIdAndNameOrderByVersionDesc(t,
     name);
     boolean found=false;
     for(Policy p:all){
         boolean a=p.getVersion()==version;
         p.setStatus(a?"ACTIVE":"INACTIVE");
         if(a)found=true;
         policies.save(p);
         }
         if(!found)throw new IllegalArgumentException("policy version not found");
 }
 public List<PolicyChangeRequest> changes(UUID t){
     tenant.set(t);
     return changes.findByTenantIdOrderByCreatedAtDesc(t);
 }
 public List<PolicyDeployment> deployments(UUID t){
     tenant.set(t);
     return
deployments.findByTenantIdOrderByStartedAtDesc(t);
 }
 public Map<String,Object> diff(UUID t,UUID id){
     tenant.set(t);
     PolicyChangeRequest x=changes.findByIdAndTenantId(id,
     t).orElseThrow();
     Policy current=policies.findTopByTenantIdAndNameOrderByVersionDesc(t,
     x.getPolicyName()).orElse(null);
     Map<String,Object> d=diffService.diff(current,
     x.getPolicyText());
     try{
         x.setDiffJson(mapper.writeValueAsString(d));
         changes.save(x);
     }
     catch(Exception ignored){
     }
     return d;
     }
 @Transactional @CacheEvict(cacheNames="activePolicies",key="#t") public void abortCanary(UUID t,
 UUID id,String reason){
     tenant.set(t);
     PolicyDeployment dep=deployments.findByIdAndTenantId(id,
t).orElseThrow();
if(!"CANARY".equals(dep.getStage())) throw new IllegalStateException("deployment is not a canary");
     dep.setStatus("ABORTED");
     dep.setCanaryPercent(0);
     dep.setCanaryStoppedAt(Instant.now());
     dep.setCanaryReason(reason);
     dep.setCompletedAt(Instant.now());
     deployments.save(dep);
     policies.findByIdAndTenantId(dep.getPolicyId(),t).ifPresent(p->{
         p.setStatus("INACTIVE");
         p.setUpdatedAt(Instant.now());
         policies.save(p);
         }
         );
         }
 @Transactional public GitOpsSource registerGit(UUID t,GitOpsSource x){
     tenant.set(t);
     x.setId(UUID.randomUUID());
     x.setTenantId(t);
     return gitops.save(x);
 }
 @Transactional public Map<String,Object> sync(UUID t,UUID sourceId,String commit,
 String policyText,String name,int version,String by){
     tenant.set(t);
     GitOpsSource g=gitops.findById(sourceId).
     orElseThrow();
     g.setLastCommitSha(commit);
     g.setLastSyncedAt(Instant.now());
     gitops.save(g);
     PolicyChangeRequest x=propose(t,"GITOPS",g.getRepository()+"@"+commit,
     name,version,policyText,by,0,commit);
     x.setStatus("PENDING");
     changes.save(x);
     return Map.of("status","PROPOSED","changeRequestId",x.getId(),"commitSha",
     commit);
     }
}
