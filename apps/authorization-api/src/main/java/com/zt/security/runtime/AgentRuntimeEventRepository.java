package com.zt.security.runtime;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;

public interface AgentRuntimeEventRepository extends JpaRepository<AgentRuntimeEvent,UUID>{
 List<AgentRuntimeEvent> findTop100ByTenantIdAndSessionIdOrderByCreatedAtDesc(UUID tenantId,UUID sessionId);
 List<AgentRuntimeEvent> findTop10000ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
 long countByTenantIdAndSessionId(UUID tenantId,UUID sessionId);
List<AgentRuntimeEvent> findByTenantIdAndAgentExternalIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
     UUID tenantId, String agentExternalId, Instant from, Instant to);
}
