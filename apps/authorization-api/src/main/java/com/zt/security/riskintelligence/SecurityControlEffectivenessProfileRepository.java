package com.zt.security.riskintelligence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SecurityControlEffectivenessProfileRepository extends
JpaRepository<SecurityControlEffectivenessProfile,
UUID> {
  Optional<SecurityControlEffectivenessProfile> findByTenantIdAndActionTypeAndTarget(UUID tenantId,
  String actionType,String target);
  List<SecurityControlEffectivenessProfile> findTop100ByTenantIdOrderByEffectivenessScoreDescUpdatedAtDesc(UUID
tenantId);
}
