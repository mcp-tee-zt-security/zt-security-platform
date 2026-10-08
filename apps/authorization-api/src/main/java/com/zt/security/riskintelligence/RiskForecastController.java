package com.zt.security.riskintelligence;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/security/agent-risk/forecast")
public class RiskForecastController{
 private final RiskForecastService service;
 RiskForecastController(RiskForecastService service){
     this.service=service;
 }
 @GetMapping("/overview") public Map<String,Object> overview(@RequestHeader("X-Tenant-Id") UUID tenant,
 @RequestParam(defaultValue="24") int windowHours,@RequestParam(defaultValue="12") int horizonHours){
     return service.overview(tenant,Math.max(1,Math.min(windowHours,168)),Math.max(1,
     Math.min(horizonHours,168)));
     }
 @GetMapping("/{agent}") public Map<String,Object> agent(@RequestHeader("X-Tenant-Id") UUID tenant,
 @PathVariable String agent,@RequestParam(defaultValue="24") int windowHours,
 @RequestParam(defaultValue="12") int horizonHours){
     return service.agent(tenant,
     agent,Math.max(1,Math.min(windowHours,168)),Math.max(1,Math.min(horizonHours,
     168)));
     }
}
