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
        w.setId(UUID.randomUUID());
        w.setTenantId(tenant);
        return repo.save(w);
    }

    @GetMapping("/{id}") @Transactional(readOnly=true)
    public Workspace get(@RequestHeader("X-Tenant-Id") UUID tenant,@PathVariable UUID id){
        session.set(tenant);
        return repo.findByTenantIdAndId(tenant,id).orElseThrow();
    }
}
