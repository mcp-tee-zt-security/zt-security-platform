package com.zerotrust.security.sre.cloud;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class CloudSecurityService {
 public Map<String,Object> normalize(String provider,String account,String asset) {
   return Map.of("provider",provider,"account",account,"asset",asset);
 }
}
