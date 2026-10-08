package com.zerotrust.security.sre.v4.intelligence;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class V4IntelligenceContextService {
    private final RiskScoringProperties config;

    public V4IntelligenceContextService(RiskScoringProperties config) { this.config = config; }

    public double confidence(double evidence, double corroboration, double model) {
        return Math.min(config.getMaxScore(),
                evidence * config.getIntelligenceEvidenceWeight()
                        + corroboration * config.getIntelligenceCorroborationWeight()
                        + model * config.getIntelligenceModelWeight());
    }
}
