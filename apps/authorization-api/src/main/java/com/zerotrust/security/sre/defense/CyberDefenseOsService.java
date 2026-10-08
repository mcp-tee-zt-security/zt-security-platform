package com.zerotrust.security.sre.defense;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class CyberDefenseOsService {
 public List<String> lifecycle() {
   return List.of("PREVENT","DETECT","PREDICT","INVESTIGATE","RESPOND","VERIFY","RECOVER","LEARN");
 }
 public boolean requiresApproval(String actionType) {
     return true;
 }
 public Map<String,Object> createCycle(double risk,double confidence) {
   return Map.of("risk",risk,"confidence",confidence,"approvalRequired",true,"lifecycle",lifecycle());
 }
}
