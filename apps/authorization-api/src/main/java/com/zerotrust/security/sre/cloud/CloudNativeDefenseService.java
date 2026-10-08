package com.zerotrust.security.sre.cloud;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class CloudNativeDefenseService {
    private final RiskScoringProperties config;

    public CloudNativeDefenseService(RiskScoringProperties config) { this.config = config; }

    public double risk(double identity, double configScore, double runtime) {
        return Math.min(config.getMaxScore(),
                identity * config.getCloudIdentityWeight()
                        + configScore * config.getCloudConfigWeight()
                        + runtime * config.getCloudRuntimeWeight());
    }
}
