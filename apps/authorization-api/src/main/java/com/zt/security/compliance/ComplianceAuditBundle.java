package com.zt.security.compliance;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="compliance_audit_bundles")
public class ComplianceAuditBundle {
 @Id UUID id=UUID.randomUUID();
 @Column(name="tenant_id") UUID tenantId;
 @Column(name="assessment_id") UUID assessmentId;
 @Column(name="bundle_hash") String bundleHash;
 String signature;
 @Column(name="key_id") String keyId;
 @Column(name="created_at") Instant createdAt=Instant.now();
 @Column(name="created_by") String createdBy;
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
     public String getBundleHash(){
         return bundleHash;
     }
     public void setBundleHash(String v){
     bundleHash=v;
     }
 public String getSignature(){
     return signature;
 }
 public void setSignature(String v){
     signature=v;
     }
     public String getKeyId(){
         return keyId;
     }
     public void setKeyId(String v){
     keyId=v;
     }
 public Instant getCreatedAt(){
     return createdAt;
 }
 public String getCreatedBy(){
     return createdBy;
     }
     public void setCreatedBy(String v){
         createdBy=v;
     }
}
