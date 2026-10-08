package com.zt.security.incident;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="security_cases")
public class SecurityCase {
    @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    String title;
    String severity="MEDIUM";
    String status="OPEN";
    @Column(name="source_type") String sourceType;
    @Column(name="source_id") UUID sourceId;
    @Column(name="assigned_to") String assignedTo;
    @Column(columnDefinition="text") String summary="";
    @Column(name="external_ref") String externalRef;
    @Column(name="evidence_count") int evidenceCount=0;
    @Column(name="created_at") Instant createdAt=Instant.now();
    @Column(name="updated_at") Instant updatedAt=Instant.now();
    @Column(name="closed_at") Instant closedAt;

    public UUID getId(){
        return id;
    }
    public UUID getTenantId(){
        return tenantId;
    }
    public void setTenantId(UUID v){
        tenantId=v;
    }
    public String getTitle(){
        return title;
    }
    public void setTitle(String v){
        title=v;
    }
    public String getSeverity(){
        return severity;
    }
    public void setSeverity(String v){
        severity=v;
    }
    public String getStatus(){
        return status;
    }
    public void setStatus(String v){
        status=v;
    }
    public String getSourceType(){
        return sourceType;
    }
    public void setSourceType(String v){
        sourceType=v;
    }
    public UUID getSourceId(){
        return sourceId;
    }
    public void setSourceId(UUID v){
        sourceId=v;
    }
    public String getAssignedTo(){
        return assignedTo;
    }
    public void setAssignedTo(String v){
        assignedTo=v;
    }
    public String getSummary(){
        return summary;
    }
    public void setSummary(String v){
        summary=v;
    }
    public String getExternalRef(){
        return externalRef;
    }
    public void setExternalRef(String v){
        externalRef=v;
    }
    public int getEvidenceCount(){
        return evidenceCount;
    }
    public void setEvidenceCount(int v){
        evidenceCount=v;
    }
    public Instant getCreatedAt(){
        return createdAt;
    }
    public Instant getUpdatedAt(){
        return updatedAt;
        }
        public void touch(){
            updatedAt=Instant.now();
        }
    public Instant getClosedAt(){
        return closedAt;
    }
    public void setClosedAt(Instant v){
        closedAt=v;
    }
}
