package com.zt.security.governance;

import com.zt.security.common.TenantSession;
import com.zt.security.policy.Policy;
import com.zt.security.policy.PolicyDsl;
import com.zt.security.policy.PolicyRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.
annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1/governance/policies")
public class PolicyGovernanceController {
  private final PolicyRepository policies;
  private final TenantSession session;
  private final PolicyLintService lint;
  public PolicyGovernanceController(PolicyRepository p, TenantSession s,
  PolicyLintService l){
      policies=p;
      session=s;
      lint=l;
  }

  @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')")
  @GetMapping("/{id}/blast-radius")
  @Transactional(readOnly=true)
  public Map<String,Object> blast(@RequestHeader("X-Tenant-Id") UUID tenant,@PathVariable UUID id){
    session.set(tenant);
    Policy p=policies.findByIdAndTenantId(id,tenant).orElseThrow();
    PolicyDsl d=PolicyDsl.parse(p.getPolicyText());
    Set<String> actions=new TreeSet<>(), resources=new TreeSet<>(), identities=new TreeSet<>();
    for(PolicyDsl.Rule r:d.rules()){
        if(r.left().equals("action"))actions.add(r.right());
        if(r.left().equals("resource.type"))resources.add(r.right());
        if(r.left().equals("principal.type"))identities.
        add(r.right());
    }
    String risk=(actions.contains("*")||resources.contains("*")?"HIGH":actions.size()>10?"MEDIUM":"LOW");
    return Map.of("policyId",id,"policy",p.getName(),"version",p.getVersion(),
    "riskLevel",risk,"affectedActions",actions,"affectedResourceTypes",resources,
    "affectedPrincipalTypes",identities,"recommendation",risk.equals("HIGH")?
    "Require review before publish":"Review optional");
  }

  @PostMapping("/lint")
  public Map<String,Object> lint(@RequestBody Map<String,String> x){
      var issues=lint.lint(x.get("policyText"));
      boolean ok=issues.stream().noneMatch(i -> "ERROR".equals(i.get("severity")) ||
      "CRITICAL".equals(i.get("severity")));
      return Map.of("valid",ok,"issues",issues);
      }

  @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')")
  @PostMapping("/diff")
  public Map<String,Object> diff(@RequestBody DiffRequest x){
    PolicyDsl a=PolicyDsl.parse(x.before());
    PolicyDsl b=PolicyDsl.parse(x.after());
    return Map.of("changed",!Objects.equals(x.before(),x.after()),"beforeEffect",
    String.valueOf(a.effect()),"afterEffect",String.valueOf(b.effect()),"beforeRules",
    a.rules(),"afterRules",b.rules());
  }
  public record DiffRequest(String before,String after){
  }

  @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')")
  @GetMapping("/{id}/as-code")
  @Transactional(readOnly=true)
  public Map<String,Object> export(@RequestHeader("X-Tenant-Id") UUID tenant,
  @PathVariable UUID id){
      session.set(tenant);
      Policy p=policies.findByIdAndTenantId(id,
      tenant).orElseThrow();
      return Map.of("apiVersion","zt.security/v1","kind",
      "AuthorizationPolicy","metadata",Map.of("name",p.getName(),"version",p.getVersion(),
      "id",p.getId()),"spec",Map.of("priority",p.getPriority(),"status",p.getStatus(),
      "policyText",p.getPolicyText()));
      }
}
