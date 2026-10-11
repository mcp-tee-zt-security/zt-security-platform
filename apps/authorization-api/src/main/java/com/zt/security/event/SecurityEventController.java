package com.zt.security.event;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/v1/events")
public class SecurityEventController {
 private final SecurityActivityLedger ledger;
 public SecurityEventController(SecurityActivityLedger ledger){this.ledger=ledger;}
 @GetMapping public List<Map<String,Object>> list(@RequestHeader("X-Tenant-Id") UUID tenant,@RequestHeader("X-Workspace-Id") UUID workspace,@RequestParam(defaultValue="24") int hours,@RequestParam(defaultValue="100") int limit){return ledger.recent(tenant,workspace,Instant.now().minusSeconds(Math.max(1,Math.min(hours,720))*3600L),limit);}
 @GetMapping("/summary") public Object summary(@RequestHeader("X-Tenant-Id") UUID tenant,@RequestHeader("X-Workspace-Id") UUID workspace,@RequestParam(defaultValue="24") int hours){return ledger.summary(tenant,workspace,Instant.now().minusSeconds(Math.max(1,Math.min(hours,720))*3600L));}
 @GetMapping("/contract") public Object contract(){return Map.of("schemaVersion",1,"stages",List.of("AUTHORIZATION","EXECUTION","LIFECYCLE","OBSERVATION"),"riskScore","null means not measured","analysis","One decisive AI authorization per request; lifecycle events are not decisions","extension","Internal domain producers publish CommonSecurityEvent; clients cannot publish trusted decisions");}
}
