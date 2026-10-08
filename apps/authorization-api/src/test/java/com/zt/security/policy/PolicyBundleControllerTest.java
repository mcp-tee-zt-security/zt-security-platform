package com.zt.security.policy;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PolicyBundleControllerTest {
    @Test void javaAndRustShareUtf8CanonicalVector() {
        var policy = new PolicyBundleController.FastPolicy("정책|x", 1, 7, "allow",
            "P", List.of("a"), "R", null, true);
        var bundle = new PolicyBundleController.Bundle(2, "v", "t", null, "time",
            null, null, null, List.of(policy));
        String expected = "zt-policy-bundle-v2\n1:2\n1:t\n-\n1:v\n4:time\n-\n1:1\n8:정책|x\n1:1\n1:7\n5:allow\n1:P\n1:1\n1:a\n1:R\n-\n4:true\n";
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), PolicyBundleController.canonical(bundle));
    }
    @Test void duplicateActionClausesCannotBroadenAnAllowPolicy() {
        Policy policy = mock(Policy.class);
        when(policy.getName()).thenReturn("duplicate");
        when(policy.getVersion()).thenReturn(1);
        when(policy.getEffect()).thenReturn("allow");
        var dsl = PolicyDsl.parse("""
            policy "duplicate" {
                effect allow
                principal.type == "AI_AGENT"
                resource.type == "bank_account"
                action == "payment.transfer"
                action == "refund.create"
            }
            """);
        var projection = PolicyBundleController.project(policy, dsl);
        assertFalse(projection.fastPath());
        assertTrue(projection.actions().isEmpty());
    }
    @Test void revisionIncludesFastEligibilityAndScope() {
        var policy = new PolicyBundleController.FastPolicy("p", 1, 7, "allow",
            "P", List.of("a"), "R", null, true);
        var deferred = new PolicyBundleController.FastPolicy("p", 1, 7, "allow",
            "P", List.of("a"), "R", null, false);
        String baseline = PolicyBundleController.revision("t", null, List.of(policy));
        assertNotEquals(baseline, PolicyBundleController.revision("t", null, List.of(deferred)));
        assertNotEquals(baseline, PolicyBundleController.revision("t", "w", List.of(policy)));
    }
}
