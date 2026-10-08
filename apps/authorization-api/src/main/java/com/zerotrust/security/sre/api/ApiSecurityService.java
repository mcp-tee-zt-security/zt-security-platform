package com.zerotrust.security.sre.api;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class ApiSecurityService {
    private final RiskScoringProperties config;

    public ApiSecurityService(RiskScoringProperties config) { this.config = config; }

    public double risk(double exposure, double abuse, double identity) {
        return Math.min(config.getMaxScore(),
                exposure * config.getApiExposureWeight()
                        + abuse * config.getApiAbuseWeight()
                        + identity * config.getApiIdentityWeight());
    }
}
