package com.zt.security.incident;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/security/cases")
public class SecurityCaseController {
 private final SecurityCaseService service;
 SecurityCaseController(SecurityCaseService s){
     service=s;
 }
 @GetMapping List<Map<String,Object>> list(@RequestHeader("X-Tenant-Id") UUID t){
     return service.list(t);
 }
 @GetMapping("/{id}") Map<String,Object> detail(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id){
     return service.detail(t,id);
 }
 @PostMapping("/from-response/{id}") Map<String,Object> fromResponse(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@RequestBody(required=false) Map<String,Object> b){
     return service.createFromResponse(t,id,String.valueOf(b==null?"dashboard-admin":b.getOrDefault("requestedBy",
     "dashboard-admin")));
     }
 @PostMapping("/{id}/status") SecurityCase status(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@RequestParam String value,@RequestParam(defaultValue="dashboard-admin") String actor){
     return service.updateStatus(t,id,value,actor);
     }
 @PostMapping("/{id}/evidence") CaseEvidence evidence(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@RequestBody Map<String,Object> b){
     return service.addEvidence(t,
     id,String.valueOf(b.getOrDefault("type","NOTE")),String.valueOf(b.getOrDefault("sourceRef",
     "manual")),b.getOrDefault("payload",Map.of()),String.valueOf(b.getOrDefault("createdBy",
     "dashboard-admin")));
     }
 @PostMapping("/{id}/handoff") Map<String,Object> handoff(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@RequestBody(required=false) Map<String,Object> b){
     return service.handoff(t,id,String.valueOf(b==null?"GENERIC_SOAR":b.getOrDefault("connector",
     "GENERIC_SOAR")),String.valueOf(b==null?"dashboard-admin":b.getOrDefault("actor",
     "dashboard-admin")));
     }
}
