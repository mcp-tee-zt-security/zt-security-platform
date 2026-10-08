package com.zt.security.lifecycle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PolicyChangeRequestRepository extends JpaRepository<PolicyChangeRequest,
UUID>{
    List<PolicyChangeRequest> findByTenantIdOrderByCreatedAtDesc(UUID t);
    Optional<PolicyChangeRequest> findByIdAndTenantId(UUID id,UUID t);
    }
