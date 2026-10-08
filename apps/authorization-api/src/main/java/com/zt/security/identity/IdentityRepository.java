package com.zt.security.identity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface IdentityRepository extends JpaRepository<Identity,
UUID>{
    Optional<Identity> findByTenantIdAndExternalId(UUID t,String e);
    List<Identity> findByTenantId(UUID t);
    }
