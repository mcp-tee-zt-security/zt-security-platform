package com.zt.security.audit;
import com.zt.security.common.TenantSession;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
@Service public class AuditVerificationService {
    final AuditRepository repo;
    final TenantSession session;
    AuditVerificationService(AuditRepository r,
    TenantSession s){
        repo=r;
        session=s;
    }
    @Transactional(readOnly=true) public Map<String,
Object> verify(UUID tenant){
    session.set(tenant);
    List<AuditLog> all=repo.findTop100ByTenantIdOrderByCreatedAtDesc(tenant).
        stream().sorted(Comparator.comparing(AuditLog::getCreatedAt)).toList();
        String prev="";
        int checked=0;
        List<String> bad=new ArrayList<>();
        for(AuditLog a:all){
            String expected=hash(prev+a.getId()+tenant+a.getRequestId()+a.getDecision()+a.getCreatedAt());
            if(!Objects.equals(prev,a.getPreviousHash())||!expected.equals(a.getEventHash()))bad.add(a.
            getId().toString());
            prev=a.getEventHash();
            checked++;
            }
            return Map.of("valid",bad.isEmpty(),"checked",
        checked,"invalidEventIds",bad);
        }
        private String hash(String s){
            try{
                return
HexFormat.of().formatHex(MessageDigest.
            getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
        }
        }
