package com.zt.security.compliance;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="compliance_assessments")
public class ComplianceAssessment {
 @Id UUID id=UUID.randomUUID();
 @Column(name="tenant_id") UUID tenantId;
 String framework;
 @Column(name="period_start") Instant periodStart;
 @Column(name="period_end") Instant periodEnd;
 String status;
 double score;
 @Column(columnDefinition="jsonb") String controls="[]";
 @Column(name="evidence_count") int evidenceCount;
 @Column(name="report_hash") String reportHash;
 @Column(name="generated_by") String generatedBy;
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
 public String getFramework(){
 return framework;
 }
 public void setFramework(String v){
     framework=v;
 }
 public Instant getPeriodStart(){
 return periodStart;
 }
 public void setPeriodStart(Instant v){
     periodStart=v;
 }
 public Instant getPeriodEnd(){
     return periodEnd;
 }
 public void setPeriodEnd(Instant v){
 periodEnd=v;
 }
 public String getStatus(){
     return status;
 }
 public void setStatus(String v){
 status=v;
 }
 public double getScore(){
     return score;
 }
 public void setScore(double v){
 score=v;
 }
 public String getControls(){
     return controls;
 }
 public void setControls(String v){
 controls=v;
 }
 public int getEvidenceCount(){
     return evidenceCount;
 }
 public void setEvidenceCount(int v){
 evidenceCount=v;
 }
 public String getReportHash(){
     return reportHash;
 }
 public void setReportHash(String v){
 reportHash=v;
 }
 public String getGeneratedBy(){
     return generatedBy;
 }
 public void setGeneratedBy(String v){
 generatedBy=v;
 }
 public Instant getCreatedAt(){
     return createdAt;
 }
}
