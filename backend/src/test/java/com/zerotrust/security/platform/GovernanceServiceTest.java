package com.zerotrust.security.platform;

import com.zerotrust.security.platform.domain.GovernanceModels.ActionRequest;
import com.zerotrust.security.platform.governance.GovernanceService;
import com.zerotrust.security.platform.observability.ObservabilityService;
import com.zerotrust.security.platform.store.PlatformStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GovernanceServiceTest {
    @Test
    void highRiskActionRequiresApproval() {
        GovernanceService service = new GovernanceService(new PlatformStore(), new ObservabilityService());

        var result = service.evaluate(new ActionRequest(
                "analyst",
                "isolate-workload",
                "workload/payment",
                "tenant-a",
                Map.of()));

        assertEquals("REQUIRE_APPROVAL", result.decision());
    }
}
