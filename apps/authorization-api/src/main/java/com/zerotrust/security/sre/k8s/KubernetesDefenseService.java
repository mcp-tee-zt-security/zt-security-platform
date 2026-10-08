package com.zerotrust.security.sre.k8s;

import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.stereotype.Service;

@Service
public class KubernetesDefenseService {
    private final RiskScoringProperties config;

    public KubernetesDefenseService(RiskScoringProperties config) { this.config = config; }

    public boolean admissionAllowed(double risk, double threshold) { return risk < threshold; }

    public double runtimeRisk(double imageRisk, double behaviorRisk, double networkRisk) {
        return Math.min(config.getMaxScore(),
                imageRisk * config.getKubernetesImageWeight()
                        + behaviorRisk * config.getKubernetesBehaviorWeight()
                        + networkRisk * config.getKubernetesNetworkWeight());
    }
}
