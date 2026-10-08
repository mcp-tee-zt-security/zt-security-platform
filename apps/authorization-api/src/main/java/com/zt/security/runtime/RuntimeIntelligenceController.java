package com.zt.security.runtime;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/runtime/intelligence")
public class RuntimeIntelligenceController {
 private final RuntimeIntelligenceService service;
 RuntimeIntelligenceController(RuntimeIntelligenceService s){
     service=s;
     }
 @GetMapping("/overview") public Map<String,Object> overview(@RequestHeader("X-Tenant-Id") UUID t){
     return service.overview(t);
     }
 @GetMapping("/{agent}") public Map<String,Object> agent(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable String agent){
     return service.agent(t,agent);
 }
}
