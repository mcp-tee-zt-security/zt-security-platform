package com.zerotrust.security.sre.supply;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class SupplyChainSecurityService {
    private final RiskScoringProperties config;

    public SupplyChainSecurityService(RiskScoringProperties config) { this.config = config; }

    public double risk(double vuln, double provenance, double exposure) {
        return Math.min(config.getMaxScore(),
                vuln * config.getSupplyVulnerabilityWeight()
                        + provenance * config.getSupplyProvenanceWeight()
                        + exposure * config.getSupplyExposureWeight());
    }
}
