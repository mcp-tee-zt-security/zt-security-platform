package com.zerotrust.security.sre.secops;
import org.springframework.stereotype.Component;
import java.util.Set;
@Component
public class SecOpsAutomationGuard {
 private static final Set<String> APPROVAL_REQUIRED=Set.of("ISOLATE_AGENT",
 "BLOCK_IP","DISABLE_CREDENTIAL","REVOKE_SESSION","ROLLBACK_POLICY","QUARANTINE_RESOURCE");
 public boolean requiresApproval(String actionType) {
     return APPROVAL_REQUIRED.contains(actionType);
 }
 public void assertApproved(String approvalStatus) {
if (!"APPROVED".equals(approvalStatus)) throw new IllegalStateException("SecOps response action requires explicit approval");
 }
}
