package com.zerotrust.security.sre.response;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class AutonomousResponseEngineeringService {
    private final RiskScoringProperties config;

    public AutonomousResponseEngineeringService(RiskScoringProperties config) { this.config = config; }

    public boolean safeToPropose(double safety, double rollback) {
        return safety >= config.getResponseSafetyThreshold()
                && rollback >= config.getResponseRollbackThreshold();
    }

    public boolean requiresApproval() { return true; }
}
