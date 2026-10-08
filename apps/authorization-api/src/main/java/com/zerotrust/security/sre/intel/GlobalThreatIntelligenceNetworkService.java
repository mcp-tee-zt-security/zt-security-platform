package com.zerotrust.security.sre.intel;

import com.zerotrust.security.config.RiskScoringProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GlobalThreatIntelligenceNetworkService {
    private final RiskScoringProperties config;

    public GlobalThreatIntelligenceNetworkService(RiskScoringProperties config) { this.config = config; }

    public double confidence(double sourceTrust, double corroboration, double freshness) {
        return Math.min(config.getMaxScore(),
                sourceTrust * config.getThreatSourceTrustWeight()
                        + corroboration * config.getThreatCorroborationWeight()
                        + freshness * config.getThreatFreshnessWeight());
    }
}
