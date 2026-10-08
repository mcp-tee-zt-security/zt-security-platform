package com.zt.security.lifecycle;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="gitops_sources") public class GitOpsSource {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    String name,repository,branch,
    path;
    @Column(name="webhook_secret") String webhookSecret;
    boolean enabled=true;
    @Column(name="last_commit_sha") String lastCommitSha;
    @Column(name="last_synced_at") Instant lastSyncedAt;
    public UUID getId(){
        return id;
    }
    public void setId(UUID x){
        id=x;
    }
    public void setTenantId(UUID x){
        tenantId=x;
        }
        public String getName(){
            return name;
        }
        public void setName(String x){
        name=x;
        }
        public void setRepository(String x){
            repository=x;
        }
        public void setBranch(String x){
        branch=x;
        }
        public void setPath(String x){
            path=x;
        }
        public void setWebhookSecret(String x){
        webhookSecret=x;
        }
        public void setEnabled(boolean x){
            enabled=x;
        }
        public void setLastCommitSha(String x){
        lastCommitSha=x;
        }
        public void setLastSyncedAt(Instant x){
            lastSyncedAt=x;
    }
    public String getRepository(){
        return repository;
    }
    public String getBranch(){
    return branch;
    }
    public String getPath(){
        return path;
    }
    public boolean isEnabled(){
    return enabled;
    }
    }
