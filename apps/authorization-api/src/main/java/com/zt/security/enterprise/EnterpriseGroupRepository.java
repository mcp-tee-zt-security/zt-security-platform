package com.zt.security.enterprise;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface EnterpriseGroupRepository extends JpaRepository<EnterpriseGroup,
UUID>{
    List<EnterpriseGroup> findByTenantIdOrderByDisplayName(UUID t);
    Optional<EnterpriseGroup>
findByTenantIdAndDisplayName(UUID t,
    String n);
    }
