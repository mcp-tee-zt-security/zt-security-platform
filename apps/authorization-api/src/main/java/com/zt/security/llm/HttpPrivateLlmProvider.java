package com.zt.security.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.*;

@Component
public class HttpPrivateLlmProvider implements PrivateLlmProvider {
 private final ObjectMapper mapper;
 @Value("${zt.security.risk-scoring.llm-temperature:0.10}") private double temperature;
 public HttpPrivateLlmProvider(ObjectMapper mapper){
     this.mapper=mapper;
 }
 public String provider(){
     return "OPENAI_COMPATIBLE";
 }
 public Map<String,Object> health(PrivateLlmRequest r){
     String p=norm(r.provider());
     String url=strip(r.endpoint());
     if(p.equals("OLLAMA"))url+="/api/tags";
     else url+="/v1/models";
     return request(url,r.secretRef(),"GET",null,r.metadata());
 }
 public Map<String,Object> chat(PrivateLlmRequest r){
     String p=norm(r.provider());
     String url=strip(r.endpoint());
     Map<String,Object> body;
     if(p.equals("OLLAMA"))body=Map.of("model",
     r.model(),"stream",false,"messages",List.of(Map.of("role","user","content",
     r.prompt())));
     else body=Map.of("model",r.model(),"temperature",temperature,"messages",
     List.of(Map.of("role","user","content",r.prompt())));
     return request(p.equals("OLLAMA")?
     url+"/api/chat":url+"/v1/chat/completions",
     r.secretRef(),"POST",body,r.metadata());
     }
 public InputStream stream(PrivateLlmRequest r)throws Exception{
     String p=norm(r.provider());
     if(p.equals("OLLAMA"))throw new IllegalArgumentException("stream endpoint currently requires OpenAI-compatible" +
" SSE");
     String url=strip(r.endpoint())+"/v1/chat/completions";
     Map<String,Object> body=Map.of("model",
     r.model(),"temperature",temperature,"stream",true,"messages",List.of(Map.of("role",
     "user","content",r.prompt())));
     HttpRequest.Builder b=builder(url,r.secretRef(),
     r.metadata()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.
     writeValueAsString(body)));
     HttpResponse<InputStream> res=client(r.metadata()).send(b.build(),HttpResponse.BodyHandlers.ofInputStream());
     if(res.statusCode()/100!=2){
         String x=new String(res.body().readAllBytes());
         throw new IllegalStateException("LLM stream returned "+res.statusCode()+": "+x);
     }
     return res.body();
     }
 private Map<String,Object> request(String url,String secret,String method,
 Object payload,Map<String,Object> options){
     try{
         HttpRequest.Builder b=builder(url,
         secret,options).header("Accept","application/json");
         if("POST".equals(method))b.header("Content-Type",
         "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));
         else b.GET();
         var res=client(options).send(b.build(),HttpResponse.BodyHandlers.ofString());
         Map<String,Object> out=new LinkedHashMap<>();
         out.put("ok",res.statusCode()/100==2);
         out.put("httpStatus",res.statusCode());
         try{
             out.put("body",mapper.readValue(res.body(),
             Map.class));
             }
             catch(Exception e){
                 out.put("body",Map.of("raw",res.body()));
         }
         return out;
         }
         catch(Exception e){
             return Map.of("ok",false,"httpStatus",
     0,"error",e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()));
 }
 }
 private HttpRequest.Builder builder(String url,String secret,Map<String,
 Object> options){
     var b=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(longVal(options,
     "timeoutSeconds",60)));
     if(secret!=null&&!secret.isBlank()){
         String v=System.getenv(secret);
         if(v!=null&&!v.isBlank())b.header("Authorization","Bearer "+v);
         }
         return b;
 }
 private HttpClient client(Map<String,Object> o)throws Exception{
     return HttpClient.newBuilder().connectTimeout(Duration.
     ofSeconds(longVal(o,
     "connectTimeoutSeconds",5))).sslContext(ssl(o)).build();
     }
 private SSLContext ssl(Map<String,Object> o)throws Exception{
     if(!bool(o,
     "mtls",false)&&!bool(o,"customTrust",false))return SSLContext.getDefault();
     KeyStore ks=null,ts=null;
     KeyManagerFactory kmf=null;
     TrustManagerFactory tmf=null;
     String
ksRef=String.valueOf(o.getOrDefault("keyStoreRef",
     ""));
     if(!ksRef.isBlank()){
         ks=KeyStore.getInstance(String.valueOf(o.getOrDefault("keyStoreType",
         "PKCS12")));
         try(InputStream in=Files.newInputStream(Path.of(envPath(ksRef)))){
             ks.load(in,password(o,"keyStorePasswordRef"));
             }
             kmf=KeyManagerFactory.getInstance(KeyManagerFactory.
             getDefaultAlgorithm());
         kmf.init(ks,password(o,"keyStorePasswordRef"));
         }
         String tsRef=String.valueOf(o.getOrDefault("trustStoreRef",
     ""));
     if(!tsRef.isBlank()){
         ts=KeyStore.getInstance(String.valueOf(o.getOrDefault("trustStoreType",
         "PKCS12")));
         try(InputStream in=Files.newInputStream(Path.of(envPath(tsRef)))){
             ts.load(in,password(o,"trustStorePasswordRef"));
             }
             tmf=TrustManagerFactory.getInstance(TrustManagerFactory.
             getDefaultAlgorithm());
         tmf.init(ts);
         }
         SSLContext c=SSLContext.getInstance("TLS");
         c.init(kmf==null?null:kmf.getKeyManagers(),
     tmf==null?null:tmf.getTrustManagers(),new SecureRandom());
     return c;
     }
 private char[] password(Map<String,Object>o,String key){
     String ref=String.valueOf(o.getOrDefault(key,
     ""));
     String v=ref.isBlank()?"":String.valueOf(System.getenv(ref));
     return v.toCharArray();
 }
 private String envPath(String ref){
     String v=System.getenv(ref);
     return v==null||v.isBlank()?ref:v;
 }
 private String strip(String s){
     return s.replaceAll("/+$","");
 }
 private String norm(String s){
 return s==null?"":s.toUpperCase(Locale.ROOT).replace('-','_');
 }
 private boolean bool(Map<String,
 Object>m,String k,boolean d){
     Object v=m.get(k);
     return v==null?d:Boolean.parseBoolean(String.valueOf(v));
 }
 private long longVal(Map<String,Object>m,String k,long d){
     try{
         return Long.parseLong(String.valueOf(m.getOrDefault(k,
     d)));
     }
     catch(Exception e){
         return d;
     }
     }
}
