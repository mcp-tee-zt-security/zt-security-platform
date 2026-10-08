package com.zt.security.rbac;
import jakarta.persistence.*;
import java.util.*;
@Entity @Table(name="roles",uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","name"}
    )) public class Role {
        @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    String name;
    String description;
    @Column(columnDefinition="jsonb") String permissions="[]";
    public UUID getId(){
        return id;
        }
        public UUID getTenantId(){
            return tenantId;
        }
        public void setTenantId(UUID x){
        tenantId=x;
        }
        public String getName(){
            return name;
        }
        public void setName(String x){
        name=x;
        }
        public String getDescription(){
            return description;
        }
        public void setDescription(String x){
        description=x;
        }
        public String getPermissions(){
            return permissions;
        }
        public void setPermissions(String x){
        permissions=x==null?"[]":x;
        }
        }
