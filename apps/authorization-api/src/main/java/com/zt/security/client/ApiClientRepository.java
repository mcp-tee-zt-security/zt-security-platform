package com.zt.security.client;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ApiClientRepository extends JpaRepository<ApiClient,
UUID>{
    Optional<ApiClient> findByTenantIdAndClientId(UUID tenantId,String clientId);
    Optional<ApiClient> findByClientId(String clientId);
    List<ApiClient> findByTenantId(UUID tenantId);
}
