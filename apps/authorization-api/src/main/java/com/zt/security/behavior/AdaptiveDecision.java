package com.zt.security.behavior;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="agent_adaptive_decisions")
public class AdaptiveDecision {
 @Id UUID id;
 @Column(name="tenant_id") UUID tenantId;
 @Column(name="agent_external_id") String agentExternalId;
 @Column(name="request_id") UUID requestId;
 @Column(name="peer_group") String peerGroup;
 @Column(name="behavior_score") double behaviorScore;
 @Column(name="baseline_score") double baselineScore;
 @Column(name="sequence_score") double sequenceScore;
 @Column(name="peer_score") double peerScore;
 String decision;
 @Column(columnDefinition="jsonb") String signals="[]";
 @Column(columnDefinition="jsonb") String evidence="{}";
 @Column(name="created_at") Instant createdAt=Instant.now();
 public UUID getId(){
     return id;
 }
 public void setId(UUID v){
     id=v;
 }
 public void setTenantId(UUID v){
     tenantId=v;
     }
     public void setAgentExternalId(String v){
         agentExternalId=v;
 }
 public void setRequestId(UUID v){
     requestId=v;
 }
 public void setPeerGroup(String v){
     peerGroup=v;
     }
     public void setBehaviorScore(double v){
         behaviorScore=v;
     }
 public void setBaselineScore(double v){
     baselineScore=v;
 }
 public void setSequenceScore(double v){
     sequenceScore=v;
 }
 public void setPeerScore(double v){
     peerScore=v;
     }
     public void setDecision(String v){
         decision=v;
     }
     public void setSignals(String v){
     signals=v;
     }
     public void setEvidence(String v){
         evidence=v;
     }
     public String getAgentExternalId(){
     return agentExternalId;
     }
     public double getBehaviorScore(){
         return behaviorScore;
 }
 public Instant getCreatedAt(){
     return createdAt;
 }
}
