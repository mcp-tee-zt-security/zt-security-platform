package com.zt.security.enterprise;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import com.zt.security.common.TenantSession;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
@RestController @RequestMapping("/v1/enterprise/directory") public class EnterpriseDirectoryController {
 final EnterpriseUserRepository users;
 final EnterpriseGroupRepository groups;
 final TenantSession tenant;
 final ObjectMapper mapper;
 EnterpriseDirectoryController(EnterpriseUserRepository u,EnterpriseGroupRepository g,
 TenantSession t,ObjectMapper m){
     users=u;
     groups=g;
     tenant=t;
     mapper=m;
 }
 record UserIn(String externalId,String userName,String displayName,String email,
 Boolean active,String source,Map<String,Object> attributes){
 }
 @GetMapping("/users") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')") @Transactional(readOnly=
 true) List<EnterpriseUser> users(@RequestHeader("X-Tenant-Id") UUID t){
     tenant.set(t);
     return users.findByTenantIdOrderByUserName(t);
     }
 @PostMapping("/users") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") @Transactional EnterpriseUser
create(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody UserIn x){
     tenant.set(t);
     EnterpriseUser u=new EnterpriseUser();
     u.setTenantId(t);
     u.setExternalId(x.externalId()==null?x.userName():x.externalId());
     u.setUserName(x.userName());
     u.setDisplayName(x.displayName());
     u.setEmail(x.email());
     u.setActive(x.active()==null||x.active());
     u.setSource(x.source()==null?"LOCAL":x.source());
     u.setAttributes(x.attributes()==null?"{}":com.zt.security.common.Jsons.string(mapper,
     x.attributes()));
     return users.save(u);
     }
 @PatchMapping("/users/{id}") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") @Transactional
EnterpriseUser patch(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@RequestBody UserIn x){
     tenant.set(t);
     EnterpriseUser u=users.findById(id).filter(v->
     t.equals(v.getTenantId())).orElseThrow();
     if(x.displayName()!=null)u.setDisplayName(x.displayName());
     if(x.email()!=null)u.setEmail(x.email());
     if(x.active()!=null)u.setActive(x.active());
     u.touch();
     return users.save(u);
 }
 @GetMapping("/groups") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')") @Transactional(readOnly=
 true) List<EnterpriseGroup> groups(@RequestHeader("X-Tenant-Id") UUID t){
     tenant.set(t);
     return groups.findByTenantIdOrderByDisplayName(t);
     }
}
