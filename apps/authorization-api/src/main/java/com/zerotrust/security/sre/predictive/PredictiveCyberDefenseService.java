package com.zerotrust.security.sre.predictive;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.zerotrust.security.config.RiskScoringProperties;
import java.util.*;
@Service
public class PredictiveCyberDefenseService {
 private final RiskScoringProperties config;
 @Value("${zt.security.risk-scoring.predictive-exposure-weight:0.35}") private double exposureWeight;
 @Value("${zt.security.risk-scoring.predictive-threat-weight:0.40}") private double threatWeight;
 @Value("${zt.security.risk-scoring.predictive-identity-weight:0.25}") private double identityWeight;
 public PredictiveCyberDefenseService(RiskScoringProperties config) { this.config = config; }
 public Map<String,Object> predict(double exposure,double threat,double identity) {
   double probability=Math.min(1,(exposure * exposureWeight + threat * threatWeight + identity * identityWeight)/100.0);
   return Map.of("probability",probability,"priority",probability >= config.getPredictiveCriticalProbability() ? "CRITICAL" : probability >= config.getPredictiveHighProbability() ? "HIGH" : "NORMAL");
 }
}
