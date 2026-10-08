package com.zerotrust.security.sre.ai;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
@Service
public class AiSecurityAnalystService {
 @Value("${zt.security.risk-scoring.analyst-confidence-threshold:0.70}") private double confidenceThreshold;
 public Map<String,Object> investigationPlan(String incidentSummary) {
   return Map.of("steps",List.of("scope","timeline","entities","evidence","response-review"),
                 "summary",incidentSummary);
 }
 public boolean canRecommend(double confidence) {
     return confidence >= confidenceThreshold;
 }
}
