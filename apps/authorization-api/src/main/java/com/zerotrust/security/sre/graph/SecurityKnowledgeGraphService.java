package com.zerotrust.security.sre.graph;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class SecurityKnowledgeGraphService {
 public Map<String,Object> pathRisk(List<Double> edgeRisks) {
   double max=edgeRisks.stream().mapToDouble(Double::doubleValue).max().orElse(0);
   double avg=edgeRisks.stream().mapToDouble(Double::doubleValue).average().orElse(0);
   return Map.of("maxRisk",max,"averageRisk",avg,"edges",edgeRisks.size());
 }
}
