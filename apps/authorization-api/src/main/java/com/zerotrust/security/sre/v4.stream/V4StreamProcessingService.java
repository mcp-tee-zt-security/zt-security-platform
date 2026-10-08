package com.zerotrust.security.sre.v4.stream;
import org.springframework.stereotype.Service;
@Service
public class V4StreamProcessingService {
 public boolean validCheckpoint(String checkpoint) {
     return checkpoint!=null && !checkpoint.isBlank();
 }
 public boolean retryable(int retryCount) {
     return retryCount < 5;
 }
}
