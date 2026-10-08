package com.zt.security.settings;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/settings") public class TenantSettingsController {
    final TenantSettingsRepository repo;
    TenantSettingsController(TenantSettingsRepository r){
        repo=r;
        }
        @GetMapping TenantSettings get(@RequestHeader("X-Tenant-Id") UUID t){
        return repo.findById(t).orElseGet(()->{
            var x=new TenantSettings();x.setTenantId(t);
            return repo.save(x);
            }
            );
            }
            @PutMapping TenantSettings update(@RequestHeader("X-Tenant-Id") UUID t,
    @RequestBody TenantSettings x){
        x.setTenantId(t);
        return repo.save(x);
    }
    }
