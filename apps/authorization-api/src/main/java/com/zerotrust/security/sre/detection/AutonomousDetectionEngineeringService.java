package com.zerotrust.security.sre.detection;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class AutonomousDetectionEngineeringService {
    private final RiskScoringProperties config;

    public AutonomousDetectionEngineeringService(RiskScoringProperties config) { this.config = config; }

    public boolean promote(double coverage, double precision) {
        return coverage >= config.getDetectionCoverageThreshold()
                && precision >= config.getDetectionPrecisionThreshold();
    }
}
