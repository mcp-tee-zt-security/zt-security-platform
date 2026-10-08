package com.zt.security.behavior;
import org.springframework.data.jpa.repository.*;
import java.time.*;
import java.util.*;
interface ProfileRepo extends JpaRepository<AgentBehaviorProfile,UUID>{
    Optional<AgentBehaviorProfile> findByTenantIdAndAgentExternalId(UUID t,
    String a);
    }
interface EventRepo extends JpaRepository<AgentBehaviorEvent,UUID>{
 long countByTenantIdAndAgentExternalIdAndCreatedAtAfter(UUID t,String a,Instant x);
 long countByTenantIdAndAgentExternalIdAndDecisionAndCreatedAtAfter(UUID t,String a,String d,Instant x);
 List<AgentBehaviorEvent> findTop100ByTenantIdAndAgentExternalIdOrderByCreatedAtDesc(UUID t,String a);
 List<AgentBehaviorEvent> findByTenantIdAndAgentExternalIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID t,
 String a,Instant x);
 List<AgentBehaviorEvent> findByTenantIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID t,Instant x);
 List<AgentBehaviorEvent> findByTenantIdAndAgentExternalIdInAndCreatedAtAfterOrderByCreatedAtAsc(UUID t,
 Collection<String> agents,Instant x);
}
interface AnomalyRepo extends JpaRepository<AgentAnomaly,UUID>{
    List<AgentAnomaly>
findByTenantIdAndAgentExternalIdOrderByCreatedAtDesc(UUID t,
    String agent);
    List<AgentAnomaly> findByTenantIdOrderByCreatedAtDesc(UUID t);
}
