package com.zt.security.replay;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ActionReplayRepository extends JpaRepository<ActionReplay,
UUID>{
    List<ActionReplay> findByTenantIdOrderByCreatedAtDesc(UUID t);
}
