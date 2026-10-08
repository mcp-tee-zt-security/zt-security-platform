package com.zt.security.common;

import com.zt.security.policy.Policy;
import com.zt.security.risk.RiskEngine;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RedisSerializationTest {
    @Test
    void cachedPoliciesRetainTheirTypeAndInstantAfterRoundTrip() {
        var pair = new RedisConfig().redisCacheConfiguration().getValueSerializationPair();
        Policy policy = new Policy();
        policy.setId(UUID.randomUUID());
        policy.setName("cache-regression");
        var decoded = (List<?>) pair.read(pair.write(new ArrayList<>(List.of(policy))));
        Policy restored = assertInstanceOf(Policy.class, decoded.get(0));
        assertEquals(policy.getId(), restored.getId());
        assertEquals(policy.getCreatedAt(), restored.getCreatedAt());
    }

    @Test
    void cachedRiskResultRetainsEvidenceAndRecordType() {
        var pair = new RedisConfig().redisCacheConfiguration().getValueSerializationPair();
        var result = new RiskEngine.Result(35, "LOW", List.of(
                new RiskEngine.Evidence("TRANSACTION", "HIGH_AMOUNT", 35, 35, "test")));
        var restored = assertInstanceOf(RiskEngine.Result.class, pair.read(pair.write(result)));
        assertEquals(result, restored);
    }
}
