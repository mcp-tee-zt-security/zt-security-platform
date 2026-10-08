package com.zt.security.scim;
import com.zt.security.enterprise.*;
import com.zt.security.common.TenantSession;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@RestController @RequestMapping("/scim/v2") public class ScimController {
    final EnterpriseUserRepository users;
    final EnterpriseGroupRepository groups;
    final TenantSession tenant;
    ScimController(EnterpriseUserRepository u,
    EnterpriseGroupRepository g,TenantSession t){
        users=u;
        groups=g;
        tenant=t;
    }
 record Name(String formatted,String givenName,String familyName){
 }
 record UserIn(String userName,
 String displayName,String active,String externalId,Name name,Map<String,
 Object> meta){
 }
 @GetMapping("/ServiceProviderConfig") @PreAuthorize("hasRole('SCIM')") Map<String,
 Object> config(){
     return Map.of("schemas",List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"),
     "patch",Map.of("supported",true),"bulk",Map.of("supported",false),"filter",
     Map.of("supported",true,"maxResults",100),"changePassword",Map.of("supported",
     false));
     }
 @GetMapping("/Users") @PreAuthorize("hasRole('SCIM')") @Transactional(readOnly=true) Map<String,
 Object> listUsers(@RequestHeader("X-Tenant-Id") UUID t,@RequestParam(required=false) String filter){
     tenant.set(t);
     var all=users.findByTenantIdOrderByUserName(t);
     if(filter!=null&&filter.contains("userName eq \"")){
         String q=filter.substring(filter.indexOf("\"")+1,filter.lastIndexOf("\""));
         all=all.stream().filter(u->q.equalsIgnoreCase(u.getUserName())).toList();
     }
     return Map.of("schemas",List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"),
 "totalResults",all.size(),"startIndex",1,"itemsPerPage",all.size(),"Resources",
 all.stream().map(this::resource).toList());
 }
 @GetMapping("/Users/{id}") @PreAuthorize("hasRole('SCIM')") @Transactional(readOnly=true) Map<String,
 Object> getUser(@RequestHeader("X-Tenant-Id") UUID t,@PathVariable UUID id){
     tenant.set(t);
     return resource(users.findById(id).filter(u->t.equals(u.getTenantId())).orElseThrow());
 }
 @PostMapping("/Users") @PreAuthorize("hasRole('SCIM')") @Transactional Map<String,
 Object> create(@RequestHeader("X-Tenant-Id") UUID t,@RequestBody UserIn x){
     tenant.set(t);
     EnterpriseUser u=new EnterpriseUser();
     u.setTenantId(t);
     u.setExternalId(x.externalId()==
     null?UUID.randomUUID().toString():x.externalId());
     u.setUserName(x.userName());
     u.setDisplayName(x.displayName());
     u.setActive(!"false".equalsIgnoreCase(x.active()));
     u.setSource("SCIM");
     return resource(users.save(u));
     }
 @PatchMapping("/Users/{id}") @PreAuthorize("hasRole('SCIM')") @Transactional Map<String,
 Object> patch(@RequestHeader("X-Tenant-Id") UUID t,@PathVariable UUID id,
 @RequestBody Map<String,Object> x){
     tenant.set(t);
     EnterpriseUser u=users.findById(id).filter(v->
     t.equals(v.getTenantId())).orElseThrow();
     if(x.get("active")!=null)u.setActive(Boolean.parseBoolean(String.valueOf(x.get("active"))));
     if(x.get("displayName")!=null)u.setDisplayName(String.valueOf(x.get("displayName")));
     return resource(users.save(u));
     }
 @DeleteMapping("/Users/{id}") @PreAuthorize("hasRole('SCIM')") @Transactional @ResponseStatus(org.springframework.
 http.HttpStatus.NO_CONTENT) void delete(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id){
     tenant.set(t);
     EnterpriseUser u=users.findById(id).filter(v->
     t.equals(v.getTenantId())).orElseThrow();
     u.setActive(false);
     users.save(u);
     }
 private Map<String,Object> resource(EnterpriseUser u){
     return Map.of("schemas",
     List.of("urn:ietf:params:scim:schemas:core:2.0:User"),"id",u.getId().toString(),
     "userName",u.getUserName(),"displayName",u.getDisplayName()==null?u.getUserName():u.getDisplayName(),
     "active",u.isActive(),"externalId",u.getExternalId(),"meta",Map.of("resourceType",
     "User","created",u.getCreatedAt().toString()));
     }
}
