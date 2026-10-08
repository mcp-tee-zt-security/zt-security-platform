package com.zerotrust.security.sre.intelligence;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class CyberDefenseIntelligenceOsService {
 public List<String> lifecycle() {
   return List.of("PREVENT","DETECT","PREDICT","INVESTIGATE","RESPOND","VERIFY","RECOVER","LEARN");
 }
 public Map<String,Object> orchestrate(double threatProbability,double risk,double confidence) {
   return Map.of("threatProbability",threatProbability,"riskScore",risk,"confidence",confidence,
                 "approvalRequired",true,"lifecycle",lifecycle());
 }
}
