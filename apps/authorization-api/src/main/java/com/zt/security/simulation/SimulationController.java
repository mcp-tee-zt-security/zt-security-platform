package com.zt.security.simulation;
import com.zt.security.action.EvaluateModels.EvaluateRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/policies") public class SimulationController {
    final PolicySimulator simulator;
    SimulationController(PolicySimulator s){
        simulator=s;
        }
        record Draft(String policyText,EvaluateRequest request){
    }
    record Compare(List<UUID> policyIds,EvaluateRequest request){
    }
 @PostMapping("/{id}/simulate") Object simulate(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@RequestBody EvaluateRequest req){
     return simulator.simulate(t,
     req,id);
     }
 @PostMapping("/simulate-draft") Object simulateDraft(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody Draft x){
     return simulator.simulateDraft(t,x.request(),x.policyText());
 }
 @PostMapping("/simulate-compare") Object compare(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody Compare x){
     return simulator.compare(t,x.request(),x.policyIds());
 }
}
