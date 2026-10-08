package com.zt.security.enterprise.runtime;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/enterprise/sso") public class SsoPreflightController {
    private final SsoPreflightService service;
    public SsoPreflightController(SsoPreflightService service){
        this.service=service;
        }
 @PostMapping("/preflight/{provider}") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')") public Map<String,
 Object> preflight(@RequestHeader("X-Tenant-Id") UUID t,@PathVariable String provider){
     return service.validate(t,provider);
     }
 @PostMapping("/login/start") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')") public Map<String,
 Object> start(@RequestHeader("X-Tenant-Id") UUID t,@RequestParam String provider,
 @RequestParam String redirectUri,@RequestParam(required=false,defaultValue="openid profile email") String scopes){
     return service.start(t,provider,redirectUri,scopes);
     }
 @GetMapping("/login/callback") public Map<String,Object> callback(@RequestParam String state,
 @RequestParam String code){
     return service.callback(state,code);
 }
}
