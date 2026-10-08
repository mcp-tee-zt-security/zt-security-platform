package com.zt.security.agent;
import jakarta.persistence.*;
import java.util.*;
@Entity @Table(name="agent_tools",uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","name"}
    )) public class AgentTool {
        @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    String name;
    String description;
    @Column(name="risk_level") String riskLevel="MEDIUM";
    @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(columnDefinition="jsonb") String metadata="{}";
    boolean enabled=true;
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
    public String getRiskLevel(){
        return riskLevel;
    }
    public void setRiskLevel(String x){
    riskLevel=x;
    }
    public boolean isEnabled(){
        return enabled;
    }
    public void setEnabled(boolean x){
    enabled=x;
    }
    }
