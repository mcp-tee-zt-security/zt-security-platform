package com.zerotrust.security.sre.xdr;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class XdrCorrelationService {
 public Map<String,Object> correlate(List<Map<String,Object>> observations) {
   return Map.of("observationCount",observations.size(),"correlated",observations.size()>1);
 }
}
