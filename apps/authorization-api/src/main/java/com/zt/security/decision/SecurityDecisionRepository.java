package com.zt.security.decision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SecurityDecisionRepository extends JpaRepository<SecurityDecision,
UUID>{
    List<SecurityDecision> findTop100ByTenantIdOrderByCreatedAtDesc(UUID t);
    Optional<SecurityDecision> findTopByTenantIdAndPrincipalIdOrderByCreatedAtDesc(UUID t,
    String p);
    }
