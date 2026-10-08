package com.zerotrust.security.sre.twin;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class SecurityDigitalTwin2Service {
    private final RiskScoringProperties config;

    public SecurityDigitalTwin2Service(RiskScoringProperties config) {
        this.config = config;
    }

    public boolean safe(double predictedRisk, double blastRadius) {
        return predictedRisk < config.getDigitalTwinSafeRiskThreshold()
                && blastRadius < config.getDigitalTwinSafeImpactThreshold();
    }
}
