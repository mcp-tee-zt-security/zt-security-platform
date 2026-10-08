package com.zt.security.blast;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface BlastRadiusAssessmentRepository extends JpaRepository<BlastRadiusAssessment,UUID>{
    List<BlastRadiusAssessment> findTop20ByTenantIdAndSourceNodeOrderByGeneratedAtDesc(UUID tenantId,String sourceNode);
}
