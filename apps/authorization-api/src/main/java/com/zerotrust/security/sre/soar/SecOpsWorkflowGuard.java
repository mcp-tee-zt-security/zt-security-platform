package com.zerotrust.security.sre.soar;
import org.springframework.stereotype.Component;
@Component
public class SecOpsWorkflowGuard {
 public void validateStep(String type,String approvalStatus) {
   if (("EXECUTE".equals(type)||"HIGH_IMPACT".equals(type)) && !"APPROVED".equals(approvalStatus))
     throw new IllegalStateException("Workflow step requires explicit approval");
 }
}
