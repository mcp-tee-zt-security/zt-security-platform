package com.zt.security.compliance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ComplianceAuditBundleRepository extends JpaRepository<ComplianceAuditBundle,
UUID> {
    Optional<ComplianceAuditBundle> findByIdAndTenantId(UUID id,UUID tenantId);
}
