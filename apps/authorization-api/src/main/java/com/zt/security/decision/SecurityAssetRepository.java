package com.zt.security.decision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SecurityAssetRepository extends JpaRepository<SecurityAsset,
UUID>{
    Optional<SecurityAsset> findByTenantIdAndResourceTypeAndResourceId(UUID t,
    String type,String id);
    List<SecurityAsset> findByTenantIdOrderByCriticalityDesc(UUID t);
}
