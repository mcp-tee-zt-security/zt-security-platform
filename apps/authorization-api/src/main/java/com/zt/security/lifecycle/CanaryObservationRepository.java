package com.zt.security.lifecycle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CanaryObservationRepository extends JpaRepository<CanaryObservation,
UUID>{
    List<CanaryObservation> findTop50ByTenantIdAndDeploymentIdOrderByObservedAtDesc(UUID tenantId,
    UUID deploymentId);
    }
