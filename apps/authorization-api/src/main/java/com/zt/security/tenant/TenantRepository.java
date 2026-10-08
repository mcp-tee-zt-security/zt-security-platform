package com.zt.security.tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TenantRepository extends JpaRepository<Tenant,
UUID>{
    Optional<Tenant> findBySlug(String slug);
}
