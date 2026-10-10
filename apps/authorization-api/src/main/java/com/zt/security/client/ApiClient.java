package com.zt.security.client;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="api_clients", uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","client_id"}
))
public class ApiClient {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="workspace_id") UUID workspaceId;
    @Column(name="client_id") String clientId;
    String name;
    @Column(name="secret_hash") String secretHash;
    String status="ACTIVE";
    @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(columnDefinition="jsonb") String scopes="[]";
    Instant lastUsedAt;
    Instant expiresAt;
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
    public String getClientId(){
        return clientId;
    }
    public void setClientId(String v){
    clientId=v;
    }
    public String getName(){
        return name;
    }
    public void setName(String v){
    name=v;
    }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getSecretHash(){
        return secretHash;
    }
    public void setSecretHash(String v){
    secretHash=v;
    }
    public String getStatus(){
        return status;
    }
    public void setStatus(String v){
    status=v;
    }
    public String getScopes(){
        return scopes;
    }
    public void setScopes(String v){
    scopes=v;
    }
    public Instant getLastUsedAt(){
        return lastUsedAt;
    }
    public void setLastUsedAt(Instant v){
    lastUsedAt=v;
    }
    public Instant getExpiresAt(){
        return expiresAt;
    }
    public void setExpiresAt(Instant v){
    expiresAt=v;
    }
    public Instant getCreatedAt(){
        return createdAt;
    }
    }
