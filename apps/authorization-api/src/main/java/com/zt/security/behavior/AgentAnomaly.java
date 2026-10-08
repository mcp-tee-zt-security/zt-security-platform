package com.zt.security.behavior;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="agent_anomalies") public class AgentAnomaly {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="agent_external_id") String agentExternalId;
    @Column(name="request_id") UUID requestId;
    @Column(name="anomaly_type") String anomalyType;
    String severity;
    double score;
    String reason;
    @Column(name="evidence",
    columnDefinition="jsonb") String evidence="{}";
    String status="OPEN";
    @Column(name="created_at") Instant createdAt=
    Instant.now();
    public UUID getId(){
        return id;
    }
    public void setId(UUID x){
        id=x;
    }
    public void setTenantId(UUID x){
        tenantId=x;
        }
        public void setAgentExternalId(String x){
            agentExternalId=x;
    }
    public void setRequestId(UUID x){
        requestId=x;
    }
    public void setAnomalyType(String x){
    anomalyType=x;
    }
    public void setSeverity(String x){
        severity=x;
    }
    public void setScore(double x){
    score=x;
    }
    public void setReason(String x){
        reason=x;
    }
    public void setEvidence(String x){
    evidence=x;
    }
    public String getAgentExternalId(){
        return agentExternalId;
}
public UUID getRequestId(){
    return requestId;
}
public String getAnomalyType(){
return anomalyType;
}
public String getSeverity(){
    return severity;
}
public double getScore(){
return score;
}
public Instant getCreatedAt(){
    return createdAt;
}
}
