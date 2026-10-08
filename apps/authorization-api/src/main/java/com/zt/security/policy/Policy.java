package com.zt.security.policy;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="policies",uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","name","version"}
))
public class Policy {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    String name;
    int version;
    String status="DRAFT";
    int priority=100;
    String effect;
    @Column(name="policy_text",columnDefinition="text") String policyText;
    @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(name="policy_json",columnDefinition="jsonb") String policyJson;
    @Column(name="created_by") String createdBy;
    @Column(name="created_at") Instant createdAt=Instant.now();
    @Column(name="updated_at") Instant updatedAt=Instant.now();
    @Column(name="workspace_id") UUID workspaceId;
    public UUID getWorkspaceId(){
        return workspaceId;
    }
    public void setWorkspaceId(UUID x){
        workspaceId=x;
        }
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
        public String getName(){
            return name;
        }
        public void setName(String v){
        name=v;
        }
        public int getVersion(){
            return version;
        }
        public void setVersion(int v){
        version=v;
        }
        public String getStatus(){
            return status;
        }
        public void setStatus(String v){
        status=v;
        }
        public int getPriority(){
            return priority;
        }
        public void setPriority(int v){
        priority=v;
        }
        public String getEffect(){
            return effect;
        }
        public void setEffect(String v){
        effect=v;
        }
        public String getPolicyText(){
            return policyText;
        }
        public void setPolicyText(String v){
        policyText=v;
        }
        public String getPolicyJson(){
            return policyJson;
        }
        public void setPolicyJson(String v){
        policyJson=v;
        }
        public String getCreatedBy(){
            return createdBy;
        }
        public void setCreatedBy(String v){
        createdBy=v;
        }
        public Instant getCreatedAt(){
            return createdAt;
        }
        public Instant getUpdatedAt(){
        return updatedAt;
        }
        public void setUpdatedAt(Instant v){
            updatedAt=v;
        }
        }
