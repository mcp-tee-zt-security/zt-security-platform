package com.zt.security.behavior;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="agent_behavior_profiles",uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","agent_external_id"}
    )) public class AgentBehaviorProfile {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="agent_external_id") String agentExternalId;
    @Column(name="max_actions_per_minute") int maxActionsPerMinute=60;
    @Column(name="max_risk_score")
double maxRiskScore=
    70;
    @Column(name="max_amount") Double maxAmount;
    @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(name="allowed_actions",
    columnDefinition="jsonb") String allowedActions="[]";
    @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(name="allowed_resource_types",
    columnDefinition="jsonb") String allowedResourceTypes="[]";
    @Column(name="deny_burst_threshold") int
denyBurstThreshold=
    5;
    @Column(name="peer_group") String peerGroup="default";
    @Column(name="baseline_window_days") int baselineWindowDays=
    7;
    @Column(name="adaptive_step_up_score") double adaptiveStepUpScore=65;
@Column(name="adaptive_deny_score") double adaptiveDenyScore=
    85;
    boolean enabled=true;
    @Column(name="created_at") Instant createdAt=Instant.now();
    @Column(name="updated_at") Instant updatedAt=Instant.now();
    public UUID getId(){
        return id;
        }
        public void setId(UUID x){
            id=x;
        }
        public void setTenantId(UUID x){
        tenantId=x;
        }
        public String getAgentExternalId(){
            return agentExternalId;
    }
    public void setAgentExternalId(String x){
        agentExternalId=x;
    }
    public int getMaxActionsPerMinute(){
    return maxActionsPerMinute;
    }
    public void setMaxActionsPerMinute(int x){
    maxActionsPerMinute=x;
    }
    public double getMaxRiskScore(){
        return maxRiskScore;
}
public void setMaxRiskScore(double x){
    maxRiskScore=x;
}
public Double getMaxAmount(){
return maxAmount;
}
public void setMaxAmount(Double x){
    maxAmount=x;
}
public String getAllowedActions(){
return allowedActions;
}
public void setAllowedActions(String x){
    allowedActions=x;
}
public String getAllowedResourceTypes(){
    return allowedResourceTypes;
}
public void setAllowedResourceTypes(String x){
    allowedResourceTypes=x;
}
public int getDenyBurstThreshold(){
    return denyBurstThreshold;
}
public void setDenyBurstThreshold(int x){
denyBurstThreshold=x;
}
public boolean isEnabled(){
    return enabled;
}
public void setEnabled(boolean x){
enabled=x;
}
public String getPeerGroup(){
    return peerGroup;
}
public void setPeerGroup(String x){
peerGroup=x;
}
public int getBaselineWindowDays(){
    return baselineWindowDays;
}
public void setBaselineWindowDays(int x){
    baselineWindowDays=x;
}
public double getAdaptiveStepUpScore(){
return adaptiveStepUpScore;
}
public void setAdaptiveStepUpScore(double x){
adaptiveStepUpScore=x;
}
public double getAdaptiveDenyScore(){
    return adaptiveDenyScore;
}
public void setAdaptiveDenyScore(double x){
    adaptiveDenyScore=x;
}
}
