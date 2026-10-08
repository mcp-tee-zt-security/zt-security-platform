package com.zt.security.rbac;
import jakarta.persistence.*;
import java.util.*;
@Entity @Table(name="role_bindings",uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","identity_id","role_id"}
    )) public class RoleBinding {
        @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="identity_id") UUID identityId;
    @Column(name="role_id") UUID roleId;
    @Column(name="workspace_id") UUID workspaceId;
    public UUID getId(){
        return id;
    }
    public UUID getTenantId(){
        return tenantId;
    }
    public void setTenantId(UUID x){
        tenantId=x;
    }
    public UUID getIdentityId(){
    return identityId;
    }
    public void setIdentityId(UUID x){
        identityId=x;
    }
    public UUID getRoleId(){
    return roleId;
    }
    public void setRoleId(UUID x){
        roleId=x;
    }
    public UUID getWorkspaceId(){
    return workspaceId;
    }
    public void setWorkspaceId(UUID x){
        workspaceId=x;
}
}
