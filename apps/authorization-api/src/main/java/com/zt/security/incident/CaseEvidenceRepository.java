package com.zt.security.incident;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CaseEvidenceRepository extends JpaRepository<CaseEvidence,UUID>{
 List<CaseEvidence> findTop200ByTenantIdAndCaseIdOrderByCreatedAtAsc(UUID tenantId, UUID caseId);
}
