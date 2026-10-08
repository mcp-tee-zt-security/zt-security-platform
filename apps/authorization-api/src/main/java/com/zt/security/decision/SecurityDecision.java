package com.zt.security.decision;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="security_decisions") public class SecurityDecision {
    @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="request_id") UUID requestId;
    @Column(name="principal_id") String principalId;
    @Column(name="principal_type") String principalType;
    String action;
    @Column(name="resource_type")
String resourceType;
    @Column(name="resource_id") String resourceId;
    @Column(name="base_risk") double baseRisk;
    @Column(name="behavior_risk") double behaviorRisk;
    @Column(name="asset_criticality") double assetCriticality;
    @Column(name="attack_path_risk") double attackPathRisk;
    @Column(name="policy_risk") double policyRisk;
    @Column(name="composite_score") double compositeScore;
    String decision;
    String reason;
    @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(columnDefinition="jsonb") String signals="{}";
    @Column(name="created_at") Instant createdAt=
    Instant.now();
    public UUID getId(){
        return id;
    }
    public UUID getTenantId(){
        return tenantId;
    }
    public UUID getRequestId(){
        return requestId;
    }
    public String getPrincipalId(){
    return principalId;
    }
    public String getPrincipalType(){
        return principalType;
}
public String getAction(){
    return action;
}
public String getResourceType(){
return resourceType;
}
public String getResourceId(){
    return resourceId;
}
public double getBaseRisk(){
    return baseRisk;
}
public double getBehaviorRisk(){
return behaviorRisk;
}
public double getAssetCriticality(){
    return assetCriticality;
}
public double getAttackPathRisk(){
    return attackPathRisk;
}
public double getPolicyRisk(){
return policyRisk;
}
public String getReason(){
    return reason;
}
public String getSignals(){
return signals;
}
public void setId(UUID x){
    id=x;
}
public void setTenantId(UUID x){
tenantId=x;
}
public void setRequestId(UUID x){
    requestId=x;
}
public void setPrincipalId(String x){
principalId=x;
}
public void setPrincipalType(String x){
    principalType=x;
}
public void setAction(String x){
    action=x;
}
public void setResourceType(String x){
resourceType=x;
}
public void setResourceId(String x){
    resourceId=x;
}
public void setBaseRisk(double x){
baseRisk=x;
}
public void setBehaviorRisk(double x){
    behaviorRisk=x;
}
public void setAssetCriticality(double x){
assetCriticality=x;
}
public void setAttackPathRisk(double x){
    attackPathRisk=x;
}
public void setPolicyRisk(double x){
    policyRisk=x;
}
public void setCompositeScore(double x){
compositeScore=x;
}
public void setDecision(String x){
    decision=x;
}
public void setReason(String x){
reason=x;
}
public void setSignals(String x){
    signals=x;
}
public Instant getCreatedAt(){
return createdAt;
}
public double getCompositeScore(){
    return compositeScore;
}
public String getDecision(){
    return decision;
}
}
