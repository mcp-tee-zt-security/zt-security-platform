package com.zt.security.riskintelligence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SecurityControlFeedbackRepository extends JpaRepository<SecurityControlFeedback,UUID> {
  Optional<SecurityControlFeedback> findByTenantIdAndResponseId(UUID tenantId, UUID responseId);
  List<SecurityControlFeedback> findTop100ByTenantIdOrderByEvaluatedAtDesc(UUID tenantId);
}
