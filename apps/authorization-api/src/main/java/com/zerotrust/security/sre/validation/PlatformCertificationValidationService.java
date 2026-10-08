package com.zerotrust.security.sre.validation;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class PlatformCertificationValidationService {
    private final RiskScoringProperties config;

    public PlatformCertificationValidationService(RiskScoringProperties config) { this.config = config; }

    public boolean releaseReady(double score, int failedControls) {
        return score >= config.getCertificationScoreThreshold() && failedControls == 0;
    }
}
