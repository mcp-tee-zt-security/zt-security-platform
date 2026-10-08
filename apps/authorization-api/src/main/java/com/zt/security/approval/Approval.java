package com.zt.security.approval;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="approvals") public class Approval {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="request_id") UUID requestId;
    @Column(name="approver_id") String approverId;
    String status="PENDING";
    String reason;
    @Column(columnDefinition="jsonb") String payload="{}";
    @Column(name="approval_type") String approvalType="HUMAN";
    @Column(columnDefinition="jsonb") String metadata="{}";
    @Column(name="created_at") Instant createdAt=Instant.now();
    @Column(name="expires_at") Instant expiresAt;
    @Column(name="decided_at") Instant decidedAt;
    @Column(name="decided_by") String decidedBy;
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
     public UUID getRequestId(){
     return requestId;
     }
     public void setRequestId(UUID x){
         requestId=x;
     }
     public String getApproverId(){
     return approverId;
     }
     public void setApproverId(String x){
         approverId=x;
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
     public String getPayload(){
         return payload;
     }
     public void setPayload(String x){
     payload=x;
     }
     public String getApprovalType(){
         return approvalType;
     }
     public void setApprovalType(String x){
     approvalType=x;
     }
     public String getMetadata(){
         return metadata;
     }
     public void setMetadata(String x){
     metadata=x;
     }
     public Instant getCreatedAt(){
         return createdAt;
     }
     public Instant getExpiresAt(){
     return expiresAt;
     }
     public void setExpiresAt(Instant x){
         expiresAt=x;
     }
     public Instant getDecidedAt(){
     return decidedAt;
     }
     public void setDecidedAt(Instant x){
         decidedAt=x;
     }
     public String getDecidedBy(){
     return decidedBy;
     }
     public void setDecidedBy(String x){
         decidedBy=x;
     }
     }
