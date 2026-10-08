package com.zt.security.idempotency;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.common.TenantSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
@Service public class IdempotencyService {
    final IdempotencyRepository repo;
    final TenantSession session;
    final ObjectMapper mapper=new ObjectMapper();
    public IdempotencyService(IdempotencyRepository r,TenantSession s){
        repo=r;
        session=s;
        }
 @Transactional public Optional<String> existing(UUID tenant,String key,
 Object request){
     if(key==null||key.isBlank())return Optional.empty();
     session.set(tenant);
     String h=hash(request);
     var x=repo.findByTenantIdAndKey(tenant,key);
     if(x.isEmpty()){
         IdempotencyKey k=new IdempotencyKey();
         k.setId(UUID.randomUUID());
         k.setTenantId(tenant);
         k.setKey(key);
         k.setRequestHash(h);
         repo.save(k);
         return Optional.empty();
}
if(!x.get().getRequestHash().equals(h))throw new IllegalStateException("Idempotency-Key reused with different request");
 return Optional.ofNullable(x.get().getResponseJson());
 }
 @Transactional public void complete(UUID tenant,String key,Object response){
     if(key==null||key.isBlank())return;
     session.set(tenant);
     repo.findByTenantIdAndKey(tenant,
     key).ifPresent(x->{
         try{
             x.setResponseJson(mapper.writeValueAsString(response));
             x.setStatus("COMPLETED");
             repo.save(x);
             }
             catch(Exception e){
                 throw new IllegalStateException(e);
         }
         }
         );
         }
 private String hash(Object o){
     try{
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").
         digest(mapper.writeValueAsBytes(o)));
     }
     catch(Exception e){
         throw new IllegalStateException(e);
     }
     }
}
