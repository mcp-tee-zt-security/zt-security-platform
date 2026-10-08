package com.zt.security.sso;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import com.zt.security.common.TenantSession;
import java.util.*;
@RestController @RequestMapping("/v1/enterprise/sso") public class SsoController {
    final SsoConnectionRepository repo;
    final TenantSession tenant;
    SsoController(SsoConnectionRepository r,
    TenantSession t){
        repo=r;
        tenant=t;
    }
 record Upsert(String provider,String issuerUri,String clientId,String clientSecretRef,
 Boolean enabled,List<String> allowedDomains,Map<String,String> claimsMapping){
 }
 @GetMapping @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')") @Transactional(readOnly=
 true) List<SsoConnection> list(@RequestHeader("X-Tenant-Id") UUID t){
     tenant.set(t);
     return repo.findByTenantId(t);
     }
 @PutMapping @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") @Transactional SsoConnection
upsert(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody Upsert x){
     tenant.set(t);
     SsoConnection c=repo.findByTenantIdAndProvider(t,
     x.provider()).orElseGet(SsoConnection::new);
     c.setTenantId(t);
     c.setProvider(x.provider());
     c.setIssuerUri(x.issuerUri());
     c.setClientId(x.clientId());
     c.setClientSecretRef(x.clientSecretRef());
     c.setEnabled(x.enabled()!=null&&x.enabled());
     c.setAllowedDomains(x.allowedDomains()==
     null?"[]":new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(x.allowedDomains()).toString());
     c.setClaimsMapping(x.claimsMapping()==null?"{\"subject\":\"sub\",\"email\":\"email\",\"groups\":\"groups\"}":
     new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(x.claimsMapping()).toString());
     return repo.save(c);
     }
}
