package com.zt.security.tenant;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/tenants") public class TenantController {
    final TenantRepository repo;
    TenantController(TenantRepository r){
        repo=r;
    }
    record Create(String slug,String name){
    }
    @GetMapping List<Tenant> all(){
    return repo.findAll();
    }
    @PostMapping Tenant create(@RequestBody Create x){
    Tenant t=new Tenant();
    t.setId(UUID.randomUUID());
    t.setSlug(x.slug());
    t.setName(x.name());
    return repo.save(t);
    }
    }
