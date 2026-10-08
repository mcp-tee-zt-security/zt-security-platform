package com.zt.security.idempotency;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface IdempotencyRepository extends JpaRepository<IdempotencyKey,
UUID>{
    Optional<IdempotencyKey> findByTenantIdAndKey(UUID tenant,String key);
}
