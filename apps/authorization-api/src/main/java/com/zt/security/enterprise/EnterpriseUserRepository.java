package com.zt.security.enterprise;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface EnterpriseUserRepository extends JpaRepository<EnterpriseUser,
UUID>{
    List<EnterpriseUser> findByTenantIdOrderByUserName(UUID t);
    Optional<EnterpriseUser>
findByTenantIdAndUserName(UUID t,
    String u);
    Optional<EnterpriseUser> findByTenantIdAndExternalId(UUID t,
    String e);
    }
