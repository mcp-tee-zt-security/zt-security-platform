package com.zt.security.incident;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="security_incidents") public class SecurityIncident {
    @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    String title;
    String severity="MEDIUM";
    String status="OPEN";
    @Column(name="principal_id") String principalId;
    @Column(name="source_decision_id") UUID sourceDecisionId;
    String summary;
    @Column(columnDefinition="jsonb") String evidence="[]";
    @Column(name="created_at") Instant createdAt=Instant.now();
    @Column(name="updated_at") Instant updatedAt=Instant.now();
 public UUID getId(){
     return id;
 }
 public String getTitle(){
     return title;
 }
 public String getSeverity(){
     return severity;
 }
 public String getStatus(){
 return status;
 }
 public String getPrincipalId(){
     return principalId;
 }
 public UUID getSourceDecisionId(){
 return sourceDecisionId;
 }
 public String getSummary(){
     return summary;
 }
 public String getEvidence(){
 return evidence;
 }
 public Instant getCreatedAt(){
     return createdAt;
 }
 public void setTenantId(UUID x){
 tenantId=x;
 }
 public void setTitle(String x){
     title=x;
 }
 public void setSeverity(String x){
 severity=x;
 }
 public void setStatus(String x){
     status=x;
 }
 public void setPrincipalId(String x){
 principalId=x;
 }
 public void setSourceDecisionId(UUID x){
     sourceDecisionId=x;
 }
 public void setSummary(String x){
     summary=x;
 }
 public void setEvidence(String x){
 evidence=x;
 }
}
