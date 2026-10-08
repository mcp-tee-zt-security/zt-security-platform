package com.zerotrust.security.sre.governance;
import org.springframework.stereotype.Component;
@Component
public class FourEyesApprovalGuard {
 public void validate(String requester,String approver) {
   if (requester!=null && requester.equals(approver))
     throw new IllegalStateException("Four-eyes approval requires a distinct approver");
 }
}
