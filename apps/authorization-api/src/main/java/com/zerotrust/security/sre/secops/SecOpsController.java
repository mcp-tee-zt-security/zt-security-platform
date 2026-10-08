package com.zerotrust.security.sre.secops;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/v1/secops")
public class SecOpsController {
 private final SecOpsIncidentService service;
 public SecOpsController(SecOpsIncidentService service) {
     this.service=service;
 }
 @PostMapping("/incidents")
 public SecOpsIncident open(@RequestParam UUID tenantId,@RequestParam String incidentKey,@RequestParam String title,
  @RequestParam String severity,@RequestParam String source,@RequestParam(required=false) String owner,
  @RequestBody(required=false) String summary) {
      return service.open(tenantId,
      incidentKey,title,severity,source,owner,summary);
      }
 @GetMapping("/incidents")
 public List<SecOpsIncident> list(@RequestParam UUID tenantId) {
     return service.list(tenantId);
 }
 @PostMapping("/incidents/{incidentId}/response-actions")
 public SecOpsResponseAction request(@PathVariable UUID incidentId,@RequestParam UUID tenantId,
 @RequestParam String actionType,
  @RequestParam String requestedBy,@RequestParam(required=false) String rationale) {
  return service.requestResponse(tenantId,incidentId,actionType,requestedBy,rationale);
 }
 @PostMapping("/response-actions/{actionId}/approve")
 public SecOpsResponseAction approve(@PathVariable UUID actionId,@RequestParam UUID tenantId) {
     return service.approve(tenantId,actionId);
     }
 @GetMapping("/incidents/{incidentId}/response-actions")
 public List<SecOpsResponseAction> actions(@PathVariable UUID incidentId,@RequestParam UUID tenantId) {
  return service.actions(tenantId,incidentId);
 }
}
