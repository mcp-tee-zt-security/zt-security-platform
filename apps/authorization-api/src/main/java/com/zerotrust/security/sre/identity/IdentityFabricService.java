package com.zerotrust.security.sre.identity;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class IdentityFabricService {
    private final RiskScoringProperties config;

    public IdentityFabricService(RiskScoringProperties config) { this.config = config; }

    public double trust(double user, double device, double workload, double session) {
        double average = (user + device + workload + session) / config.getIdentityFabricAverageDivisor();
        return Math.max(config.getIdentityFabricMinimumScore(), Math.min(config.getMaxScore(), average));
    }
}
