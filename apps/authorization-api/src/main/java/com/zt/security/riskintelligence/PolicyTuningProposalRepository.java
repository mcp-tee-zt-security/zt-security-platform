package com.zt.security.riskintelligence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PolicyTuningProposalRepository extends JpaRepository<PolicyTuningProposal,UUID>{
  List<PolicyTuningProposal> findTop100ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
  Optional<PolicyTuningProposal> findByIdAndTenantId(UUID id, UUID tenantId);
}
