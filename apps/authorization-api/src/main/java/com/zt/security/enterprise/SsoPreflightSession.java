package com.zt.security.enterprise;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="enterprise_sso_preflight_sessions") public class SsoPreflightSession {
    @Id UUID id=UUID.randomUUID();
    @Column(name="tenant_id") UUID tenantId;
    @Column(name="state_hash") String stateHash;
    @Column(name="nonce_hash") String nonceHash;
    String provider;
    String issuer;
    @Column(name="client_id") String clientId;
    @Column(name="redirect_uri") String redirectUri;
    @Column(name="created_at") Instant createdAt=Instant.now();
    @Column(name="expires_at") Instant expiresAt;
    @Column(name="used_at") Instant usedAt;
 public UUID getId(){
     return id;
 }
 public UUID getTenantId(){
     return tenantId;
 }
 public void setTenantId(UUID v){
     tenantId=v;
 }
 public String getStateHash(){
 return stateHash;
 }
 public void setStateHash(String v){
     stateHash=v;
 }
 public String getNonceHash(){
 return nonceHash;
 }
 public void setNonceHash(String v){
     nonceHash=v;
 }
 public String getProvider(){
 return provider;
 }
 public void setProvider(String v){
     provider=v;
 }
 public String getIssuer(){
 return issuer;
 }
 public void setIssuer(String v){
     issuer=v;
 }
 public String getClientId(){
 return clientId;
 }
 public void setClientId(String v){
     clientId=v;
 }
 public String getRedirectUri(){
 return redirectUri;
 }
 public void setRedirectUri(String v){
     redirectUri=v;
 }
 public Instant getCreatedAt(){
     return createdAt;
 }
 public Instant getExpiresAt(){
 return expiresAt;
 }
 public void setExpiresAt(Instant v){
     expiresAt=v;
 }
 public Instant getUsedAt(){
 return usedAt;
 }
 public void setUsedAt(Instant v){
     usedAt=v;
 }
 }
