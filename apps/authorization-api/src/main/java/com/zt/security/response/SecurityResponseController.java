package com.zt.security.response;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/security/responses") public class SecurityResponseController {
    final SecurityResponseService service;
    SecurityResponseController(SecurityResponseService s){
        service=s;
        }
 @PostMapping("/forecast-propose") List<Map<String,Object>> forecastPropose(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody Map<String,Object> b){
     return service.proposeFromForecast(t,
     String.valueOf(b.get("agent")),String.valueOf(b.getOrDefault("requestedBy",
     "dashboard-admin")),Double.parseDouble(String.valueOf(b.getOrDefault("forecastScore",
     0))),Double.parseDouble(String.valueOf(b.getOrDefault("highProbability",
     0))),String.valueOf(b.getOrDefault("recommendation","MONITOR_AND_REASSESS")));
 }
 @GetMapping List<Map<String,Object>> list(@RequestHeader("X-Tenant-Id") UUID t){
     return service.list(t);
 }
 @PostMapping("/propose") List<Map<String,Object>> propose(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody Map<String,Object> b){
     return service.propose(t,UUID.fromString(String.valueOf(b.get("assessmentId"))),
     String.valueOf(b.getOrDefault("requestedBy","dashboard-admin")));
     }
 @PostMapping("/{id}/approve") Map<String,Object> approve(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@RequestParam(defaultValue="security-approver") String approver){
     return service.approve(t,id,approver);
     }
 @PostMapping("/{id}/reject") Map<String,Object> reject(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@RequestParam(defaultValue="security-approver") String approver,
 @RequestBody(required=false) Map<String,Object> b){
     return service.reject(t,
     id,approver,String.valueOf(b==null?"Rejected":b.getOrDefault("reason",
     "Rejected")));
     }
 @PostMapping("/{id}/execute") Map<String,Object> execute(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@RequestParam(defaultValue="security-operator") String executor){
     return service.execute(t,id,executor);
     }
}
