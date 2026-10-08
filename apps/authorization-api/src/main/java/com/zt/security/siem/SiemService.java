package com.zt.security.siem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.audit.AuditLog;
import com.zt.security.common.TenantSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
@Service public class SiemService {
    final SiemSinkRepository repo;
    final TenantSession session;
    final ObjectMapper mapper;
    final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.
    ofSeconds(2)).build();
    public SiemService(SiemSinkRepository r,TenantSession s,ObjectMapper m){
        repo=r;
        session=s;
        mapper=m;
        }
 @Transactional(readOnly=true) public List<SiemSink> list(UUID tenant){
     session.set(tenant);
     return repo.findByTenantIdAndEnabledTrue(tenant);
     }
 @Transactional public SiemSink save(UUID tenant,SiemSink x){
     session.set(tenant);
     x.setId(UUID.randomUUID());
     x.setTenantId(tenant);
     return repo.save(x);
     }
 public void publish(AuditLog a){
     try{
         for(SiemSink s:list(a.getTenantId())){
             String body=mapper.writeValueAsString(Map.ofEntries(
        Map.entry("schema", "zt.security.audit/v1"),
        Map.entry("eventId", a.getId()),
        Map.entry("tenantId", a.getTenantId()),
        Map.entry("requestId", a.getRequestId()),
        Map.entry("action", a.getAction()),
        Map.entry("resourceType", a.getResourceType()),
        Map.entry("resourceId", a.getResourceId()),
        Map.entry("decision", a.getDecision()),
        Map.entry("reason", a.getReason()),
        Map.entry("riskScore", a.getRiskScore()),
        Map.entry("createdAt", a.getCreatedAt())
    ));
             client.sendAsync(HttpRequest.newBuilder(URI.
             create(s.getEndpoint())).timeout(Duration.ofSeconds(3)).header("Content-Type",
             "application/json").header("X-ZT-Signature",signature(body,s.getSecretRef())).POST(HttpRequest.
             BodyPublishers.ofString(body)).build(),
             HttpResponse.BodyHandlers.discarding());
             }
             }
             catch(Exception ignored){
             }
             }
 private String signature(String body,String secret){
     if(secret==null||secret.isBlank())return "unsigned";
     try{
         var mac=javax.crypto.Mac.getInstance("HmacSHA256");
         mac.init(new javax.crypto.spec.SecretKeySpec(secret.
         getBytes(java.nio.charset.StandardCharsets.UTF_8),
         "HmacSHA256"));
         return HexFormat.of().formatHex(mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.
         UTF_8)));
     }
     catch(Exception e){
         return "invalid";
     }
     }
}
