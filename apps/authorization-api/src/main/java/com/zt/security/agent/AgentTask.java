package com.zt.security.agent;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="agent_tasks",uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","external_task_id"}
    )) public class AgentTask {
        @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="agent_identity_id") UUID agentIdentityId;
    @Column(name="external_task_id") String externalTaskId;
    String purpose;
    String status="ACTIVE";
    @Column(name="expires_at") Instant expiresAt;
    @Column(columnDefinition=
    "jsonb") String metadata="{}";
    @Column(name="created_at") Instant createdAt=Instant.now();
    public UUID getId(){
        return id;
        }
        public UUID getTenantId(){
            return tenantId;
        }
        public void setTenantId(UUID x){
        tenantId=x;
        }
        public UUID getAgentIdentityId(){
            return agentIdentityId;
        }
    public void setAgentIdentityId(UUID x){
        agentIdentityId=x;
    }
    public String getExternalTaskId(){
        return externalTaskId;
        }
        public void setExternalTaskId(String x){
            externalTaskId=x;
    }
    public String getPurpose(){
        return purpose;
    }
    public void setPurpose(String x){
    purpose=x;
    }
    public String getStatus(){
        return status;
    }
    public void setStatus(String x){
    status=x;
    }
    public Instant getExpiresAt(){
        return expiresAt;
    }
    public void setExpiresAt(Instant x){
    expiresAt=x;
    }
    public String getMetadata(){
        return metadata;
    }
    public void setMetadata(String x){
    metadata=x;
    }
    }
