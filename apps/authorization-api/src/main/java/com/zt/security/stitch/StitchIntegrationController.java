package com.zt.security.stitch;

import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import java.util.*;

@RestController @RequestMapping({"/v1/integrations/retrieval","/v1/integrations/stitch"})
@ConditionalOnProperty(name="zt.stitch.enabled",havingValue="true")
public class StitchIntegrationController {
    @org.springframework.beans.factory.annotation.Value("${zt.stitch.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}") private String allowedOrigins;
    private final StitchIntegrationService service;
    public StitchIntegrationController(StitchIntegrationService service){this.service=service;}
    @GetMapping("/capabilities") public Object capabilities(){return Map.of("contractVersion","2","humanAuth","OIDC JWT","aiAuth","OIDC JWT or registered service client plus human-issued delegation session","index","PostgreSQL full text / bounded cosine ranking over authorized chunks","policyAuthority","ZT Policy Studio: active ALLOW required, DENY overrides; source user ACL is intersected","connectionRegistration","One registered connector subject per workspace; generic /v1/integrations/retrieval API");}
    @GetMapping("/context") public Object context(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication a){return service.verify(t,w,a);}
    @GetMapping("/calls") public Object calls(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication a){return service.calls(t,w,a);}
    @GetMapping("/source-state") public Object state(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication a){return service.state(t,w,a);}
    @PostMapping("/source-state") public Object checkpoint(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody StitchContracts.Checkpoint x,Authentication a){return service.checkpoint(t,w,x,a);}
    @PostMapping("/resources") public Object sync(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody StitchContracts.Resource x,Authentication a){return service.sync(t,w,x,a);}
    @PostMapping("/subjects") public Object subjects(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody StitchContracts.Subject x,Authentication a){return service.subjects(t,w,x,a);}
    @PostMapping("/revoked-tokens") public Object tokenRevocation(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody StitchContracts.Revocation x,Authentication a){return service.revokeToken(t,w,x,a);}
    @DeleteMapping("/resources/{id}") public Object delete(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@PathVariable String id,Authentication a){return service.delete(t,w,id,a);}
    @PostMapping("/sessions") public Object delegate(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody StitchContracts.Delegate x,Authentication a){return service.delegate(t,w,x.aiSubject(),a);}
    @DeleteMapping("/sessions/{id}") public Object revoke(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@PathVariable UUID id,Authentication a){return service.revoke(t,w,id,a);}
    @PostMapping("/retrieve") public Object read(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody StitchContracts.Retrieve x,Authentication a){return service.retrieve(t,w,x,a);}
    @PostMapping("/children") public Object children(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody StitchContracts.Retrieve x,Authentication a){return service.children(t,w,x,a);}
    @PostMapping("/download") public org.springframework.http.ResponseEntity<byte[]> download(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody StitchContracts.Retrieve x,Authentication a){byte[] data=service.download(t,w,x,a);if(data==null)throw new org.springframework.security.access.AccessDeniedException("Download not authorized");return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM).header("Content-Disposition","attachment; filename=\"download.bin\"").body(data);}
    @PostMapping("/search") public Object search(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody StitchContracts.Search x,Authentication a){return service.search(t,w,x,a);}
    @PostMapping("/mcp") public Object mcp(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@RequestHeader(value="MCP-Protocol-Version",required=false) String protocol,@RequestHeader(value="X-ZT-Delegation",required=false) UUID delegation,@RequestHeader(value="Origin",required=false) String origin,@RequestBody com.fasterxml.jackson.databind.JsonNode x,Authentication a){
        if(origin!=null&&!java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim).anyMatch(origin::equals))throw new org.springframework.security.access.AccessDeniedException("MCP origin not allowed");
        if(protocol!=null&&!"2025-06-18".equals(protocol))throw new IllegalArgumentException("Supported MCP version: 2025-06-18");
        if(x.isObject()&&"2.0".equals(x.path("jsonrpc").asText())&&!x.has("id")&&"notifications/initialized".equals(x.path("method").asText())){service.verify(t,w,a);return org.springframework.http.ResponseEntity.accepted().build();}
        try{return service.mcp(t,w,x,delegation,a);}catch(IllegalArgumentException e){return java.util.Map.of("jsonrpc","2.0","id",x.hasNonNull("id")?x.get("id"):com.fasterxml.jackson.databind.node.NullNode.getInstance(),"error",java.util.Map.of("code",-32602,"message","Invalid retrieval request"));}
    }
    @GetMapping("/deletion-events") public Object events(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication a){return service.events(t,w,a);}
    @PostMapping("/deletion-events/{id}/ack") public Object ack(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@PathVariable UUID id,@Valid @RequestBody StitchContracts.Receipt x,Authentication a){return service.ack(t,w,id,x.receiptId(),a);}
    @PutMapping("/retention") public Object retention(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody StitchContracts.Retention x,Authentication a){return service.retention(t,w,x.receiptTtlDays(),a);}
    @PostMapping("/maintenance") public Object maintenance(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication a){return service.maintenance(t,w,a);}
}
