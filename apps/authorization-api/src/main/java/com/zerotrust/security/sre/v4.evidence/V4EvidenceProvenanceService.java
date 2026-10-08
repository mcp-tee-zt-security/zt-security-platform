package com.zerotrust.security.sre.v4.evidence;
import org.springframework.stereotype.Service;
import java.security.*;
@Service
public class V4EvidenceProvenanceService {
 public boolean hashPresent(String sha256) {
     return sha256!=null && sha256.matches("[0-9a-fA-F]{64}");
 }
}
