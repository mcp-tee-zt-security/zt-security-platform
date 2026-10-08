package com.zt.security.riskintelligence;

import com.zt.security.action.EvaluateModels.EvaluateRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1/security/control-loop/policy-tuning")
public class PolicyTuningController {
  private final PolicyTuningService service;
  PolicyTuningController(PolicyTuningService s){
      service=s;
  }
  record Propose(UUID profileId, UUID basePolicyId, String requestedBy){
  }
  @GetMapping("/candidates") List<Map<String,Object>> candidates(@RequestHeader("X-Tenant-Id") UUID t){
      return service.candidates(t);
      }
  @GetMapping("/proposals") List<Map<String,Object>> proposals(@RequestHeader("X-Tenant-Id") UUID t){
      return service.list(t);
      }
  @PostMapping("/proposals") Map<String,Object> propose(@RequestHeader("X-Tenant-Id") UUID t,
  @RequestBody Propose x){
      return service.propose(t,x.profileId(),x.basePolicyId(),
      x.requestedBy());
      }
  @PostMapping("/proposals/{id}/simulate") Map<String,Object> simulate(@RequestHeader("X-Tenant-Id") UUID t,
  @PathVariable UUID id,@RequestBody EvaluateRequest req){
      return service.simulate(t,
      id,req);
      }
  @PostMapping("/proposals/{id}/approve") Map<String,Object> approve(@RequestHeader("X-Tenant-Id") UUID t,
  @PathVariable UUID id,@RequestParam(defaultValue="dashboard-admin") String actor){
      return service.approve(t,id,actor);
      }
  @PostMapping("/proposals/{id}/reject") Map<String,Object> reject(@RequestHeader("X-Tenant-Id") UUID t,
  @PathVariable UUID id,@RequestParam(defaultValue="dashboard-admin") String actor){
      return service.reject(t,id,actor);
      }
}
