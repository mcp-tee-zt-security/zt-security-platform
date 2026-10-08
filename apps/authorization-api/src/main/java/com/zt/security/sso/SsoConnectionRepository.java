package com.zt.security.sso;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface SsoConnectionRepository extends JpaRepository<SsoConnection,
UUID>{
    List<SsoConnection> findByTenantId(UUID t);
    Optional<SsoConnection> findByTenantIdAndProvider(UUID t,
    String p);
    }
