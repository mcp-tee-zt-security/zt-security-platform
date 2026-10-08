package com.zt.security.rbac;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.
annotation.Transactional;
import com.zt.security.common.TenantSession;
import com.zt.security.common.WorkspaceSession;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/rbac") public class RbacController {
    final RoleRepository roles;
    final RoleBindingRepository bindings;
    final TenantSession session;
    final WorkspaceSession workspace;
    final RbacAuthorizationService auth;
    final com.zt.security.identity.IdentityRepository identities;
    RbacController(RoleRepository r,
    RoleBindingRepository b,TenantSession s,WorkspaceSession w,RbacAuthorizationService a,
    com.zt.security.identity.IdentityRepository i){
        roles=r;
        bindings=b;
        session=s;
        workspace=w;
        auth=a;
        identities=i;
        }
 @PostMapping("/roles") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") @Transactional public Role
create(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody Role r){
     session.set(t);
     r.setTenantId(t);
     return roles.save(r);
 }
 @GetMapping("/roles") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')") @Transactional(readOnly=
 true) public List<Role> list(@RequestHeader("X-Tenant-Id") UUID t){
     session.set(t);
     return roles.findByTenantId(t);
     }
 @PostMapping("/bindings") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") @Transactional public
RoleBinding bind(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestHeader(value="X-Workspace-Id",required=false) UUID w,@RequestBody RoleBinding b){
     session.set(t);
     workspace.set(w);
     b.setTenantId(t);
     b.setWorkspaceId(w);
     return bindings.save(b);
 }
 @GetMapping("/identities/{id}/roles") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')")
@Transactional(readOnly=
 true) public List<RoleBinding> list(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestHeader(value="X-Workspace-Id",required=false) UUID w,@PathVariable UUID id){
     session.set(t);
     workspace.set(w);
     return bindings.findByTenantIdAndIdentityId(t,
     id);
     }
 @GetMapping("/me/permissions") @Transactional(readOnly=true) public Map<String,
 Object> me(@RequestHeader("X-Tenant-Id") UUID t,org.springframework.security.core.Authentication a){
     session.set(t);
     List<String> ps=new ArrayList<>();
     for(RoleBinding b:bindings.findByTenantIdAndIdentityId(t,
     resolve(a.getName(),t))){
         roles.findById(b.getRoleId()).ifPresent(r->{
             try{
                 var m=new com.fasterxml.jackson.databind.ObjectMapper();
                 ps.addAll(m.readValue(r.getPermissions(),
                 m.getTypeFactory().constructCollectionType(List.class,String.class)));
             }
             catch(Exception ignored){
             }
             }
             );
             }
             return Map.of("subject",a.getName(),"permissions",
 ps.stream().distinct().sorted().toList());
 }
 private UUID resolve(String s,UUID t){
     try{
         return UUID.fromString(s);
     }
     catch(Exception e){
         return identities.findByTenantIdAndExternalId(t,s).map(x->x.getId()).orElse(UUID.randomUUID());
     }
     }
}
