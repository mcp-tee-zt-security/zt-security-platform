package com.zt.security.workspace;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspaces", uniqueConstraints = @UniqueConstraint(columnNames = {
    "tenant_id", "slug"}
))
public class Workspace {
    @Id UUID id = UUID.randomUUID();
    @Column(name="tenant_id", nullable=false) UUID tenantId;
    @Column(nullable=false) String slug;
    @Column(nullable=false) String name;
    @Column(nullable=false) String status = "ACTIVE";
    @Column(name="created_at", nullable=false) Instant createdAt = Instant.now();
    @Column(name="updated_at", nullable=false) Instant updatedAt = Instant.now();

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
    public String getSlug(){
        return slug;
    }
    public void setSlug(String v){
        slug=v;
    }
    public String getName(){
        return name;
    }
    public void setName(String v){
        name=v;
    }
    public String getStatus(){
        return status;
    }
    public void setStatus(String v){
        status=v;
    }
    public Instant getCreatedAt(){
        return createdAt;
    }
    public Instant getUpdatedAt(){
        return updatedAt;
    }
}
