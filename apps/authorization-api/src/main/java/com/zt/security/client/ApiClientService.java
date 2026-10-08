package com.zt.security.client;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
@Service public class ApiClientService {
    final ApiClientRepository repo;
    ApiClientService(ApiClientRepository r){
        repo=r;
    }
    static String hash(String s){
try{
    var d=MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(d.digest(s.getBytes(StandardCharsets.
            UTF_8)));
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
        }
        public Map<String,
Object> create(UUID tenant,Create x){
    String secret=UUID.randomUUID()+"."+UUID.randomUUID();
    ApiClient c=new ApiClient();
    c.setId(UUID.randomUUID());
    c.setTenantId(tenant);
    c.setWorkspaceId(x.workspaceId());
    c.setClientId(x.clientId());
    c.setName(x.name());
    c.setSecretHash(hash(secret));
    c.setScopes(x.scopes()==null?"[]":x.scopes().toString());
    c.setExpiresAt(x.expiresAt());
    repo.save(c);
    return Map.of("id",c.getId(),
    "clientId",c.getClientId(),"secret",secret,"warning","Store this secret now; it cannot be retrieved later");
}
public record Create(UUID workspaceId,String clientId,String name,List<String> scopes,
Instant expiresAt){
}
public record Principal(ApiClient client){
}
public Optional<Principal>
authenticate(String clientId,
String secret){
    return repo.findByClientId(clientId).filter(c->"ACTIVE".equals(c.getStatus()) &&
    (c.getExpiresAt()==null||c.getExpiresAt().isAfter(Instant.now())) && MessageDigest.isEqual(c.getSecretHash().
    getBytes(StandardCharsets.UTF_8),
    hash(secret).getBytes(StandardCharsets.UTF_8))).map(c->{
        c.setLastUsedAt(Instant.now());
        repo.save(c);
        return new Principal(c);
        }
        );
        }
        }
