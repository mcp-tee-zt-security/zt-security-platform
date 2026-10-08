package com.zerotrust.security.sre.ai;

import com.zerotrust.security.config.RiskScoringProperties;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiThreatHuntingService {
    private final RiskScoringProperties config;

    public AiThreatHuntingService(RiskScoringProperties config) { this.config = config; }

    public Map<String, Object> prioritize(double anomaly, double identity, double exposure) {
        double risk = Math.min(config.getMaxScore(),
                anomaly * config.getAiThreatAnomalyWeight()
                        + identity * config.getAiThreatIdentityWeight()
                        + exposure * config.getAiThreatExposureWeight());
        String priority = risk >= config.getAiThreatCriticalThreshold() ? "CRITICAL"
                : risk >= config.getAiThreatHighThreshold() ? "HIGH" : "NORMAL";
        return Map.of("riskScore", risk, "priority", priority);
    }
}
