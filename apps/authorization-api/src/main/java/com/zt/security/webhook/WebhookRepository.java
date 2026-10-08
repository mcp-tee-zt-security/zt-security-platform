package com.zt.security.webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WebhookRepository extends JpaRepository<Webhook,
UUID>{
    List<Webhook> findByTenantIdAndStatus(UUID tenantId,String status);
}
