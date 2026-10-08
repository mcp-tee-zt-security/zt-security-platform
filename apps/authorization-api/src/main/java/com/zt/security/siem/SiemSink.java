package com.zt.security.siem;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="siem_sinks",uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","name"}
))
public class SiemSink {
    @Id UUID id;
    @Column(name="tenant_id") UUID tenantId;
    String name;
    String endpoint;
    @Column(name="secret_ref") String secretRef;
    boolean enabled=true;
    @Column(name="event_types",columnDefinition="jsonb") String eventTypes="[]";
    @Column(name="created_at") Instant createdAt=Instant.now();
    public UUID getId(){
        return id;
        }
        public void setId(UUID v){
            id=v;
        }
        public UUID getTenantId(){
        return tenantId;
        }
        public void setTenantId(UUID v){
            tenantId=v;
        }
        public String getName(){
        return name;
        }
        public void setName(String v){
            name=v;
        }
        public String getEndpoint(){
        return endpoint;
        }
        public void setEndpoint(String v){
            endpoint=v;
        }
        public String getSecretRef(){
        return secretRef;
        }
        public void setSecretRef(String v){
            secretRef=v;
        }
        public boolean isEnabled(){
        return enabled;
        }
        public void setEnabled(boolean v){
            enabled=v;
        }
        public String getEventTypes(){
        return eventTypes;
        }
        public void setEventTypes(String v){
            eventTypes=v;
        }
}
