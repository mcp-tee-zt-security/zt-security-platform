package com.zt.security.rbac;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.zt.security.common.TenantSession;
import com.zt.security.identity.IdentityRepository;
@Service("enterpriseAuth") public class RbacAuthorizationService {
    final RoleBindingRepository bindings;
    final RoleRepository roles;
    final TenantSession tenant;
    final ObjectMapper mapper;
    final IdentityRepository identities;
    RbacAuthorizationService(RoleBindingRepository b,
    RoleRepository r,TenantSession t,ObjectMapper m,IdentityRepository i){
        bindings=b;
        roles=r;
        tenant=t;
        mapper=m;
        identities=i;
        }
 @Transactional(readOnly=true) public boolean hasPermission(Authentication a,
 String permission){
     if(a==null||!a.isAuthenticated())return false;
     if(a.getAuthorities().stream().anyMatch(x->
     x.getAuthority().equals("ROLE_PLATFORM")))return true;
     UUID t=tenant.get();
     if(t==null)return false;
     String subject=a.getName();
     for(RoleBinding b: bindings.findByTenantIdAndIdentityId(t,resolveIdentityId(t,
     subject))){
         Optional<Role> r=roles.findById(b.getRoleId());
         if(r.isPresent()&&contains(r.get().getPermissions(),
         permission))return true;
         }
         return a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_ADMIN"));
 }
 private UUID resolveIdentityId(UUID t,String subject){
     try{
         return UUID.fromString(subject);
     }
     catch(Exception ignored){
     }
     return identities.findByTenantIdAndExternalId(t,
 subject).map(x->x.getId()).orElse(null);
 }
 private boolean contains(String json,String p){
     try{
         List<String> xs=mapper.readValue(json,
         mapper.getTypeFactory().constructCollectionType(List.class,String.class));
         return xs.contains(p)||xs.contains("*");
         }
         catch(Exception e){
             return false;
     }
     }
}
