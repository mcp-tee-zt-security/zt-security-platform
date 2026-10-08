package com.zt.security.lifecycle;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="policy_deployments") public class PolicyDeployment {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="policy_id") UUID policyId;
    @Column(name="policy_name") String policyName;
    int version;
    String stage;
    @Column(name="canary_percent") int canaryPercent;
    String status;
    @Column(name="commit_sha") String commitSha;
    @Column(name="started_at") Instant startedAt=Instant.now();
    @Column(name="completed_at") Instant completedAt;
    @Column(name="canary_started_at") Instant canaryStartedAt;
    @Column(name="canary_stopped_at") Instant
canaryStoppedAt;
    @Column(name="canary_reason") String canaryReason;
    @Column(name="auto_stop_threshold") double autoStopThreshold=20;
    boolean autoRollbackEnabled=true;
    int minCanaryEvents=20;
    public UUID getId(){
        return id;
        }
        public void setId(UUID x){
            id=x;
        }
        public void setTenantId(UUID x){
        tenantId=x;
        }
        public void setPolicyId(UUID x){
            policyId=x;
        }
        public void setPolicyName(String x){
        policyName=x;
        }
        public void setVersion(int x){
            version=x;
        }
        public void setStage(String x){
        stage=x;
        }
        public void setCanaryPercent(int x){
            canaryPercent=x;
        }
        public void setStatus(String x){
        status=x;
        }
        public void setCommitSha(String x){
            commitSha=x;
        }
        public void setCompletedAt(Instant x){
        completedAt=x;
        }
        public UUID getTenantId(){
            return tenantId;
        }
        public UUID getPolicyId(){
        return policyId;
        }
        public String getPolicyName(){
            return policyName;
        }
        public int getVersion(){
        return version;
        }
        public String getStage(){
            return stage;
        }
        public String getCommitSha(){
        return commitSha;
        }
        public int getCanaryPercent(){
            return canaryPercent;
    }
    public String getStatus(){
        return status;
    }
    public Instant getStartedAt(){
    return startedAt;
    }
    public Instant getCompletedAt(){
        return completedAt;
}
public Instant getCanaryStartedAt(){
    return canaryStartedAt;
}
public Instant getCanaryStoppedAt(){
return canaryStoppedAt;
}
public String getCanaryReason(){
    return canaryReason;
}
public double getAutoStopThreshold(){
    return autoStopThreshold;
}
public void setAutoStopThreshold(double x){
autoStopThreshold=x;
}
public boolean getAutoRollbackEnabled(){
    return autoRollbackEnabled;
}
public void setAutoRollbackEnabled(boolean x){
    autoRollbackEnabled=x;
}
public int getMinCanaryEvents(){
    return minCanaryEvents;
}
public void setMinCanaryEvents(int x){
minCanaryEvents=x;
}
public void setCanaryStartedAt(Instant x){
    canaryStartedAt=x;
}
public void setCanaryStoppedAt(Instant x){
    canaryStoppedAt=x;
}
public void setCanaryReason(String x){
canaryReason=x;
}
}
