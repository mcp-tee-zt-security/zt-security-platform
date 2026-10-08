package com.zerotrust.security.sre.command;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class SecurityCommandCenterService {
 public Map<String,Object> executiveKpi(double risk,double exposure,int incidents,int responses) {
   return Map.of("overallRisk",risk,"exposure",exposure,"incidents",incidents,"activeResponses",responses);
 }
}
