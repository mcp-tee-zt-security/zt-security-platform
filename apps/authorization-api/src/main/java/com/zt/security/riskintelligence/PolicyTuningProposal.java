package com.zt.security.riskintelligence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="policy_tuning_proposals")
public class PolicyTuningProposal {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
  @Column(name="tenant_id", nullable=false) private UUID tenantId;
  @Column(name="profile_id", nullable=false) private UUID profileId;
  @Column(name="base_policy_id", nullable=false) private UUID basePolicyId;
  @Column(name="base_policy_version", nullable=false) private int basePolicyVersion;
  @Column(name="proposed_priority", nullable=false) private int proposedPriority;
  @Column(name="priority_delta", nullable=false) private int priorityDelta;
  @Column(nullable=false) private String status="PENDING";
  @Column(name="reason", nullable=false, columnDefinition="text") private String reason;
  @Column(name="simulation_status", nullable=false) private String simulationStatus="NOT_RUN";
  @Column(name="simulation_result", columnDefinition="jsonb") private String simulationResult;
  @Column(name="approved_policy_id") private UUID approvedPolicyId;
  @Column(name="requested_by") private String requestedBy;
  @Column(name="approved_by") private String approvedBy;
  @Column(name="created_at", nullable=false) private Instant createdAt=Instant.now();
  @Column(name="updated_at", nullable=false) private Instant updatedAt=Instant.now();
  @Column(name="approved_at") private Instant approvedAt;

  public UUID getId(){
      return id;
  }
  public UUID getTenantId(){
      return tenantId;
  }
  public void setTenantId(UUID v){
      tenantId=v;
  }
  public UUID getProfileId(){
      return profileId;
  }
  public void setProfileId(UUID v){
      profileId=v;
  }
  public UUID getBasePolicyId(){
      return basePolicyId;
  }
  public void setBasePolicyId(UUID v){
      basePolicyId=v;
  }
  public int getBasePolicyVersion(){
      return basePolicyVersion;
  }
  public void setBasePolicyVersion(int v){
      basePolicyVersion=v;
      }
  public int getProposedPriority(){
      return proposedPriority;
  }
  public void setProposedPriority(int v){
      proposedPriority=v;
  }
  public int getPriorityDelta(){
      return priorityDelta;
  }
  public void setPriorityDelta(int v){
      priorityDelta=v;
  }
  public String getStatus(){
      return status;
  }
  public void setStatus(String v){
      status=v;
  }
  public String getReason(){
      return reason;
  }
  public void setReason(String v){
      reason=v;
  }
  public String getSimulationStatus(){
      return simulationStatus;
  }
  public void setSimulationStatus(String v){
      simulationStatus=v;
      }
  public String getSimulationResult(){
      return simulationResult;
  }
  public void setSimulationResult(String v){
      simulationResult=v;
      }
  public UUID getApprovedPolicyId(){
      return approvedPolicyId;
  }
  public void setApprovedPolicyId(UUID v){
      approvedPolicyId=v;
      }
  public String getRequestedBy(){
      return requestedBy;
  }
  public void setRequestedBy(String v){
      requestedBy=v;
  }
  public String getApprovedBy(){
      return approvedBy;
  }
  public void setApprovedBy(String v){
      approvedBy=v;
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
  public Instant getApprovedAt(){
      return approvedAt;
  }
  public void setApprovedAt(Instant v){
      approvedAt=v;
  }
}
