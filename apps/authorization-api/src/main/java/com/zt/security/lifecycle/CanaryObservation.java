package com.zt.security.lifecycle;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="canary_observations") public class CanaryObservation {
 @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
 @Column(name="tenant_id",
 nullable=false) UUID tenantId;
 @Column(name="deployment_id",nullable=false) UUID deploymentId;
 @Column(name="observed_at",nullable=false) Instant observedAt=Instant.now();
 @Column(name="canary_events") int canaryEvents;
 @Column(name="baseline_events") int baselineEvents;
 @Column(name="canary_deny_rate") double canaryDenyRate;
 @Column(name="baseline_deny_rate") double baselineDenyRate;
 @Column(name="canary_high_risk_rate") double canaryHighRiskRate;
 @Column(name="baseline_high_risk_rate")
double baselineHighRiskRate;
 @Column(name="canary_avg_risk") double canaryAvgRisk;
 @Column(name="baseline_avg_risk") double baselineAvgRisk;
 @Column(name="risk_delta") double riskDelta;
 @Column(name="decision_delta") double decisionDelta;
 String status;
 @Column(columnDefinition="text") String reason;
 public UUID getId(){
     return id;
 }
 public void setTenantId(UUID x){
     tenantId=x;
 }
 public void setDeploymentId(UUID x){
     deploymentId=x;
 }
 public UUID getTenantId(){
 return tenantId;
 }
 public UUID getDeploymentId(){
     return deploymentId;
 }
 public Instant getObservedAt(){
     return observedAt;
 }
 public void setObservedAt(Instant x){
     observedAt=x;
     }
     public int getCanaryEvents(){
         return canaryEvents;
     }
     public void setCanaryEvents(int x){
     canaryEvents=x;
     }
     public int getBaselineEvents(){
         return baselineEvents;
 }
 public void setBaselineEvents(int x){
     baselineEvents=x;
 }
 public double getCanaryDenyRate(){
     return canaryDenyRate;
 }
 public void setCanaryDenyRate(double x){
     canaryDenyRate=x;
     }
     public double getBaselineDenyRate(){
         return baselineDenyRate;
 }
 public void setBaselineDenyRate(double x){
     baselineDenyRate=x;
 }
 public double getCanaryHighRiskRate(){
     return canaryHighRiskRate;
 }
 public void setCanaryHighRiskRate(double x){
     canaryHighRiskRate=x;
     }
     public double getBaselineHighRiskRate(){
         return baselineHighRiskRate;
 }
 public void setBaselineHighRiskRate(double x){
     baselineHighRiskRate=x;
 }
 public double getCanaryAvgRisk(){
     return canaryAvgRisk;
 }
 public void setCanaryAvgRisk(double x){
     canaryAvgRisk=x;
     }
     public double getBaselineAvgRisk(){
         return baselineAvgRisk;
 }
 public void setBaselineAvgRisk(double x){
     baselineAvgRisk=x;
 }
 public double getRiskDelta(){
     return riskDelta;
 }
 public void setRiskDelta(double x){
     riskDelta=x;
     }
     public double getDecisionDelta(){
         return decisionDelta;
     }
     public void setDecisionDelta(double x){
     decisionDelta=x;
     }
     public String getStatus(){
         return status;
     }
     public void setStatus(String x){
     status=x;
     }
     public String getReason(){
         return reason;
     }
     public void setReason(String x){
     reason=x;
     }
}
