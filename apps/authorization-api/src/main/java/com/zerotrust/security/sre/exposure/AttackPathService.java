package com.zerotrust.security.sre.exposure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AttackPathService {

    private final double maxScore;
    private final double exposureWeight;
    private final double privilegeWeight;
    private final double assetRiskWeight;

    public AttackPathService(
            @Value("${zt.security.risk-scoring.max-score:100.0}") double maxScore,
            @Value("${zt.security.risk-scoring.attack-path-weight:0.30}") double exposureWeight,
            @Value("${zt.security.risk-scoring.identity-weight:0.20}") double privilegeWeight,
            @Value("${zt.security.risk-scoring.vulnerability-weight:0.25}") double assetRiskWeight) {
        this.maxScore = maxScore;
        this.exposureWeight = exposureWeight;
        this.privilegeWeight = privilegeWeight;
        this.assetRiskWeight = assetRiskWeight;
    }

    public double criticality(double exposure, double privilege, double assetRisk) {
        double weightedScore = exposure * exposureWeight
                + privilege * privilegeWeight
                + assetRisk * assetRiskWeight;
        return Math.min(maxScore, weightedScore);
    }

    public List<String> rank(List<String> paths) {
        return new ArrayList<>(paths);
    }
}
