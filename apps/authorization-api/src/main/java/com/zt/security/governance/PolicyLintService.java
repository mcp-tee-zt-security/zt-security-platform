package com.zt.security.governance;
import com.zt.security.policy.PolicyDsl;
import org.springframework.stereotype.Service;
import java.util.*;
@Service public class PolicyLintService {
 public List<Map<String,Object>> lint(String text){
     List<Map<String,Object>> out=new ArrayList<>();
     if(text==null||text.isBlank()){
         out.add(Map.of("severity","ERROR","code",
         "EMPTY_POLICY","message","Policy text is empty"));
         return out;
         }
         PolicyDsl d;
     try {
         d=PolicyDsl.parse(text);
     }
     catch(PolicyDsl.PolicyParseException e){
         out.add(Map.of("severity","ERROR","code","DSL_PARSE_ERROR","message",e.getMessage()));
         return out;
         }
         if(d.name()==null)out.add(Map.of("severity","ERROR","code",
     "MISSING_NAME","message","policy name is required"));
     if(d.effect()==null)out.add(Map.of("severity",
     "ERROR","code","MISSING_EFFECT","message","effect must be allow, deny or step_up"));
     if(text.contains("action == \"*\"")&&text.contains("effect allow"))out.add(Map.of("severity",
     "CRITICAL","code","GLOBAL_ALLOW","message","Wildcard action with allow effect can grant broad authority"));
     if(text.contains("resource.type == \"*\"")&&text.contains("effect allow"))out.add(Map.of("severity",
     "HIGH","code","GLOBAL_RESOURCE_ALLOW","message","Wildcard resource allow creates a large blast radius"));
     if(text.contains("context.amount")&&text.contains("effect allow")&&!text.contains("condition"))out.
     add(Map.of("severity",
     "MEDIUM","code","AMOUNT_WITHOUT_GUARD","message","Amount-aware policy should use an explicit condition block"));
     if(d.rules().size()>30)out.add(Map.of("severity","MEDIUM","code","RULE_COMPLEXITY",
     "message","Policy has more than 30 parsed rules"));
     return out;
     }
}
