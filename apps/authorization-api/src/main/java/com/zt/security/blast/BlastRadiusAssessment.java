package com.zt.security.blast;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="blast_radius_assessments")
public class BlastRadiusAssessment {
    @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="source_node") String sourceNode;
    @Column(name="source_type") String sourceType="COMPROMISED_AGENT";
    @Column(name="risk_score") double riskScore;
    String severity="LOW";
    @Column(name="impacted_assets") int impactedAssets;
    @Column(name="critical_assets") int criticalAssets;
    @Column(name="restricted_assets") int restrictedAssets;
    @Column(name="recommended_actions") int recommendedActions;
    @Column(name="generated_at") Instant generatedAt=Instant.now();
    @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(columnDefinition="jsonb") String result="{}";
    public UUID getId(){
        return id;
    }
    public UUID getTenantId(){
        return tenantId;
    }
    public void setTenantId(UUID x){
        tenantId=x;
    }
    public String getSourceNode(){
        return sourceNode;
    }
    public void setSourceNode(String x){
        sourceNode=x;
    }
    public String getSourceType(){
        return sourceType;
    }
    public void setSourceType(String x){
        sourceType=x;
    }
    public double getRiskScore(){
        return riskScore;
    }
    public void setRiskScore(double x){
        riskScore=x;
    }
    public String getSeverity(){
        return severity;
    }
    public void setSeverity(String x){
        severity=x;
    }
    public int getImpactedAssets(){
        return impactedAssets;
    }
    public void setImpactedAssets(int x){
        impactedAssets=x;
    }
    public int getCriticalAssets(){
        return criticalAssets;
    }
    public void setCriticalAssets(int x){
        criticalAssets=x;
    }
    public int getRestrictedAssets(){
        return restrictedAssets;
    }
    public void setRestrictedAssets(int x){
        restrictedAssets=x;
        }
    public int getRecommendedActions(){
        return recommendedActions;
    }
    public void setRecommendedActions(int x){
        recommendedActions=x;
        }
    public Instant getGeneratedAt(){
        return generatedAt;
    }
    public String getResult(){
        return result;
        }
        public void setResult(String x){
            result=x;
        }
}
