package com.zt.security.decision;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="security_assets", uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","resource_type","resource_id"}
    ))
public class SecurityAsset {
    @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="resource_type") String resourceType;
    @Column(name="resource_id") String resourceId;
    int criticality=50;
    @Column(name="data_classification") String dataClassification="INTERNAL";
    String owner;
    @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(columnDefinition="jsonb") String metadata="{}";
    @Column(name="updated_at") Instant updatedAt=
    Instant.now();
    public UUID getId(){
        return id;
    }
    public UUID getTenantId(){
        return tenantId;
    }
    public void setTenantId(UUID x){
        tenantId=x;
    }
    public String getResourceType(){
    return resourceType;
    }
    public void setResourceType(String x){
        resourceType=x;
}
public String getResourceId(){
    return resourceId;
}
public void setResourceId(String x){
resourceId=x;
}
public int getCriticality(){
    return criticality;
}
public void setCriticality(int x){
criticality=x;
}
public String getDataClassification(){
    return dataClassification;
}
public void setDataClassification(String x){
    dataClassification=x;
}
public String getOwner(){
return owner;
}
public void setOwner(String x){
    owner=x;
}
public String getMetadata(){
return metadata;
}
public void setMetadata(String x){
    metadata=x;
}
}
