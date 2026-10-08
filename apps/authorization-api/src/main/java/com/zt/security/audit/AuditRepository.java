package com.zt.security.audit;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface AuditRepository extends JpaRepository<AuditLog,
UUID>{
    Optional<AuditLog> findTopByTenantIdOrderByCreatedAtDesc(UUID t);
    List<AuditLog> findTop100ByTenantIdOrderByCreatedAtDesc(UUID t);
    }
