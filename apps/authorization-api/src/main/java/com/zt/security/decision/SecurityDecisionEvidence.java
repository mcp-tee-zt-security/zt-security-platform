package com.zt.security.decision;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="security_decision_evidence") public class SecurityDecisionEvidence {
 @Id UUID id=UUID.randomUUID();
 @Column(name="tenant_id") UUID tenantId;
 @Column(name="decision_id") UUID decisionId;
 String category;
 String signal;
 double weight;
 double score;
 String explanation;
 @Column(name="created_at") Instant createdAt=Instant.now();
 public UUID getId(){
     return id;
 }
 public UUID getDecisionId(){
     return decisionId;
 }
 public String getCategory(){
     return category;
 }
 public String getSignal(){
 return signal;
 }
 public double getWeight(){
     return weight;
 }
 public double getScore(){
 return score;
 }
 public String getExplanation(){
     return explanation;
 }
 public void setTenantId(UUID x){
     tenantId=x;
 }
 public void setDecisionId(UUID x){
     decisionId=x;
     }
     public void setCategory(String x){
         category=x;
     }
     public void setSignal(String x){
     signal=x;
     }
     public void setWeight(double x){
         weight=x;
     }
     public void setScore(double x){
     score=x;
     }
     public void setExplanation(String x){
         explanation=x;
     }
}
