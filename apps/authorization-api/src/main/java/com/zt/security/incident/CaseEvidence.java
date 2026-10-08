package com.zt.security.incident;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="security_case_evidence")
public class CaseEvidence {
 @Id UUID id=UUID.randomUUID();
 @Column(name="tenant_id") UUID tenantId;
 @Column(name="case_id") UUID caseId;
 @Column(name="evidence_type") String evidenceType;
 @Column(name="source_ref") String sourceRef;
 @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(columnDefinition="jsonb") String payload="{}";
 @Column(name="content_hash") String contentHash;
 @Column(name="created_by") String createdBy;
 @Column(name="created_at") Instant createdAt=Instant.now();
 public UUID getId(){
     return id;
 }
 public UUID getTenantId(){
     return tenantId;
 }
 public void setTenantId(UUID v){
     tenantId=v;
 }
 public UUID getCaseId(){
     return caseId;
 }
 public void setCaseId(UUID v){
     caseId=v;
 }
 public String getEvidenceType(){
     return evidenceType;
 }
 public void setEvidenceType(String v){
     evidenceType=v;
 }
 public String getSourceRef(){
     return sourceRef;
 }
 public void setSourceRef(String v){
     sourceRef=v;
 }
 public String getPayload(){
     return payload;
 }
 public void setPayload(String v){
     payload=v;
 }
 public String getContentHash(){
     return contentHash;
 }
 public void setContentHash(String v){
     contentHash=v;
 }
 public String getCreatedBy(){
     return createdBy;
 }
 public void setCreatedBy(String v){
     createdBy=v;
 }
 public Instant getCreatedAt(){
     return createdAt;
 }
}
