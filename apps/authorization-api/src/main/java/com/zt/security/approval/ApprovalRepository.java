package com.zt.security.approval;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface ApprovalRepository extends JpaRepository<Approval,
UUID>{
    List<Approval> findByTenantIdOrderByCreatedAtDesc(UUID t);
    Optional<Approval> findByIdAndTenantId(UUID id,
    UUID t);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Approval a where a.id=:id and a.tenantId=:tenant")
    Optional<Approval> lockByIdAndTenantId(@org.springframework.data.repository.query.Param("id") UUID id,@org.springframework.data.repository.query.Param("tenant") UUID tenant);
    List<Approval> findByTenantIdAndRequestIdOrderByCreatedAtDesc(UUID tenantId,UUID requestId);
    }
