package com.zerotrust.security.sre.v4.execution;
import org.springframework.stereotype.Service;
@Service
public class V4ExecutionContractService {
 public boolean executable(boolean approval,boolean policy,boolean capability) {
     return approval && policy && capability;
     }
 public boolean idempotencyRequired() {
     return true;
 }
}
