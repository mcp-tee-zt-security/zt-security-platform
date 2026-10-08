package com.zt.security.lifecycle;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface PolicyDeploymentRepository extends JpaRepository<PolicyDeployment,UUID>{
 List<PolicyDeployment> findByTenantIdOrderByStartedAtDesc(UUID tenantId);
 Optional<PolicyDeployment> findByIdAndTenantId(UUID id, UUID tenantId);
}
