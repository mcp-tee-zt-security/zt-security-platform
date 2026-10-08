package com.zt.security.event;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
@Service public class SecurityEventService {
    final SecurityEventOutboxRepository repo;
    SecurityEventService(SecurityEventOutboxRepository r){
        repo=r;
        }
        public UUID enqueue(UUID tenant,UUID workspace,String type,String aggregateType,
    String aggregateId,String payload){
        var e=new SecurityEventOutbox();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenant);
        e.setWorkspaceId(workspace);
        e.setEventType(type);
        e.setAggregateType(aggregateType);
        e.setAggregateId(aggregateId);
        e.setPayload(payload==null?"{}":payload);
        return repo.save(e).getId();
        }
        public List<SecurityEventOutbox> pending(){
        return repo.findTop100ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc("PENDING",
        Instant.now());
        }
        }
