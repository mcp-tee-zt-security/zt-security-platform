package com.zt.security.runtime;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AgentRuntimeSessionRepository extends JpaRepository<AgentRuntimeSession,
UUID>{
    Optional<AgentRuntimeSession> findByIdAndTenantId(UUID id,UUID tenantId);
    List<AgentRuntimeSession> findTop50ByTenantIdOrderByLastSeenAtDesc(UUID tenantId);
}
