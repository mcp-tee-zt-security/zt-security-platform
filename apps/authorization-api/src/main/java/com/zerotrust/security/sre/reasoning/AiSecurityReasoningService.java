package com.zerotrust.security.sre.reasoning;

import com.zerotrust.security.config.RiskScoringProperties;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiSecurityReasoningService {
    private final RiskScoringProperties config;

    public AiSecurityReasoningService(RiskScoringProperties config) { this.config = config; }

    public Map<String, Object> reason(double threat, double exposure, double confidence) {
        double score = Math.min(config.getMaxScore(),
                threat * config.getReasoningThreatWeight()
                        + exposure * config.getReasoningExposureWeight()
                        + confidence * config.getReasoningConfidenceWeight());
        return Map.of("priorityScore", score, "humanApprovalRequired", true);
    }
}
