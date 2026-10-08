package com.zerotrust.security.sre.insider;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class InsiderRiskService {
    private final RiskScoringProperties config;

    public InsiderRiskService(RiskScoringProperties config) { this.config = config; }

    public double score(double accessAnomaly, double dataExfil, double privilegeAbuse) {
        return Math.min(config.getMaxScore(),
                accessAnomaly * config.getInsiderAccessAnomalyWeight()
                        + dataExfil * config.getInsiderDataExfiltrationWeight()
                        + privilegeAbuse * config.getInsiderPrivilegeAbuseWeight());
    }
}
