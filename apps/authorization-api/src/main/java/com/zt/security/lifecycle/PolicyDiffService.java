package com.zt.security.lifecycle;

import com.zt.security.policy.Policy;
import com.zt.security.policy.PolicyDsl;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class PolicyDiffService {
  public Map<String,Object> diff(Policy current, String proposedText) {
    PolicyDsl next = PolicyDsl.parse(proposedText);
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("policy", next.name());
    out.put("fromVersion", current == null ? null : current.getVersion());
    out.put("toVersion", current == null ? null : current.getVersion()+1);
    out.put("risk", risk(current, next));
    List<Map<String,Object>> changes = new ArrayList<>();
    if (current == null) {
      changes.add(change("POLICY", "", "CREATE", "HIGH"));
    }
    else {
      PolicyDsl old = PolicyDsl.parse(current.getPolicyText());
      if (!Objects.equals(old.effect(), next.effect())) changes.add(change("EFFECT",
      old.effect(), next.effect(), effectRisk(old.effect(), next.effect())));
      if (old.priority()!=next.priority()) changes.add(change("PRIORITY",
      String.valueOf(old.priority()), String.valueOf(next.priority()), "MEDIUM"));
      if (!Objects.equals(old.rules(), next.rules())) changes.add(change("MATCH_RULES",
      old.rules().toString(), next.rules().toString(), "HIGH"));
      if (!Objects.equals(String.valueOf(old.condition()), String.valueOf(next.condition()))) changes.
      add(change("CONDITION",
      String.valueOf(old.condition()), String.valueOf(next.condition()), "HIGH"));
      if (!Objects.equals(old.tags(), next.tags())) changes.add(change("TAGS",
      old.tags().toString(), next.tags().toString(), "LOW"));
      if (!Objects.equals(old.mode(), next.mode())) changes.add(change("MODE",
      String.valueOf(old.mode()), String.valueOf(next.mode()), "MEDIUM"));
    }
    out.put("changes", changes);
    out.put("changeCount", changes.size());
    out.put("requiresApproval", changes.stream().anyMatch(x -> Set.of("HIGH","CRITICAL").contains(x.get("risk"))));
    return out;
  }
  private String risk(Policy c, PolicyDsl n){
    if(c==null || "deny".equals(n.effect())) return "HIGH";
    if(!Objects.equals(c.getEffect(), n.effect())) return "CRITICAL";
    return "MEDIUM";
  }
  private String effectRisk(String a,String b){
      if("deny".equals(b)) return "HIGH";
      if("allow".equals(a)&&"step_up".equals(b)) return "LOW";
      return "CRITICAL";
  }
  private Map<String,Object> change(String field,String before,String after,
  String risk){
      return Map.of("field",field,"before",before,"after",after,
      "risk",risk);
      }
}
