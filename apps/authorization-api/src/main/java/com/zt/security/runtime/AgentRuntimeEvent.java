package com.zt.security.runtime;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="agent_runtime_events") public class AgentRuntimeEvent {
@Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
@Column(name="tenant_id",
nullable=false) private UUID tenantId;
@Column(name="workspace_id") private UUID workspaceId;
@Column(name="session_id",nullable=false) private UUID sessionId;
@Column(name="request_id") private UUID requestId;
@Column(name="agent_external_id",nullable=false) private String agentExternalId;
@Column(name="task_external_id") private String taskExternalId;
@Column(name="tool_id") private String toolId;
@Column(nullable=false) private String action;
@Column(name="resource_type",
nullable=false) private String resourceType;
@Column(name="resource_id",
nullable=false) private String resourceId;
@Column(nullable=false) private String decision;
@Column(name="risk_score") private Double riskScore;
@Column(name="latency_ms") private Long latencyMs;
@Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
@Column(columnDefinition="jsonb",nullable=false) private String context="{}";
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
    public UUID getWorkspaceId(){
    return workspaceId;
    }
    public void setWorkspaceId(UUID x){
        workspaceId=x;
}
public UUID getSessionId(){
    return sessionId;
}
public void setSessionId(UUID x){
sessionId=x;
}
public UUID getRequestId(){
    return requestId;
}
public void setRequestId(UUID x){
requestId=x;
}
public String getAgentExternalId(){
    return agentExternalId;
}
public void setAgentExternalId(String x){
    agentExternalId=x;
}
public String getTaskExternalId(){
return taskExternalId;
}
public void setTaskExternalId(String x){
    taskExternalId=x;
}
public String getToolId(){
    return toolId;
}
public void setToolId(String x){
toolId=x;
}
public String getAction(){
    return action;
}
public void setAction(String x){
action=x;
}
public String getResourceType(){
    return resourceType;
}
public void setResourceType(String x){
resourceType=x;
}
public String getResourceId(){
    return resourceId;
}
public void setResourceId(String x){
resourceId=x;
}
public String getDecision(){
    return decision;
}
public void setDecision(String x){
decision=x;
}
public Double getRiskScore(){
    return riskScore;
}
public void setRiskScore(Double x){
riskScore=x;
}
public Long getLatencyMs(){
    return latencyMs;
}
public void setLatencyMs(Long x){
latencyMs=x;
}
public Instant getCreatedAt(){
    return createdAt;
}
public void setCreatedAt(Instant x){
createdAt=x;
}
public String getContext(){
    return context;
}
public void setContext(String x){
context=x;
}
}
