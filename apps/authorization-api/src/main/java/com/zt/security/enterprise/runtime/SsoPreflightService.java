package com.zt.security.enterprise.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.common.TenantSession;
import com.zt.security.enterprise.SsoPreflightSession;
import com.zt.security.enterprise.SsoPreflightSessionRepository;
import com.zt.security.sso.SsoConnection;
import com.zt.security.sso.SsoConnectionRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class SsoPreflightService {
 private final SsoConnectionRepository connections;
 private final SsoPreflightSessionRepository sessions;
 private final TenantSession tenant;
 private final ObjectMapper mapper;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.
 Redirect.NORMAL).build();
 public SsoPreflightService(SsoConnectionRepository c,SsoPreflightSessionRepository s,
 TenantSession t,ObjectMapper m){
     connections=c;
     sessions=s;
     tenant=t;
     mapper=m;
 }
 @Transactional(readOnly=true) public Map<String,Object> validate(UUID tenantId,
 String provider){
     tenant.set(tenantId);
     SsoConnection c=connections.findByTenantIdAndProvider(tenantId,
     provider).orElseThrow(()->new IllegalArgumentException("SSO connection not found: "+provider));
     List<Map<String,Object>> checks=new ArrayList<>();
     check(checks,"provider_supported",
     Set.of("KEYCLOAK","OKTA","ENTRA","ENTRA_ID","MICROSOFT_ENTRA","PING","PING_IDENTITY").contains(norm(c.
     getProvider())),
     "provider="+c.getProvider());
     check(checks,"issuer_configured",nonBlank(c.getIssuerUri()),
     c.getIssuerUri());
     check(checks,"client_id_configured",nonBlank(c.getClientId()),
     c.getClientId());
     check(checks,"client_secret_ref_configured",nonBlank(c.getClientSecretRef()),
     "secret reference present");
     if(nonBlank(c.getIssuerUri())){
         Map<String,
         Object>d=getJson(strip(c.getIssuerUri())+"/.well-known/openid-configuration");
         boolean ok=Boolean.TRUE.equals(d.get("ok"));
         check(checks,"oidc_discovery",
         ok,String.valueOf(d.getOrDefault("detail","unreachable")));
         if(ok){
             Map<?,
             ?>body=(Map<?,?>)d.get("body");
             check(checks,"authorization_endpoint",
             body.get("authorization_endpoint")!=null,
             String.valueOf(body.get("authorization_endpoint")));
             check(checks,"token_endpoint",
             body.get("token_endpoint")!=null,String.valueOf(body.get("token_endpoint")));
             Object jwks=body.get("jwks_uri");
             if(jwks!=null){
                 Map<String,Object>j=getJson(String.valueOf(jwks));
                 check(checks,"jwks_endpoint",Boolean.TRUE.equals(j.get("ok")),String.valueOf(j.getOrDefault("detail",
                 "unreachable")));
                 }
                 else check(checks,"jwks_endpoint",false,"jwks_uri missing from discovery");
         }
         }
 long fail=checks.stream().filter(x->"FAIL".equals(x.get("status"))).count(),
 warn=checks.stream().filter(x->"WARN".equals(x.get("status"))).count();
 String status=fail>0?"FAIL":warn>0?"WARN":"PASS";
 return Map.of("provider",
 c.getProvider(),"tenantId",tenantId,"status",status,"checkedAt",Instant.now(),
 "checks",checks,"nextStep",status.equals("PASS")?"OIDC discovery and JWKS are reachable; proceed to" +
" login integration test.":
 "Resolve failed checks before enabling enterprise SSO.");
 }
 @Transactional public Map<String,Object> start(UUID tenantId,String provider,
 String redirectUri,String scopes){
     tenant.set(tenantId);
     SsoConnection c=connections.findByTenantIdAndProvider(tenantId,
     provider).orElseThrow(()->new IllegalArgumentException("SSO connection not found: "+provider));
     if(!c.isEnabled())throw new IllegalStateException("SSO connection is disabled");
     if(!nonBlank(redirectUri))throw new IllegalArgumentException("redirectUri is required");
     try{
         Map<String,Object>d=getJson(strip(c.getIssuerUri())+"/.well-known/openid-configuration");
         if(!Boolean.TRUE.equals(d.get("ok")))throw new IllegalStateException("OIDC discovery failed");
         String auth=String.valueOf(((Map<?,?>)d.get("body")).get("authorization_endpoint"));
         String state=random(),nonce=random();
         SsoPreflightSession s=new SsoPreflightSession();
         s.setTenantId(tenantId);
         s.setProvider(c.getProvider());
         s.setIssuer(c.getIssuerUri());
         s.setClientId(c.getClientId());
         s.setRedirectUri(redirectUri);
         s.setStateHash(hash(state));
         s.setNonceHash(hash(nonce));
         s.setExpiresAt(Instant.now().plusSeconds(600));
         sessions.save(s);
         String url=auth+"?response_type=code&client_id="+enc(c.getClientId())+
         "&redirect_uri="+enc(redirectUri)+"&scope="+enc(scopes==null||scopes.isBlank()?
         "openid profile email":scopes)+"&state="+enc(state)+"&nonce="+enc(nonce);
         return Map.of("status","READY","provider",c.getProvider(),"authorizationUrl",
         url,"state",state,"expiresAt",s.getExpiresAt());
         }
         catch(Exception e){
         throw new IllegalStateException("unable to start SSO login",
         e);
         }
         }
 @Transactional public Map<String,Object> callback(String state,String code){
     if(state==null||code==null)throw new IllegalArgumentException("state and code are required");
     SsoPreflightSession s=sessions.findByStateHash(hash(state)).orElseThrow(()->
     new SecurityException("invalid SSO state"));
     tenant.set(s.getTenantId());
     if(s.getUsedAt()!=null||s.getExpiresAt().isBefore(Instant.now()))throw
new SecurityException("expired or already used SSO state");
     try{
         SsoConnection c=connections.findByTenantIdAndProvider(s.getTenantId(),
         s.getProvider()).orElseThrow(()->new SecurityException("SSO connection not found"));
         Map<String,Object>d=getJson(strip(s.getIssuer())+"/.well-known/openid-configuration");
         String tokenUrl=String.valueOf(((Map<?,?>)d.get("body")).get("token_endpoint"));
         String secretRef=c.getClientSecretRef();
         String secret=secretRef==null?
         "":String.valueOf(Optional.ofNullable(System.getenv(secretRef)).orElse(""));
         String form="grant_type=authorization_code&code="+enc(code)+"&redirect_uri="+enc(s.getRedirectUri())+
         "&client_id="+enc(s.getClientId());
         HttpRequest.Builder rb=HttpRequest.newBuilder(URI.create(tokenUrl)).timeout(Duration.ofSeconds(15)).
         header("Content-Type",
         "application/x-www-form-urlencoded");
         if(!secret.isBlank()){
             String basic=Base64.getEncoder().
             encodeToString((s.getClientId()+":"+secret).getBytes(StandardCharsets.UTF_8));
             rb.header("Authorization","Basic "+basic);
             }
             else form+="&client_secret="+enc("");
         var res=http.send(rb.POST(HttpRequest.BodyPublishers.ofString(form)).build(),
         HttpResponse.BodyHandlers.ofString());
         if(res.statusCode()/100!=2)throw new
SecurityException("token endpoint returned "+
         res.statusCode());
         Map<String,Object>token=mapper.readValue(res.body(),Map.class);
         String idToken=String.valueOf(token.
         getOrDefault("id_token",
         ""));
         if(idToken.isBlank())throw new SecurityException("id_token missing");
         JwtDecoder decoder=JwtDecoders.fromIssuerLocation(s.getIssuer());
         Jwt jwt=decoder.decode(idToken);
         if(!s.getIssuer().equals(jwt.getIssuer().toString()))throw new SecurityException("issuer mismatch");
         if(!jwt.getAudience().contains(s.getClientId()))throw new SecurityException("audience mismatch");
         Object nonce=jwt.getClaims().get("nonce");
         if(nonce!=null&&!hash(String.valueOf(nonce)).equals(s.
         getNonceHash()))throw new SecurityException("nonce mismatch");
         s.setUsedAt(Instant.now());
         sessions.save(s);
         return Map.of("status","PASS",
         "provider",s.getProvider(),"subject",jwt.getSubject(),"issuer",jwt.getIssuer(),
         "audience",jwt.getAudience(),"claims",jwt.getClaims());
         }
         catch(Exception e){
         throw new SecurityException("SSO login validation failed: "+e.getMessage(),
         e);
         }
         }
 private void check(List<Map<String,Object>>o,String n,boolean ok,String d){
     o.add(Map.of("name",n,"status",ok?"PASS":"FAIL","detail",d==null?"":d));
 }
 private Map<String,Object>getJson(String url){
     try{
         var r=http.send(HttpRequest.newBuilder(URI.create(url)).
     timeout(Duration.ofSeconds(8)).header("Accept",
     "application/json").GET().build(),HttpResponse.BodyHandlers.ofString());
     if(r.statusCode()/100!=2)return Map.of("ok",false,"detail","HTTP "+r.statusCode());
     return Map.of("ok",true,"body",mapper.readValue(r.body(),Map.class));
     }
 catch(Exception e){
     return Map.of("ok",false,"detail",e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()));
 }
 }
 private String strip(String s){
     return s.replaceAll("/+$","");
 }
 private boolean nonBlank(String s){
 return s!=null&&!s.isBlank();
 }
 private String norm(String s){
     return s==null?"":s.toUpperCase(Locale.ROOT).replace('-',
 '_').replace(' ','_');
 }
 private String random(){
     return UUID.randomUUID().toString().replace("-",
 "")+UUID.randomUUID().toString().replace("-","");
 }
 private String hash(String s){
     try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
     } catch (java.security.NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 unavailable", e);
     }
 }
 private String enc(String s){
     return URLEncoder.encode(s,StandardCharsets.UTF_8);
 }
}
