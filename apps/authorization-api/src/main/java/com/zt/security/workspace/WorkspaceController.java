package com.zt.security.workspace;

import com.zt.security.common.TenantSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/v1/workspaces")
public class WorkspaceController {
    private final WorkspaceRepository repo;
    private final TenantSession session;
    @org.springframework.beans.factory.annotation.Autowired private WorkspaceAccess access;
    public WorkspaceController(WorkspaceRepository repo, TenantSession session){
        this.repo=repo;
        this.session=session;
    }

    @GetMapping @Transactional(readOnly=true)
    public List<Workspace> list(@RequestHeader("X-Tenant-Id") UUID tenant,org.springframework.security.core.Authentication auth){
        session.set(tenant);
        if(auth!=null&&auth.getAuthorities().stream().anyMatch(a->Set.of("ROLE_ADMIN","ROLE_PLATFORM").contains(a.getAuthority())))return repo.findByTenantIdOrderByName(tenant);
        var ids=access.accessible(tenant,auth).stream().map(row->row.get("id")).toList();return repo.findByTenantIdOrderByName(tenant).stream().filter(w->ids.contains(w.getId())).toList();
        }
    @GetMapping("/accessible") public Object accessible(@RequestHeader("X-Tenant-Id") UUID tenant,org.springframework.security.core.Authentication auth){return access.accessible(tenant,auth);}
    public record Grant(String principalType,String principalId,boolean enabled){}
    @GetMapping("/{id}/access") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Object grants(@RequestHeader("X-Tenant-Id") UUID tenant,@RequestHeader("X-Workspace-Id") UUID selected,@PathVariable UUID id){if(!id.equals(selected))throw new org.springframework.security.access.AccessDeniedException("Select this workspace first");return access.grants(tenant,id);}
    @PutMapping("/{id}/access") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") @Transactional
    public Object grant(@RequestHeader("X-Tenant-Id") UUID tenant,@RequestHeader("X-Workspace-Id") UUID selected,@PathVariable UUID id,@RequestBody Grant body){
        session.set(tenant);
        if(!id.equals(selected)||repo.findByTenantIdAndId(tenant,id).isEmpty())throw new org.springframework.security.access.AccessDeniedException("Select the workspace before managing its access");
        access.grant(tenant,id,body.principalType(),body.principalId(),body.enabled());return Map.of("workspaceId",id,"principalId",body.principalId(),"enabled",body.enabled());
    }

    @PostMapping @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") @Transactional
    public Workspace create(@RequestHeader("X-Tenant-Id") UUID tenant,@RequestBody Workspace w){
        session.set(tenant);
        if(w.getName()==null||w.getName().isBlank()||w.getName().length()>128||w.getSlug()==null||!w.getSlug().matches("[a-z0-9][a-z0-9-]{0,63}"))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Workspace name and lowercase slug required");
        if(repo.findByTenantIdOrderByName(tenant).stream().anyMatch(x->w.getSlug().equals(x.getSlug())))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Workspace slug already exists");
        w.setId(UUID.randomUUID());
        w.setTenantId(tenant);
        try{return repo.saveAndFlush(w);}catch(org.springframework.dao.DataIntegrityViolationException ex){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Workspace registration conflicts with existing data");}
    }

    @GetMapping("/{id}") @Transactional(readOnly=true)
    public Workspace get(@RequestHeader("X-Tenant-Id") UUID tenant,@PathVariable UUID id,org.springframework.security.core.Authentication auth){
        session.set(tenant);
        if(auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken)access.require(auth,tenant,id);
        return repo.findByTenantIdAndId(tenant,id).orElseThrow();
    }
}
