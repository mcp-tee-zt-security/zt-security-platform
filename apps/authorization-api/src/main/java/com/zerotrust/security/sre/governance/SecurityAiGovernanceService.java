package com.zerotrust.security.sre.governance;
import org.springframework.stereotype.Service;
@Service
public class SecurityAiGovernanceService {
 public boolean allowed(boolean approved,boolean policyMatch) {
     return approved && policyMatch;
 }
 public boolean highImpactRequiresApproval() {
     return true;
 }
}
