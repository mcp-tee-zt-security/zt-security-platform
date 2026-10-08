package com.zt.security.policy;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface PolicyRepository extends JpaRepository<Policy,UUID>{
    List<Policy>
findByTenantIdAndStatusOrderByPriorityAsc(UUID t,
    String s);
    Optional<Policy> findByIdAndTenantId(UUID id,UUID t);
    Optional<Policy>
findTopByTenantIdAndNameOrderByVersionDesc(UUID t,
    String n);
    List<Policy> findByTenantIdAndNameOrderByVersionDesc(UUID t,
    String n);
    }
