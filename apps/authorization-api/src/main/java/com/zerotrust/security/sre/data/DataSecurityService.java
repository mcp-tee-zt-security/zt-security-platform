package com.zerotrust.security.sre.data;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class DataSecurityService {
    private final RiskScoringProperties config;

    public DataSecurityService(RiskScoringProperties config) { this.config = config; }

    public double risk(double sensitivity, double exposure, double accessAnomaly) {
        return Math.min(config.getMaxScore(),
                sensitivity * config.getDataSensitivityWeight()
                        + exposure * config.getDataExposureWeight()
                        + accessAnomaly * config.getDataAccessAnomalyWeight());
    }
}
