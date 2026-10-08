package com.zt.security.siem;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/siem") public class SiemController {
    final SiemService service;
    public SiemController(SiemService s){
        service=s;
    }
    record Create(String name,String endpoint,String secretRef,Boolean enabled,
String eventTypes){
}
 @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')")
 @GetMapping("/sinks") public List<SiemSink> list(@RequestHeader("X-Tenant-Id") UUID t){
     return service.list(t);
 }
 @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
 @PostMapping("/sinks") public SiemSink create(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody Create x){
     SiemSink s=new SiemSink();
     s.setName(x.name());
     s.setEndpoint(x.endpoint());
     s.setSecretRef(x.secretRef());
     s.setEnabled(x.enabled()==null||x.enabled());
     s.setEventTypes(x.eventTypes()==null?"[]":x.eventTypes());
     return service.save(t,
     s);
     }
}
