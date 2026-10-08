package com.zt.security.runtime;
import com.zt.security.action.EvaluateModels;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/runtime") public class RuntimeSecurityController {
    private final RuntimeSecurityService service;
    RuntimeSecurityController(RuntimeSecurityService s){
        service=s;
        }
@PostMapping("/sessions") public Map<String,Object> start(@RequestHeader("X-Tenant-Id") UUID t,
@RequestHeader(value="X-Workspace-Id",required=false) UUID w,@RequestBody RuntimeSecurityService.StartRequest r){
    return service.start(t,w,r);
    }
@GetMapping("/sessions") public List<Map<String,Object>> list(@RequestHeader("X-Tenant-Id") UUID t){
    return service.list(t);
    }
@GetMapping("/sessions/{id}") public Map<String,Object> detail(@RequestHeader("X-Tenant-Id") UUID t,
@PathVariable UUID id){
    return service.detail(t,id);
}
@PostMapping("/sessions/{id}/check") public EvaluateModels.EvaluateResponse check(@RequestHeader("X-Tenant-Id") UUID t,
@RequestHeader(value="X-Workspace-Id",required=false) UUID w,@RequestHeader(value="Idempotency-Key",
required=false) String idem,@PathVariable UUID id,@RequestBody EvaluateModels.EvaluateRequest r){
    return service.check(t,w,id,idem,r);
    }
@PostMapping("/sessions/{id}/end") public Map<String,Object> end(@RequestHeader("X-Tenant-Id") UUID t,
@PathVariable UUID id){
    return service.end(t,id);
}
}
