package com.zt.security.identity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/identities") public class IdentityController {
    final IdentityRepository repo;
    IdentityController(IdentityRepository r){
        repo=r;
        }
        record Create(UUID tenantId,String externalId,String type,String name,
    Map<String,Object> attributes){
    }
    @GetMapping List<Identity> all(@RequestHeader("X-Tenant-Id") UUID t){
        return repo.findByTenantId(t);
        }
        @PostMapping Identity create(@RequestBody Create x){
        Identity i=new Identity();
        i.setId(UUID.randomUUID());
        i.setTenantId(x.tenantId());
        i.setExternalId(x.externalId());
        i.setIdentityType(x.type());
        i.setName(x.name());
        i.setAttributes(x.attributes()==null?"{}":x.attributes().toString());
        return repo.save(i);
    }
    }
