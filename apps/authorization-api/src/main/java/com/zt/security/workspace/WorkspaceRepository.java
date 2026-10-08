package com.zt.security.workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WorkspaceRepository extends JpaRepository<Workspace,UUID>{
    List<Workspace> findByTenantIdOrderByName(UUID tenantId);
    Optional<Workspace> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<Workspace> findByTenantIdAndSlug(UUID tenantId, String slug);
}
