package com.zerotrust.security.sre.twin;

import com.zerotrust.security.config.RiskScoringProperties;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DigitalTwinService {
    private final RiskScoringProperties config;

    public DigitalTwinService(RiskScoringProperties config) { this.config = config; }

    public Map<String, Object> simulate(double risk, double impact) {
        boolean safeToApply = risk < config.getDigitalTwinSafeRiskThreshold()
                && impact < config.getDigitalTwinSafeImpactThreshold();
        return Map.of("predictedRisk", risk, "predictedImpact", impact, "safeToApply", safeToApply);
    }
}
