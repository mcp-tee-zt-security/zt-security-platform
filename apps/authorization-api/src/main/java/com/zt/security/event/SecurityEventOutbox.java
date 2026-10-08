package com.zt.security.event;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="security_event_outbox") public class SecurityEventOutbox {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="workspace_id") UUID workspaceId;
    @Column(name="event_type") String eventType;
    @Column(name="aggregate_type") String aggregateType;
    @Column(name="aggregate_id") String aggregateId;
    @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(columnDefinition="jsonb") String payload;
    String status="PENDING";
    int attempts=0;
    @Column(name="available_at") Instant availableAt=Instant.now();
    @Column(name="published_at") Instant publishedAt;
    Instant createdAt=Instant.now();
    public UUID getId(){
        return id;
    }
    public void setId(UUID v){
        id=v;
    }
    public UUID getTenantId(){
        return tenantId;
        }
        public void setTenantId(UUID v){
            tenantId=v;
        }
        public UUID getWorkspaceId(){
        return workspaceId;
        }
        public void setWorkspaceId(UUID v){
            workspaceId=v;
    }
    public String getEventType(){
        return eventType;
    }
    public void setEventType(String v){
    eventType=v;
    }
    public String getAggregateType(){
        return aggregateType;
    }
    public void setAggregateType(String v){
    aggregateType=v;
    }
    public String getAggregateId(){
        return aggregateId;
    }
    public void setAggregateId(String v){
    aggregateId=v;
    }
    public String getPayload(){
        return payload;
    }
    public void setPayload(String v){
    payload=v;
    }
    public String getStatus(){
        return status;
    }
    public void setStatus(String v){
    status=v;
    }
    public int getAttempts(){
        return attempts;
    }
    public void setAttempts(int v){
    attempts=v;
    }
    public Instant getAvailableAt(){
        return availableAt;
    }
    public void setAvailableAt(Instant v){
    availableAt=v;
    }
    }
