package com.zt.security.riskintelligence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="security_control_effectiveness_profiles",
       uniqueConstraints=@UniqueConstraint(name="ux_effectiveness_profile_scope",
                                           columnNames={
                                               "tenant_id","action_type","target"}
                                           ))
public class SecurityControlEffectivenessProfile {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
  @Column(name="tenant_id", nullable=false) private UUID tenantId;
  @Column(name="action_type", nullable=false) private String actionType;
  @Column(nullable=false) private String target;
  @Column(name="evidence_count", nullable=false) private int evidenceCount;
  @Column(name="effective_count", nullable=false) private int effectiveCount;
  @Column(name="ineffective_count", nullable=false) private int ineffectiveCount;
  @Column(name="neutral_count", nullable=false) private int neutralCount;
  @Column(name="effectiveness_score", nullable=false) private double effectivenessScore;
  @Column(nullable=false) private double confidence;
  @Column(name="last_verification") private Instant lastVerification;
  @Column(name="updated_at", nullable=false) private Instant updatedAt=Instant.now();

  public UUID getId(){
      return id;
  }
  public UUID getTenantId(){
      return tenantId;
  }
  public void setTenantId(UUID v){
      tenantId=v;
  }
  public String getActionType(){
      return actionType;
  }
  public void setActionType(String v){
      actionType=v;
  }
  public String getTarget(){
      return target;
  }
  public void setTarget(String v){
      target=v;
  }
  public int getEvidenceCount(){
      return evidenceCount;
  }
  public void setEvidenceCount(int v){
      evidenceCount=v;
  }
  public int getEffectiveCount(){
      return effectiveCount;
  }
  public void setEffectiveCount(int v){
      effectiveCount=v;
  }
  public int getIneffectiveCount(){
      return ineffectiveCount;
  }
  public void setIneffectiveCount(int v){
      ineffectiveCount=v;
  }
  public int getNeutralCount(){
      return neutralCount;
  }
  public void setNeutralCount(int v){
      neutralCount=v;
  }
  public double getEffectivenessScore(){
      return effectivenessScore;
  }
  public void setEffectivenessScore(double v){
      effectivenessScore=v;
      }
  public double getConfidence(){
      return confidence;
  }
  public void setConfidence(double v){
      confidence=v;
  }
  public Instant getLastVerification(){
      return lastVerification;
  }
  public void setLastVerification(Instant v){
      lastVerification=v;
      }
  public Instant getUpdatedAt(){
      return updatedAt;
  }
  public void setUpdatedAt(Instant v){
      updatedAt=v;
  }
}
