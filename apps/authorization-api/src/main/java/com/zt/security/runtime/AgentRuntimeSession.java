package com.zt.security.runtime;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="agent_runtime_sessions") public class AgentRuntimeSession {
@Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
@Column(name="tenant_id",
nullable=false) private UUID tenantId;
@Column(name="workspace_id") private UUID workspaceId;
@Column(name="agent_external_id",nullable=false) private String agentExternalId;
@Column(name="task_external_id") private String taskExternalId;
@Column(nullable=false) private String status="ACTIVE";
@Column(nullable=false) private String source="SDK";
@Column(name="started_at",
nullable=false) private Instant startedAt=Instant.now();
@Column(name="last_seen_at",
nullable=false) private Instant lastSeenAt=Instant.now();
@Column(name="ended_at") private Instant endedAt;
@org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(columnDefinition="jsonb",nullable=false) private String metadata="{}";
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
public String getStatus(){
return status;
}
public void setStatus(String x){
    status=x;
}
public String getSource(){
return source;
}
public void setSource(String x){
    source=x;
}
public Instant getStartedAt(){
return startedAt;
}
public void setStartedAt(Instant x){
    startedAt=x;
}
public Instant getLastSeenAt(){
return lastSeenAt;
}
public void setLastSeenAt(Instant x){
    lastSeenAt=x;
}
public Instant getEndedAt(){
    return endedAt;
}
public void setEndedAt(Instant x){
endedAt=x;
}
public String getMetadata(){
    return metadata;
}
public void setMetadata(String x){
metadata=x;
}
}
