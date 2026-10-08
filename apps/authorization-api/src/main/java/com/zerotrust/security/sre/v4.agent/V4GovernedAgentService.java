package com.zerotrust.security.sre.v4.agent;
import org.springframework.stereotype.Service;
@Service
public class V4GovernedAgentService {
 public boolean canExecute(boolean capabilityGranted,boolean policyAllows,boolean approvalSatisfied) {
  return capabilityGranted && policyAllows && approvalSatisfied;
 }
 public boolean highImpactRequiresApproval() {
     return true;
 }
}
