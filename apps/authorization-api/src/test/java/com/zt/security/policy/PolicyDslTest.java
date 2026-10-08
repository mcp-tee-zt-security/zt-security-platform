package com.zt.security.policy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PolicyDslTest {
    @Test void parsesEnterprisePolicy() {
        var d=PolicyDsl.parse("""
            policy "high_value" {
              priority 10
              effect step_up
              description "High value protection"
              mode "enforce"
              tags ["banking", "ai-agent"]
              principal.type == "AI_AGENT"
              action in ["payment.transfer", "payment.refund"]
              resource.type == "bank_account"
              condition {
                context.amount > 10000000
                and (risk.score >= 70 or agent.behavior == "ANOMALOUS")
              }
            }
            """);
        assertEquals("high_value",d.name());
        assertEquals("step_up",d.effect());
        assertEquals(10,d.priority());
        assertEquals(3,d.rules().size());
        assertNotNull(d.condition());
        assertEquals(2,d.tags().size());
    }

    @Test void rejectsInvalidPolicyInsteadOfPartialParsing() {
        var ex=assertThrows(PolicyDsl.PolicyParseException.class,
        ()->PolicyDsl.parse("policy \"x\" { effect allow principal.type =="
+
" \"AI_AGENT\""));
        assertTrue(ex.getMessage().contains("RBRACE") || ex.getMessage().contains("position"));
    }

    @Test void supportsNotAndLists() {
        var d=PolicyDsl.parse("""
            policy "p" {
                effect deny principal.type == "AI_AGENT" action == "data.export" condition {
              not (resource.classification in ["PUBLIC", "INTERNAL"])
            }
            }
            """);
        assertNotNull(d.condition());
    }
}
