package com.zerotrust.security.sre.ctem;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExposureManagementService {

    private final double maxScore;
    private final double attackPathWeight;
    private final double identityWeight;
    private final double vulnerabilityWeight;
    private final double threatWeight;

    public ExposureManagementService(
            @Value("${zt.security.risk-scoring.max-score:100.0}") double maxScore,
            @Value("${zt.security.risk-scoring.attack-path-weight:0.30}") double attackPathWeight,
            @Value("${zt.security.risk-scoring.identity-weight:0.20}") double identityWeight,
            @Value("${zt.security.risk-scoring.vulnerability-weight:0.25}") double vulnerabilityWeight,
            @Value("${zt.security.risk-scoring.threat-weight:0.25}") double threatWeight) {
        this.maxScore = maxScore;
        this.attackPathWeight = attackPathWeight;
        this.identityWeight = identityWeight;
        this.vulnerabilityWeight = vulnerabilityWeight;
        this.threatWeight = threatWeight;
    }

    public double score(double attackPath, double identity, double vuln, double threat) {
        double weightedScore = attackPath * attackPathWeight
                + identity * identityWeight
                + vuln * vulnerabilityWeight
                + threat * threatWeight;
        return Math.min(maxScore, weightedScore);
    }
}
