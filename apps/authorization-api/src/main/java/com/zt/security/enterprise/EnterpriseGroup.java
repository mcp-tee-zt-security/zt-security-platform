package com.zt.security.enterprise;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="enterprise_groups", uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","display_name"}
    )) public class EnterpriseGroup {
        @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="external_id") String externalId;
    @Column(name="display_name") String displayName;
    boolean active=true;
    @Column(columnDefinition=
    "jsonb") String attributes="{}";
    @Column(name="created_at") Instant createdAt=Instant.now();
    @Column(name="updated_at") Instant updatedAt=
    Instant.now();
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
    public String getDisplayName(){
        return displayName;
    }
    public void setDisplayName(String v){
        displayName=v;
        }
        public boolean isActive(){
            return active;
        }
        public void setActive(boolean v){
        active=v;
        }
        public String getAttributes(){
            return attributes;
        }
        public void setAttributes(String v){
        attributes=v;
        }
        }
