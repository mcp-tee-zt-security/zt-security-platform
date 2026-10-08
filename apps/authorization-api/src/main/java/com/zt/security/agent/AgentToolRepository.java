package com.zt.security.agent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AgentToolRepository extends JpaRepository<AgentTool,
UUID>{
    List<AgentTool> findByTenantId(UUID t);
}
