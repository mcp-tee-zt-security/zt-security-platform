package com.zt.security.sso;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="sso_connections",uniqueConstraints=@UniqueConstraint(columnNames={
    "tenant_id","provider"}
))
public class SsoConnection {
    @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    String provider;
    @Column(name="issuer_uri") String issuerUri;
    @Column(name="client_id") String clientId;
    @Column(name="client_secret_ref") String clientSecretRef;
    boolean enabled;
    @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(name="allowed_domains",columnDefinition="jsonb") String allowedDomains="[]";
    @org.hibernate.annotations.ColumnTransformer(write="cast(? as jsonb)") @Column(name="claims_mapping",columnDefinition="jsonb") String claimsMapping="{\"subject\":\"sub\",\"email\":\"email\""
+
",\"groups\":\"groups\"}";
    @Column(name="created_at") Instant createdAt=Instant.now();
    @Column(name="updated_at") Instant updatedAt=
    Instant.now();
 public UUID getId(){
     return id;
 }
 public UUID getTenantId(){
     return tenantId;
 }
 public void setTenantId(UUID v){
     tenantId=v;
 }
 public String getProvider(){
 return provider;
 }
 public void setProvider(String v){
     provider=v;
 }
 public String getIssuerUri(){
 return issuerUri;
 }
 public void setIssuerUri(String v){
     issuerUri=v;
 }
 public String getClientId(){
 return clientId;
 }
 public void setClientId(String v){
     clientId=v;
 }
 public String getClientSecretRef(){
 return clientSecretRef;
 }
 public void setClientSecretRef(String v){
     clientSecretRef=v;
 }
 public boolean isEnabled(){
     return enabled;
 }
 public void setEnabled(boolean v){
 enabled=v;
 }
 public String getAllowedDomains(){
     return allowedDomains;
 }
 public void setAllowedDomains(String v){
 allowedDomains=v;
 }
 public String getClaimsMapping(){
     return claimsMapping;
 }
 public void setClaimsMapping(String v){
     claimsMapping=v;
 }
 public Instant getCreatedAt(){
 return createdAt;
 }
 public Instant getUpdatedAt(){
     return updatedAt;
 }
 }
