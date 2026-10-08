package com.zt.security.webhook;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="webhooks") public class Webhook {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="workspace_id") UUID workspaceId;
    String name,url,secret,status="ACTIVE";
    @Column(name="event_types",columnDefinition="jsonb") String eventTypes="[]";
    Instant createdAt=Instant.now(),updatedAt=Instant.now();
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
    public String getName(){
        return name;
    }
    public void setName(String v){
    name=v;
    }
    public String getUrl(){
        return url;
    }
    public void setUrl(String v){
    url=v;
    }
    public String getSecret(){
        return secret;
    }
    public void setSecret(String v){
    secret=v;
    }
    public String getStatus(){
        return status;
    }
    public void setStatus(String v){
    status=v;
    }
    public String getEventTypes(){
        return eventTypes;
    }
    public void setEventTypes(String v){
    eventTypes=v;
    }
    }
