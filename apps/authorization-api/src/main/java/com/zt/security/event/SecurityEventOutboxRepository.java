package com.zt.security.event;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SecurityEventOutboxRepository extends JpaRepository<SecurityEventOutbox,
UUID>{
    List<SecurityEventOutbox> findTop100ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(String status,
    Instant now);
    }
