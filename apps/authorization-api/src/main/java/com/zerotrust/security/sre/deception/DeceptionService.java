package com.zerotrust.security.sre.deception;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class DeceptionService {
    private final RiskScoringProperties config;

    public DeceptionService(RiskScoringProperties config) { this.config = config; }

    public boolean suspicious(double confidence) {
        return confidence >= config.getDeceptionConfidenceThreshold();
    }
}
