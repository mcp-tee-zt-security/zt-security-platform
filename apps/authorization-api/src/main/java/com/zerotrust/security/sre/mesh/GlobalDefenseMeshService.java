package com.zerotrust.security.sre.mesh;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class GlobalDefenseMeshService {
 public Map<String,Object> correlate(List<Double> risks) {
   double max=risks.stream().mapToDouble(Double::doubleValue).max().orElse(0);
   return Map.of("nodes",risks.size(),"maxRisk",max,"correlated",risks.size()>1);
 }
}
