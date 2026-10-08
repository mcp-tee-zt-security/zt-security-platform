package com.zt.security.audit;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="audit_logs") public class AuditLog {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="request_id") UUID requestId;
    @Column(name="identity_id") UUID identityId;
    @Column(name="identity_type") String identityType;
    String action;
    @Column(name="resource_type") String resourceType;
    @Column(name="resource_id") String resourceId;
    String decision;
    @Column(name="policy_id") UUID policyId;
    String reason;
    @Column(name="risk_score") Double riskScore;
    @Column(columnDefinition="jsonb") String metadata="{}";
    @Column(name="previous_hash") String previousHash;
    @Column(name="event_hash") String eventHash;
    @Column(name="created_at") Instant createdAt=Instant.now();
    public void setId(UUID x){
        id=x;
        }
        public UUID getId(){
            return id;
        }
        public void setTenantId(UUID x){
        tenantId=x;
        }
        public void setRequestId(UUID x){
            requestId=x;
        }
        public void setIdentityId(UUID x){
        identityId=x;
        }
        public void setIdentityType(String x){
            identityType=x;
        }
        public void setAction(String x){
        action=x;
        }
        public void setResourceType(String x){
            resourceType=x;
        }
        public void setResourceId(String x){
        resourceId=x;
        }
        public void setDecision(String x){
            decision=x;
        }
        public void setPolicyId(UUID x){
        policyId=x;
        }
        public void setReason(String x){
            reason=x;
        }
        public void setRiskScore(Double x){
        riskScore=x;
        }
        public void setMetadata(String x){
            metadata=x;
        }
        public void setPreviousHash(String x){
        previousHash=x;
        }
        public void setEventHash(String x){
            eventHash=x;
        }
        public String getEventHash(){
        return eventHash;
        }
        public Instant getCreatedAt(){
            return createdAt;
        }
        public UUID getTenantId(){
        return tenantId;
        }
        public UUID getRequestId(){
            return requestId;
        }
        public String getAction(){
        return action;
        }
        public String getResourceType(){
            return resourceType;
        }
        public String getResourceId(){
        return resourceId;
        }
        public String getDecision(){
            return decision;
        }
        public String getReason(){
        return reason;
        }
        public Double getRiskScore(){
            return riskScore;
        }
        public UUID getIdentityId(){
        return identityId;
        }
        public String getIdentityType(){
            return identityType;
    }
    public UUID getPolicyId(){
        return policyId;
    }
    public String getMetadata(){
    return metadata;
    }
    public String getPreviousHash(){
        return previousHash;
}
}
