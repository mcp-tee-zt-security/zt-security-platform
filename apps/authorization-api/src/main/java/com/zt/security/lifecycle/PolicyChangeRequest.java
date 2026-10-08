package com.zt.security.lifecycle;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="policy_change_requests") public class PolicyChangeRequest {
 @Id UUID id;
 @Column(name="tenant_id") UUID tenantId;
 @Column(name="policy_id") UUID policyId;
 @Column(name="policy_name") String policyName;
 int version;
 String operation="CREATE";
 String source="API";
 @Column(name="source_ref") String sourceRef;
 @Column(name="policy_text",
 columnDefinition="text") String policyText;
 @Column(name="requested_by") String requestedBy;
 String status="PENDING";
 @Column(name="required_approvals") int requiredApprovals=1;
 int approvals=0;
 @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(name="simulation_json",columnDefinition="jsonb") String simulationJson="{}";
 @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(name="blast_radius_json",columnDefinition="jsonb") String blastRadiusJson="{}";
 @Column(name="canary_percent") int canaryPercent;
 @Column(name="commit_sha") String commitSha;
 @Column(name="created_at") Instant createdAt=Instant.now();
 @Column(name="approved_at") Instant approvedAt;
 @Column(name="published_at") Instant publishedAt;
 @Column(name="approved_by") String approvedBy;
 @Column(name="approval_comment") String approvalComment;
 @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(name="diff_json",
 columnDefinition="jsonb") String diffJson="{}";
 public UUID getId(){
     return id;
 }
 public void setId(UUID x){
     id=x;
 }
 public UUID getTenantId(){
     return tenantId;
     }
     public void setTenantId(UUID x){
         tenantId=x;
     }
     public UUID getPolicyId(){
     return policyId;
     }
     public void setPolicyId(UUID x){
         policyId=x;
     }
     public String getPolicyName(){
     return policyName;
     }
     public void setPolicyName(String x){
         policyName=x;
     }
 public int getVersion(){
     return version;
 }
 public void setVersion(int x){
     version=x;
     }
     public String getOperation(){
         return operation;
     }
     public void setOperation(String x){
     operation=x;
     }
     public String getSource(){
         return source;
     }
     public void setSource(String x){
     source=x;
     }
     public String getSourceRef(){
         return sourceRef;
     }
     public void setSourceRef(String x){
     sourceRef=x;
     }
     public String getPolicyText(){
         return policyText;
     }
     public void setPolicyText(String x){
     policyText=x;
     }
     public String getRequestedBy(){
         return requestedBy;
     }
     public void setRequestedBy(String x){
     requestedBy=x;
     }
     public String getStatus(){
         return status;
     }
     public void setStatus(String x){
     status=x;
     }
     public int getRequiredApprovals(){
         return requiredApprovals;
 }
 public int getApprovals(){
     return approvals;
 }
 public void setApprovals(int x){
 approvals=x;
 }
 public String getSimulationJson(){
     return simulationJson;
 }
 public void setSimulationJson(String x){
     simulationJson=x;
 }
 public String getBlastRadiusJson(){
 return blastRadiusJson;
 }
 public void setBlastRadiusJson(String x){
     blastRadiusJson=x;
 }
 public int getCanaryPercent(){
     return canaryPercent;
 }
 public void setCanaryPercent(int x){
 canaryPercent=x;
 }
 public String getCommitSha(){
     return commitSha;
 }
 public void setCommitSha(String x){
 commitSha=x;
 }
 public Instant getCreatedAt(){
     return createdAt;
 }
 public Instant getApprovedAt(){
 return approvedAt;
 }
 public void setApprovedAt(Instant x){
     approvedAt=x;
 }
 public Instant getPublishedAt(){
     return publishedAt;
 }
 public void setPublishedAt(Instant x){
 publishedAt=x;
 }
 public String getApprovedBy(){
     return approvedBy;
 }
 public void setApprovedBy(String x){
 approvedBy=x;
 }
 public String getApprovalComment(){
     return approvalComment;
 }
 public void setApprovalComment(String x){
     approvalComment=x;
 }
 public String getDiffJson(){
 return diffJson;
 }
 public void setDiffJson(String x){
     diffJson=x;
 }
}
