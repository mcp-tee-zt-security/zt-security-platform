package com.zt.security.identity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/identities") public class IdentityController {
    final IdentityRepository repo;
    final com.zt.security.common.TenantSession session;
    final com.fasterxml.jackson.databind.ObjectMapper mapper;
    IdentityController(IdentityRepository r,com.zt.security.common.TenantSession session,com.fasterxml.jackson.databind.ObjectMapper mapper){
        this.session=session;this.mapper=mapper;
        repo=r;
        }
        record Create(UUID tenantId,String externalId,String type,String name,
    Map<String,Object> attributes){
    }
    @GetMapping @org.springframework.transaction.annotation.Transactional List<Identity> all(@RequestHeader("X-Tenant-Id") UUID t){
        session.set(t);return repo.findByTenantId(t);
        }
        @PostMapping @org.springframework.transaction.annotation.Transactional
        @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AGENT_MANAGER')")
        Identity create(@RequestHeader("X-Tenant-Id") UUID tenant,@RequestBody Create x){
        session.set(tenant);
        if(x.externalId()==null||x.externalId().isBlank()||x.name()==null||x.name().isBlank()||x.type()==null||x.type().isBlank())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Identity type, external ID and name required");
        if(repo.findByTenantId(tenant).stream().anyMatch(i->x.externalId().equals(i.getExternalId())))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Identity already exists");
        Identity i=new Identity();
        i.setId(UUID.randomUUID());
        i.setTenantId(tenant);
        i.setExternalId(x.externalId());
        i.setIdentityType(x.type());
        i.setName(x.name());
        try{i.setAttributes(mapper.writeValueAsString(x.attributes()==null?Map.of():x.attributes()));}catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalArgumentException("Invalid attributes");}
        return repo.save(i);
    }
    }
