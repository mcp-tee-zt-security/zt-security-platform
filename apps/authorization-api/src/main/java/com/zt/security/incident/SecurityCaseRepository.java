package com.zt.security.incident;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SecurityCaseRepository extends JpaRepository<SecurityCase,UUID>{
 List<SecurityCase> findTop100ByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
 Optional<SecurityCase> findByIdAndTenantId(UUID id, UUID tenantId);
}
