package com.zt.security.response;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SecurityResponseActionRepository extends JpaRepository<SecurityResponseAction,
UUID>{
    List<SecurityResponseAction> findTop100ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<SecurityResponseAction> findByIdAndTenantId(UUID id,UUID tenantId);
}
