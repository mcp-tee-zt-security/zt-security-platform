package com.zt.security.agent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AgentTaskRepository extends JpaRepository<AgentTask,
UUID>{
    List<AgentTask> findByTenantId(UUID tenant);
    Optional<AgentTask> findByTenantIdAndExternalTaskId(UUID t,String e);
    List<AgentTask> findByTenantIdAndAgentIdentityId(UUID t,UUID a);
    }
