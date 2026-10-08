package com.zerotrust.security.sre.network;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class ZeroTrustNetworkService {
    private final RiskScoringProperties config;

    public ZeroTrustNetworkService(RiskScoringProperties config) { this.config = config; }

    public double flowRisk(double anomaly, double exposure, double privilege) {
        return Math.min(config.getMaxScore(),
                anomaly * config.getNetworkAnomalyWeight()
                        + exposure * config.getNetworkExposureWeight()
                        + privilege * config.getNetworkPrivilegeWeight());
    }

    public boolean allow(double risk, double threshold) { return risk < threshold; }
}
