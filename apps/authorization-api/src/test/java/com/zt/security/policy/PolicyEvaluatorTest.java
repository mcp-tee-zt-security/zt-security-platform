package com.zt.security.policy;
import com.zt.security.action.EvaluateModels.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PolicyEvaluatorTest {
 private final PolicyEvaluator evaluator=new PolicyEvaluator();
 private EvaluateRequest req(double amount){
     return new EvaluateRequest(new Principal("agent-1",
     "AI_AGENT",Map.of()),new Action("payment.transfer"),new Resource("bank_account",
     "a1",Map.of()),Map.of("amount",amount));
     }
 private Policy p(String name,String effect,String rule){
     Policy p=new Policy();
     p.setId(UUID.randomUUID());
     p.setName(name);
     p.setVersion(1);
     p.setStatus("ACTIVE");
     p.setEffect(effect);
     p.setPolicyText("policy \""+name+"\" { effect "+effect+" principal.type == \"AI_AGENT\" action =="
+
" \"payment.transfer\" condition { "+
             rule+" } }");
     return p;
     }
 @Test void denyWins(){
     var r=evaluator.evaluate(List.of(p("allow","allow",
     "context.amount <= 10000000"),p("deny","deny","context.amount > 5000000")),
     req(7000000));
     assertEquals("DENY",r.decision());
     }
 @Test void defaultDeny(){
     var r=evaluator.evaluate(List.of(),req(1));
     assertEquals("DENY",r.decision());
 }
 @Test void stepUpBeforeAllow(){
     var r=evaluator.evaluate(List.of(p("allow",
     "allow","context.amount <= 10000000"),p("step","step_up","context.amount > 5000000")),
     req(7000000));
     assertEquals("STEP_UP",r.decision());
     }
}
