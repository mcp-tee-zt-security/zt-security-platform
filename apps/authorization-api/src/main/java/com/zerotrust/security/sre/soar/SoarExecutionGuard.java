package com.zerotrust.security.sre.soar;
import org.springframework.stereotype.Component;
@Component
public class SoarExecutionGuard {
  public void requireApproved(String approvalStatus) {
if (!"APPROVED".equals(approvalStatus)) throw new IllegalStateException("SOAR execution requires explicit approval");
  }
}
