package com.zt.security;
import com.zt.security.action.EvaluateModels.*;
import com.zt.security.policy.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PolicyEvaluatorTest{
    @Test void denyWins(){
        Policy p=new Policy();
        p.setId(UUID.randomUUID());
        p.setName("large");
        p.setEffect("deny");
        p.setPolicyText("policy \"large\" { effect deny principal.type =="
+
" \"AI_AGENT\" action == " +
"\"payment.transfer\" condition { context.amount > 10000000 } }");
        p.setPriority(1);
        var r=new PolicyEvaluator().evaluate(List.of(p),new EvaluateRequest(new Principal("a",
        "AI_AGENT",Map.of()),new Action("payment.transfer"),new Resource("bank_account",
        "1",Map.of()),Map.of("amount",15000000)));
        assertEquals("DENY",r.decision());
    }
    }
