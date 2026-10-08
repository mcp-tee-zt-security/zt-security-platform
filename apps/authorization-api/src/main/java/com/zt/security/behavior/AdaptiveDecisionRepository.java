package com.zt.security.behavior;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface AdaptiveDecisionRepository extends JpaRepository<AdaptiveDecision,
UUID>{
    List<AdaptiveDecision> findByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID tenant, java.time.Instant after);
    List<AdaptiveDecision> findTop50ByTenantIdAndAgentExternalIdOrderByCreatedAtDesc(UUID tenant,
    String agent);
    }
