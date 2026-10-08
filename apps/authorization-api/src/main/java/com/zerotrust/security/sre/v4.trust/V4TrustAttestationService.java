package com.zerotrust.security.sre.v4.trust;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class V4TrustAttestationService {
    private final RiskScoringProperties config;

    public V4TrustAttestationService(RiskScoringProperties config) { this.config = config; }

    public boolean trusted(double score, boolean validNow) {
        return validNow && score >= config.getTrustAttestationThreshold();
    }
}
