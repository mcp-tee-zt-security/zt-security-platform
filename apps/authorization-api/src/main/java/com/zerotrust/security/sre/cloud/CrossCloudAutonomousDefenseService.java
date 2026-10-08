package com.zerotrust.security.sre.cloud;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class CrossCloudAutonomousDefenseService {
    private final RiskScoringProperties config;

    public CrossCloudAutonomousDefenseService(RiskScoringProperties config) { this.config = config; }

    public double aggregateRisk(double aws, double azure, double gcp) {
        return Math.min(config.getMaxScore(),
                aws * config.getCrossCloudAwsWeight()
                        + azure * config.getCrossCloudAzureWeight()
                        + gcp * config.getCrossCloudGcpWeight());
    }
}
