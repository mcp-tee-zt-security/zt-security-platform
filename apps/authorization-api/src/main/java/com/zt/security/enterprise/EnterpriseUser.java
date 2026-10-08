package com.zt.security.enterprise;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="enterprise_users", uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","user_name"}
))
public class EnterpriseUser {
    @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="external_id") String externalId;
    @Column(name="user_name") String userName;
    @Column(name="display_name") String displayName;
    String email;
    boolean active=true;
    String source="LOCAL";
    @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(columnDefinition="jsonb") String attributes="{}";
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
 public String getUserName(){
     return userName;
 }
 public void setUserName(String v){
     userName=v;
     }
     public String getDisplayName(){
         return displayName;
     }
     public void setDisplayName(String v){
     displayName=v;
     }
     public String getEmail(){
         return email;
     }
     public void setEmail(String v){
     email=v;
     }
     public boolean isActive(){
         return active;
     }
     public void setActive(boolean v){
     active=v;
     }
     public String getSource(){
         return source;
     }
     public void setSource(String v){
     source=v;
     }
     public String getAttributes(){
         return attributes;
     }
     public void setAttributes(String v){
     attributes=v;
     }
     public Instant getCreatedAt(){
         return createdAt;
     }
     public Instant getUpdatedAt(){
     return updatedAt;
     }
     public void touch(){
         updatedAt=Instant.now();
     }
     }

