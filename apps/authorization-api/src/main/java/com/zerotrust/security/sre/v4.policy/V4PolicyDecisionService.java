package com.zerotrust.security.sre.v4.policy;
import org.springframework.stereotype.Service;
@Service
public class V4PolicyDecisionService {
 public String decide(boolean identityTrusted,boolean capabilityGranted,boolean riskAcceptable) {
  return identityTrusted && capabilityGranted && riskAcceptable ? "ALLOW" : "DENY";
 }
}
