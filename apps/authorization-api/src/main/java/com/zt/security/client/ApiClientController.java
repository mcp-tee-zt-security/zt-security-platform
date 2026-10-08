package com.zt.security.client;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/clients") public class ApiClientController {
    final ApiClientService service;
    final ApiClientRepository repo;
    ApiClientController(ApiClientService s,
    ApiClientRepository r){
        service=s;
        repo=r;
    }
    @GetMapping List<ApiClient> list(@RequestHeader("X-Tenant-Id") UUID t){
        return repo.findByTenantId(t);
        }
        @PostMapping Map<String,Object> create(@RequestHeader("X-Tenant-Id") UUID t,
    @RequestBody ApiClientService.Create x){
        return service.create(t,x);
    }
    @PostMapping("/{id}/revoke") Map<String,
    Object> revoke(@RequestHeader("X-Tenant-Id") UUID t,@PathVariable UUID id){
        var c=repo.findById(id).orElseThrow();
        if(!t.equals(c.getTenantId()))throw new
IllegalArgumentException("tenant mismatch");
        c.setStatus("REVOKED");
        repo.save(c);
        return Map.of("status","REVOKED");
    }
    }
