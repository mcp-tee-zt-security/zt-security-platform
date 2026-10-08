package com.zerotrust.security.sre.intel;

import com.zerotrust.security.config.RiskScoringProperties;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ThreatIntelligenceService {
    private final RiskScoringProperties config;

    public ThreatIntelligenceService(RiskScoringProperties config) { this.config = config; }

    public double enrichRisk(double baseRisk, double confidence) {
        return Math.min(config.getMaxScore(),
                baseRisk + confidence * config.getThreatConfidenceRiskWeight());
    }

    public Map<String, Object> normalize(String type, String value, String source, double confidence) {
        return Map.of("type", type, "value", value, "source", source, "confidence", confidence);
    }
}
