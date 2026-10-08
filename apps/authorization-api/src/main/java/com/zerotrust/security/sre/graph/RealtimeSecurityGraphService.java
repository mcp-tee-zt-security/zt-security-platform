package com.zerotrust.security.sre.graph;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class RealtimeSecurityGraphService {
    private final RiskScoringProperties config;

    public RealtimeSecurityGraphService(RiskScoringProperties config) { this.config = config; }

    public double updateRisk(double current, double delta) {
        return Math.max(config.getIdentityFabricMinimumScore(),
                Math.min(config.getMaxScore(), current + delta));
    }
}
