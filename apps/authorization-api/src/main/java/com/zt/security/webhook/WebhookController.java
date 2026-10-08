package com.zt.security.webhook;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/webhooks") public class WebhookController {
    final WebhookRepository repo;
    WebhookController(WebhookRepository r){
        repo=r;
    }
    record Create(UUID workspaceId,String name,String url,String secret,
List<String> eventTypes){
}
@GetMapping List<Webhook> list(@RequestHeader("X-Tenant-Id") UUID t){
    return repo.findByTenantIdAndStatus(t,"ACTIVE");
    }
    @PostMapping Webhook create(@RequestHeader("X-Tenant-Id") UUID t,
@RequestBody Create x){
    Webhook w=new Webhook();
    w.setId(UUID.randomUUID());
    w.setTenantId(t);
    w.setWorkspaceId(x.workspaceId());
    w.setName(x.name());
    w.setUrl(x.url());
    w.setSecret(x.secret());
    w.setEventTypes(x.eventTypes()==null?"[]":x.eventTypes().toString());
    return repo.save(w);
    }
    @PostMapping("/{id}/disable") Map<String,Object> disable(@RequestHeader("X-Tenant-Id") UUID t,
@PathVariable UUID id){
    var w=repo.findById(id).orElseThrow();
    if(!t.equals(w.getTenantId()))throw new
IllegalArgumentException("tenant mismatch");
    w.setStatus("DISABLED");
    repo.save(w);
    return Map.of("status","DISABLED");
}
}
