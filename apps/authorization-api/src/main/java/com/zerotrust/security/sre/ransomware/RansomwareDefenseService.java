package com.zerotrust.security.sre.ransomware;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class RansomwareDefenseService {
    private final RiskScoringProperties config;

    public RansomwareDefenseService(RiskScoringProperties config) { this.config = config; }

    public double risk(double encryptionBurst, double processAnomaly, double lateralMovement) {
        return Math.min(config.getMaxScore(),
                encryptionBurst * config.getRansomwareEncryptionBurstWeight()
                        + processAnomaly * config.getRansomwareProcessAnomalyWeight()
                        + lateralMovement * config.getRansomwareLateralMovementWeight());
    }

    public boolean isolateRecommended(double risk) { return risk >= config.getRansomwareIsolationThreshold(); }
}
