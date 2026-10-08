package com.zt.security.decision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SecurityDecisionEvidenceRepository extends JpaRepository<SecurityDecisionEvidence,
UUID>{
    List<SecurityDecisionEvidence> findByTenantIdAndDecisionIdOrderByScoreDesc(UUID tenantId,
    UUID decisionId);
    }
