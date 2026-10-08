package com.zt.security.replay;
import com.zt.security.common.TenantSession;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/security/shadow-replay")
public class ShadowReplayController {
 private final ShadowReplayService service;
 private final TenantSession session;
 public ShadowReplayController(ShadowReplayService s,TenantSession ts){
     service=s;
     session=ts;
 }
 @PostMapping("/run") public ShadowReplayModels.ReplayResponse run(@RequestHeader("X-Tenant-Id") UUID tenant,
 @RequestBody ShadowReplayModels.ReplayRequest req){
     session.set(tenant);
     return service.replay(tenant,req);
     }
}
