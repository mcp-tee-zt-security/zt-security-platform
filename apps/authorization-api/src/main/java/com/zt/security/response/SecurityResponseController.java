package com.zt.security.response;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/security/responses") public class SecurityResponseController {
    @org.springframework.beans.factory.annotation.Autowired private com.zt.security.riskintelligence.RiskForecastService forecasts;
    final SecurityResponseService service;
    SecurityResponseController(SecurityResponseService s){
        service=s;
        }
 @PostMapping("/forecast-propose") List<Map<String,Object>> forecastPropose(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody Map<String,Object> b){
     String agent=String.valueOf(b.get("agent"));var forecast=forecasts.agent(t,agent,24,12);
     if(!"AVAILABLE".equals(forecast.get("dataQuality")))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Insufficient measured evidence for preventive control");
     var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
     return service.proposeFromForecast(t,agent,auth.getName(),((Number)forecast.get("forecastScore")).doubleValue(),((Number)forecast.get("highProbability")).doubleValue(),String.valueOf(forecast.get("recommendation")));
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
