package com.zt.security.incident;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SecurityIncidentRepository extends JpaRepository<SecurityIncident,
UUID>{
    List<SecurityIncident> findTop100ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
