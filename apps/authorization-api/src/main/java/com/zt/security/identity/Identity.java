package com.zt.security.identity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="identities",uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","external_id"}
    )) public class Identity {
        @Id UUID id;
        @Column(name="tenant_id") UUID tenantId;
    @Column(name="external_id") String externalId;
    @Column(name="identity_type") String identityType;
    String name;
    @Column(columnDefinition="jsonb") String attributes="{}";
    String status="ACTIVE";
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
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
        public String getExternalId(){
        return externalId;
        }
        public void setExternalId(String v){
            externalId=v;
        }
    public String getIdentityType(){
        return identityType;
    }
    public void setIdentityType(String v){
        identityType=v;
        }
        public String getName(){
            return name;
        }
        public void setName(String v){
        name=v;
        }
        public String getAttributes(){
            return attributes;
        }
        public void setAttributes(String v){
        attributes=v;
        }
        }
