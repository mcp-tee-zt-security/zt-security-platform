package com.zt.security.idempotency;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="idempotency_keys",uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","idempotency_key"}
))
public class IdempotencyKey {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="idempotency_key") String key;
    @Column(name="request_hash") String requestHash;
    @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(name="response_json",columnDefinition="jsonb") String responseJson;
    String status="IN_PROGRESS";
    @Column(name="created_at") Instant createdAt=Instant.now();
    @Column(name="expires_at") Instant expiresAt=Instant.now().plusSeconds(86400);
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
        public String getKey(){
        return key;
        }
        public void setKey(String v){
            key=v;
        }
        public String getRequestHash(){
        return requestHash;
        }
        public void setRequestHash(String v){
            requestHash=v;
    }
    public String getResponseJson(){
        return responseJson;
    }
    public void setResponseJson(String v){
    responseJson=v;
    }
    public void setStatus(String v){
        status=v;
    }
    public String getStatus(){
    return status;
    }
    public Instant getExpiresAt(){
        return expiresAt;
    }
    }
