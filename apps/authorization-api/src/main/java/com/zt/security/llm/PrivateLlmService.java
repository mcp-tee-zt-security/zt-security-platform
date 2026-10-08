package com.zt.security.llm;
import com.zt.security.common.TenantSession;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
@Service public class PrivateLlmService {
    private static final Pattern
SECRET=Pattern.compile("(?i)(api[_-]?key|authorization|bearer|password|token|se" +
"cret)\\s*[:=]\\s*[^\\s,;]+" );
    private final HttpPrivateLlmProvider provider;
    private final TenantSession tenant;
    public PrivateLlmService(HttpPrivateLlmProvider p,TenantSession t){
        provider=p;
        tenant=t;
        }
 public Map<String,Object> health(UUID tenantId,Map<String,Object>b){
     tenant.set(tenantId);
     PrivateLlmRequest r=req(b,null);
     return Map.of("tenantId",tenantId,"privateOnly",
     true,"provider",r.provider(),"mtls",Boolean.parseBoolean(String.valueOf(r.metadata().getOrDefault("mtls",
     false))),"result",provider.health(r));
     }
 public Map<String,Object> chat(UUID tenantId,Map<String,Object>b){
     tenant.set(tenantId);
     String raw=String.valueOf(b.getOrDefault("prompt",""));
     String prompt=redact(raw);
     if(prompt.isBlank())throw new IllegalArgumentException("prompt is required");
     PrivateLlmRequest r=req(b,prompt);
     Map<String,Object> result=provider.chat(r);
     return Map.of("tenantId",tenantId,"privateOnly",true,"provider",r.provider(),
     "model",r.model(),"redactionApplied",!prompt.equals(raw),"result",result);
 }
 public StreamingResponseBody stream(UUID tenantId,Map<String,Object>b){
     tenant.set(tenantId);
     String raw=String.valueOf(b.getOrDefault("prompt",
     ""));
     String prompt=redact(raw);
     if(prompt.isBlank())throw new IllegalArgumentException("prompt is required");
     PrivateLlmRequest r=req(b,prompt);
     return out->{
         try(InputStream in=provider.stream(r)){
             in.transferTo(out);
             out.flush();
             } catch (InterruptedException e) {
                 Thread.currentThread().interrupt();
                 throw new java.io.IOException("Private LLM stream interrupted", e);
             } catch (Exception e) {
                 throw new java.io.IOException("Private LLM stream failed", e);
             }
             }
             ;
             }
 private PrivateLlmRequest req(Map<String,Object>b,String prompt){
     String p=String.valueOf(b.getOrDefault("provider",
     "OPENAI_COMPATIBLE")).toUpperCase(Locale.ROOT);
     if(!Set.of("OPENAI_COMPATIBLE",
     "VLLM","OLLAMA","BEDROCK_VPC").contains(p))throw new IllegalArgumentException("unsupported private provider: "+p);
     String e=String.valueOf(b.getOrDefault("endpoint","http://localhost:8000"));
if(p.equals("BEDROCK_VPC")&&!e.startsWith("https://"))throw new IllegalArgumentException("BEDROCK_VPC requires https endpoint");
     String m=String.valueOf(b.getOrDefault("model","security-copilot"));
     String secret=String.valueOf(b.
     getOrDefault("secretRef",
     ""));
     Map<String,Object> opts=b.get("tls") instanceof Map<?,?> ? cast((Map<?,
     ?>)b.get("tls")):Map.of();
     return new PrivateLlmRequest(p,e,m,secret,prompt,
     opts);
     }
 private Map<String,Object> cast(Map<?,?>x){
     Map<String,Object>m=new HashMap<>();
     x.forEach((k,v)->m.put(String.valueOf(k),v));
     return m;
     }
     private String redact(String s){
     return SECRET.matcher(s).replaceAll("$1=[REDACTED]");
     }
}
