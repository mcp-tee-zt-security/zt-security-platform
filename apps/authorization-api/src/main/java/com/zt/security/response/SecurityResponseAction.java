package com.zt.security.response;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="security_response_actions") public class SecurityResponseAction {
 @Id UUID id=UUID.randomUUID();
 @Column(name="tenant_id") UUID tenantId;
 @Column(name="assessment_id") UUID assessmentId;
 @Column(name="action_type") String actionType;
 String target;
 String priority="MEDIUM";
 @Column(columnDefinition="text") String reason;
 @Column(name="requested_by") String requestedBy;
 @Column(name="approved_by") String approvedBy;
 String status="PENDING";
 @Column(name="requires_approval") boolean requiresApproval=true;
 @Column(name="execution_result",columnDefinition="jsonb") String executionResult="{}";
 @Column(name="created_at") Instant createdAt=Instant.now();
 @Column(name="approved_at") Instant approvedAt;
 @Column(name="executed_at") Instant executedAt;
 public UUID getId(){
     return id;
 }
 public UUID getTenantId(){
     return tenantId;
 }
 public void setTenantId(UUID v){
     tenantId=v;
 }
 public UUID getAssessmentId(){
 return assessmentId;
 }
 public void setAssessmentId(UUID v){
     assessmentId=v;
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
 public String getPriority(){
     return priority;
 }
 public void setPriority(String v){
 priority=v;
 }
 public String getReason(){
     return reason;
 }
 public void setReason(String v){
 reason=v;
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
 public String getStatus(){
     return status;
 }
 public void setStatus(String v){
 status=v;
 }
 public boolean isRequiresApproval(){
     return requiresApproval;
 }
 public void setRequiresApproval(boolean v){
     requiresApproval=v;
 }
 public String getExecutionResult(){
 return executionResult;
 }
 public void setExecutionResult(String v){
     executionResult=v;
 }
 public Instant getCreatedAt(){
     return createdAt;
 }
 public Instant getApprovedAt(){
 return approvedAt;
 }
 public void setApprovedAt(Instant v){
     approvedAt=v;
 }
 public Instant getExecutedAt(){
     return executedAt;
 }
 public void setExecutedAt(Instant v){
 executedAt=v;
 }
}
