package com.zt.security.behavior;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="agent_behavior_events") public class AgentBehaviorEvent {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="agent_external_id") String agentExternalId;
    @Column(name="request_id") UUID requestId;
    @Column(name="tool_id") UUID toolId;
    String action;
    @Column(name="resource_type") String resourceType;
    String decision;
    @Column(name="risk_score") Double riskScore;
    Double amount;
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
    public void setToolId(UUID x){
    toolId=x;
    }
    public void setAction(String x){
        action=x;
    }
    public void setResourceType(String x){
    resourceType=x;
    }
    public void setDecision(String x){
        decision=x;
    }
    public void setRiskScore(Double x){
    riskScore=x;
    }
    public void setAmount(Double x){
        amount=x;
    }
    public String getAgentExternalId(){
    return agentExternalId;
    }
    public String getAction(){
        return action;
    }
    public String getResourceType(){
    return resourceType;
    }
    public String getDecision(){
        return decision;
    }
    public Double getRiskScore(){
    return riskScore;
    }
    public Double getAmount(){
        return amount;
    }
    public UUID getRequestId(){
    return requestId;
    }
    public UUID getToolId(){
        return toolId;
    }
    public Instant getCreatedAt(){
    return createdAt;
    }
    }
