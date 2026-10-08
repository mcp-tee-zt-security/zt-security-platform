package com.zt.security.approval;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface ApprovalRepository extends JpaRepository<Approval,
UUID>{
    List<Approval> findByTenantIdOrderByCreatedAtDesc(UUID t);
    Optional<Approval> findByIdAndTenantId(UUID id,
    UUID t);
    List<Approval> findByTenantIdAndRequestIdOrderByCreatedAtDesc(UUID tenantId,UUID requestId);
    }
