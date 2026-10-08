package com.zt.security.replay;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="action_replays") public class ActionReplay {
    @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="source_audit_id") UUID sourceAuditId;
    @Column(name="policy_set") String policySet;
    String decision;
    String reason;
    @Column(name="created_at") Instant createdAt=Instant.now();
    public UUID getId(){
        return id;
        }
        public void setTenantId(UUID x){
            tenantId=x;
        }
        public void setSourceAuditId(UUID x){
        sourceAuditId=x;
        }
        public void setPolicySet(String x){
            policySet=x;
        }
        public void setDecision(String x){
        decision=x;
        }
        public void setReason(String x){
            reason=x;
        }
        public String getDecision(){
        return decision;
        }
        }
