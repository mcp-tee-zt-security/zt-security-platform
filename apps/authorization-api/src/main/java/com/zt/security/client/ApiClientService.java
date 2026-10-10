package com.zt.security.client;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zt.security.common.TenantSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
@Service public class ApiClientService {
    final ApiClientRepository repo;
    final TenantSession session;
    final ObjectMapper mapper;
    final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;
    ApiClientService(ApiClientRepository r,TenantSession session,ObjectMapper mapper,org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc){repo=r;this.session=session;this.mapper=mapper;this.jdbc=jdbc;}
    @Transactional public List<ApiClient> list(UUID tenant){session.set(tenant);return repo.findByTenantId(tenant);}
    @Transactional public Map<String,Object> revoke(UUID tenant,UUID id){session.set(tenant);var c=repo.findById(id).filter(x->tenant.equals(x.getTenantId())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Client not found"));c.setStatus("REVOKED");repo.save(c);return Map.of("status","REVOKED");}
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
        @Transactional public Map<String,
Object> create(UUID tenant,Create x){
    session.set(tenant);
    if(x.clientId()==null||!x.clientId().matches("[A-Za-z0-9_.:-]{1,120}")||x.name()==null||x.name().isBlank()||x.name().length()>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid clientId and name required");
    if(repo.findByTenantIdAndClientId(tenant,x.clientId()).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Client already exists; use its existing secret");
    if(x.workspaceId()!=null&&!Integer.valueOf(1).equals(jdbc.queryForObject("select count(*) from workspaces where id=:workspace and tenant_id=:tenant",Map.of("workspace",x.workspaceId(),"tenant",tenant),Integer.class)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Workspace does not belong to tenant");
    if(x.expiresAt()!=null&&!x.expiresAt().isAfter(Instant.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Expiry must be in the future");
    if(x.scopes()!=null&&(x.scopes().size()>100||x.scopes().stream().anyMatch(v->v==null||v.isBlank()||v.length()>200)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid scopes");
    String secret=UUID.randomUUID()+"."+UUID.randomUUID();
    ApiClient c=new ApiClient();
    c.setId(UUID.randomUUID());
    c.setTenantId(tenant);
    c.setWorkspaceId(x.workspaceId());
    c.setClientId(x.clientId());
    c.setName(x.name());
    c.setSecretHash(hash(secret));
    try{c.setScopes(mapper.writeValueAsString(x.scopes()==null?List.of():x.scopes()));}catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid scopes");}
    c.setExpiresAt(x.expiresAt());
    try{repo.saveAndFlush(c);}catch(org.springframework.dao.DataIntegrityViolationException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"Client registration conflicts with existing data");}
    return Map.of("id",c.getId(),
    "clientId",c.getClientId(),"secret",secret,"warning","Store this secret now; it cannot be retrieved later");
}
public record Create(UUID workspaceId,String clientId,String name,List<String> scopes,
Instant expiresAt){
}
public record Principal(ApiClient client){
}
@Transactional public Optional<Principal>
authenticate(UUID tenant,String clientId,
String secret){
    session.set(tenant);
    return repo.findByTenantIdAndClientId(tenant,clientId).filter(c->"ACTIVE".equals(c.getStatus()) &&
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
