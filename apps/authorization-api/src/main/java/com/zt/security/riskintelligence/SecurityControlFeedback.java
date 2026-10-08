package com.zt.security.riskintelligence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="security_control_feedback",
       uniqueConstraints=@UniqueConstraint(name="ux_security_control_feedback_response",
                                           columnNames={
                                               "tenant_id","response_id"}
                                           ))
public class SecurityControlFeedback {
  @Id
  @GeneratedValue(strategy=GenerationType.UUID)
  private UUID id;

  @Column(name="tenant_id", nullable=false) private UUID tenantId;
  @Column(name="response_id", nullable=false) private UUID responseId;
  @Column(name="action_type", nullable=false) private String actionType;
  @Column(nullable=false) private String target;
  @Column(nullable=false) private String verification;
  @Column(name="before_risk") private Double beforeRisk;
  @Column(name="after_risk") private Double afterRisk;
  @Column(name="risk_delta") private Double riskDelta;
  @Column(name="before_deny_rate") private Double beforeDenyRate;
  @Column(name="after_deny_rate") private Double afterDenyRate;
  @Column(name="deny_rate_delta") private Double denyRateDelta;
  @Column(name="before_events", nullable=false) private int beforeEvents;
  @Column(name="after_events", nullable=false) private int afterEvents;
  @Column(name="before_risk_samples", nullable=false) private int beforeRiskSamples;
  @Column(name="after_risk_samples", nullable=false) private int afterRiskSamples;
  @Column(name="observation_hours", nullable=false) private int observationHours;
  @Column(name="observation_started_at") private Instant observationStartedAt;
  @Column(name="observation_completed_at") private Instant observationCompletedAt;
  @Column(name="evaluated_at") private Instant evaluatedAt;
  @Column(nullable=false) private double confidence;
  @Column(name="learning_summary", columnDefinition="text") private String learningSummary;
  @Column(name="created_at", nullable=false) private Instant createdAt=Instant.now();

  public UUID getId(){
      return id;
  }
  public UUID getTenantId(){
      return tenantId;
  }
  public void setTenantId(UUID v){
      tenantId=v;
  }
  public UUID getResponseId(){
      return responseId;
  }
  public void setResponseId(UUID v){
      responseId=v;
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
  public String getVerification(){
      return verification;
  }
  public void setVerification(String v){
      verification=v;
  }
  public Double getBeforeRisk(){
      return beforeRisk;
  }
  public void setBeforeRisk(Double v){
      beforeRisk=v;
  }
  public Double getAfterRisk(){
      return afterRisk;
  }
  public void setAfterRisk(Double v){
      afterRisk=v;
  }
  public Double getRiskDelta(){
      return riskDelta;
  }
  public void setRiskDelta(Double v){
      riskDelta=v;
  }
  public Double getBeforeDenyRate(){
      return beforeDenyRate;
  }
  public void setBeforeDenyRate(Double v){
      beforeDenyRate=v;
  }
  public Double getAfterDenyRate(){
      return afterDenyRate;
  }
  public void setAfterDenyRate(Double v){
      afterDenyRate=v;
  }
  public Double getDenyRateDelta(){
      return denyRateDelta;
  }
  public void setDenyRateDelta(Double v){
      denyRateDelta=v;
  }
  public int getBeforeEvents(){
      return beforeEvents;
  }
  public void setBeforeEvents(int v){
      beforeEvents=v;
  }
  public int getAfterEvents(){
      return afterEvents;
  }
  public void setAfterEvents(int v){
      afterEvents=v;
  }
  public int getBeforeRiskSamples(){
      return beforeRiskSamples;
  }
  public void setBeforeRiskSamples(int v){
      beforeRiskSamples=v;
      }
  public int getAfterRiskSamples(){
      return afterRiskSamples;
  }
  public void setAfterRiskSamples(int v){
      afterRiskSamples=v;
  }
  public int getObservationHours(){
      return observationHours;
  }
  public void setObservationHours(int v){
      observationHours=v;
  }
  public Instant getObservationStartedAt(){
      return observationStartedAt;
  }
  public void setObservationStartedAt(Instant v){
      observationStartedAt=v;
  }
  public Instant getObservationCompletedAt(){
      return observationCompletedAt;
  }
  public void setObservationCompletedAt(Instant v){
      observationCompletedAt=v;
  }
  public Instant getEvaluatedAt(){
      return evaluatedAt;
  }
  public void setEvaluatedAt(Instant v){
      evaluatedAt=v;
  }
  public double getConfidence(){
      return confidence;
  }
  public void setConfidence(double v){
      confidence=v;
  }
  public String getLearningSummary(){
      return learningSummary;
  }
  public void setLearningSummary(String v){
      learningSummary=v;
      }
  public Instant getCreatedAt(){
      return createdAt;
  }
}
