package com.zt.security.riskintelligence;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1/security/control-loop")
public class ControlLoopFeedbackController {
  private final ControlLoopFeedbackService service;
  ControlLoopFeedbackController(ControlLoopFeedbackService s){
      service=s;
  }

  @GetMapping("/feedback")
  public List<Map<String,Object>> feedback(@RequestHeader("X-Tenant-Id") UUID tenant,
      @RequestParam(defaultValue="6") int observationHours){
    return service.overview(tenant,bounded(observationHours));
  }

  @GetMapping("/effectiveness")
  public List<Map<String,Object>> effectiveness(@RequestHeader("X-Tenant-Id") UUID tenant){
    return service.effectiveness(tenant);
  }

  @GetMapping("/recommendations")
  public List<Map<String,Object>> recommendations(@RequestHeader("X-Tenant-Id") UUID tenant,
      @RequestParam(required=false) String target){
    return service.recommendations(tenant,target);
  }

  @GetMapping("/feedback/{id}")
  public Map<String,Object> verify(@RequestHeader("X-Tenant-Id") UUID tenant,
      @PathVariable UUID id,@RequestParam(defaultValue="6") int observationHours){
    return service.verify(tenant,id,bounded(observationHours));
  }

  @PostMapping("/feedback/{id}/verify")
  public Map<String,Object> verifyAndPersist(@RequestHeader("X-Tenant-Id") UUID tenant,
      @PathVariable UUID id,@RequestParam(defaultValue="6") int observationHours){
    return service.verifyAndPersist(tenant,id,bounded(observationHours));
  }

  private int bounded(int hours){
      return Math.max(1,Math.min(hours,72));
  }
}
