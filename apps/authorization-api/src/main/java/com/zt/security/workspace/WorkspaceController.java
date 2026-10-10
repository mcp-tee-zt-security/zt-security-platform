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
    public WorkspaceController(WorkspaceRepository repo, TenantSession session){
        this.repo=repo;
        this.session=session;
    }

    @GetMapping @Transactional(readOnly=true)
    public List<Workspace> list(@RequestHeader("X-Tenant-Id") UUID tenant){
        session.set(tenant);
        return repo.findByTenantIdOrderByName(tenant);
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
    public Workspace get(@RequestHeader("X-Tenant-Id") UUID tenant,@PathVariable UUID id){
        session.set(tenant);
        return repo.findByTenantIdAndId(tenant,id).orElseThrow();
    }
}
