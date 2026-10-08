package com.zerotrust.security.sre.federation;

import com.zerotrust.security.config.RiskScoringProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GlobalSocFederationService {
    private final RiskScoringProperties config;

    public GlobalSocFederationService(RiskScoringProperties config) { this.config = config; }

    public Map<String, Object> federationHealth(List<Double> trustScores) {
        double avg = trustScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return Map.of("nodes", trustScores.size(), "averageTrust", avg,
                "healthy", avg >= config.getFederationHealthThreshold());
    }
}
