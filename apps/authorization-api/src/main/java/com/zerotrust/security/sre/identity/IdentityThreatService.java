package com.zerotrust.security.sre.identity;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IdentityThreatService {
    @Value("${zt.security.risk-scoring.identity-impossible-travel-score:40.0}")
    private double impossibleTravelScore;
    @Value("${zt.security.risk-scoring.identity-privilege-change-score:30.0}")
    private double privilegeChangeScore;
    @Value("${zt.security.risk-scoring.identity-suspicious-session-score:30.0}")
    private double suspiciousSessionScore;
    private final RiskScoringProperties config;

    public IdentityThreatService(RiskScoringProperties config) { this.config = config; }

    public double score(boolean impossibleTravel, boolean privilegeChange, boolean suspiciousSession) {
        return Math.min(config.getMaxScore(),
                (impossibleTravel ? impossibleTravelScore : 0)
                        + (privilegeChange ? privilegeChangeScore : 0)
                        + (suspiciousSession ? suspiciousSessionScore : 0));
    }
}
