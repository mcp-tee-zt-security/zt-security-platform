package com.zerotrust.security.sre.saas;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class SaasSecurityService {
    private final RiskScoringProperties config;

    public SaasSecurityService(RiskScoringProperties config) { this.config = config; }

    public double posture(double privilege, double configScore, double shadow) {
        return Math.min(config.getMaxScore(),
                privilege * config.getSaasPrivilegeWeight()
                        + configScore * config.getSaasConfigWeight()
                        + shadow * config.getSaasShadowWeight());
    }
}
