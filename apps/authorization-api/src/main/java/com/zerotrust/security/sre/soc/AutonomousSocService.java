package com.zerotrust.security.sre.soc;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class AutonomousSocService {
 public List<String> phases() {
     return List.of("TRIAGE","INVESTIGATE",
     "RECOMMEND","APPROVE","EXECUTE","VERIFY","CLOSE");
     }
 public Map<String,Object> recommend(double risk) {
     return Map.of("risk",
     risk,"approvalRequired",risk>=0,"phases",phases());
     }
}
