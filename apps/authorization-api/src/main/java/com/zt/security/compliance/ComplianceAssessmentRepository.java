package com.zt.security.compliance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ComplianceAssessmentRepository extends JpaRepository<ComplianceAssessment,
UUID>{
    List<ComplianceAssessment> findTop50ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<ComplianceAssessment> findByIdAndTenantId(UUID id,UUID tenantId);
}
