package com.zt.security.settings;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="tenant_settings") public class TenantSettings {
    @Id @Column(name="tenant_id") UUID tenantId;
    @Column(name="security_mode") String securityMode="ENFORCED";
    @Column(name="default_rate_limit") int defaultRateLimit=120;
    @Column(name="runtime_session_ttl_seconds")
int runtimeSessionTtlSeconds=
    3600;
    @Column(name="audit_retention_days") int auditRetentionDays=365;
    @Column(name="webhook_enabled")
boolean webhookEnabled=
    true;
    Instant updatedAt=Instant.now();
    public UUID getTenantId(){
        return tenantId;
    }
    public void setTenantId(UUID v){
        tenantId=v;
    }
    public String getSecurityMode(){
    return securityMode;
    }
    public void setSecurityMode(String v){
        securityMode=v;
}
public int getDefaultRateLimit(){
    return defaultRateLimit;
}
public void setDefaultRateLimit(int v){
defaultRateLimit=v;
}
public int getRuntimeSessionTtlSeconds(){
    return runtimeSessionTtlSeconds;
}
public void setRuntimeSessionTtlSeconds(int v){
    runtimeSessionTtlSeconds=v;
}
public int getAuditRetentionDays(){
    return auditRetentionDays;
}
public void setAuditRetentionDays(int v){
auditRetentionDays=v;
}
public boolean isWebhookEnabled(){
    return webhookEnabled;
}
public void setWebhookEnabled(boolean v){
    webhookEnabled=v;
}
}
