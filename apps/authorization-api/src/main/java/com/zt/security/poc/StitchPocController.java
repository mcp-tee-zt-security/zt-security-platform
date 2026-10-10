package com.zt.security.poc;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

@RestController @RequestMapping("/v1/poc/stitch")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="zt.poc.stitch.enabled",havingValue="true")
public class StitchPocController {
    private final StitchPocService service;
    public StitchPocController(StitchPocService service){this.service=service;}
    public record Access(@NotBlank @Pattern(regexp="ALLOW|DENY") String agentAccess){}
    public record Read(@NotBlank @Size(max=80) String documentId){}
    public record Search(@NotBlank @Size(max=100) String query){}
    public record Channel(@NotBlank @Size(max=80) String channelId){}
    @GetMapping("/context") public Object context(Authentication auth){return service.context(auth);}
    @GetMapping("/management") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Object management(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication auth){return service.management(t,w,auth);}
    @PostMapping("/setup") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Object setup(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication a){return service.setup(t,w,a);}
    @PutMapping("/areas/{id}/access") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Object access(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@PathVariable String id,@Valid @RequestBody Access x,Authentication a){return service.access(t,w,id,x.agentAccess(),a);}
    @PostMapping("/readDocument") public Object read(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody Read x,Authentication a){return service.read(t,w,x.documentId(),a);}
    @PostMapping("/searchDocuments") public Object search(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody Search x,Authentication a){return service.search(t,w,x.query(),a);}
    @PostMapping("/listChannelMessages") public Object channel(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody Channel x,Authentication a){return service.channel(t,w,x.channelId(),a);}
    @GetMapping("/calls") public Object calls(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication a){return service.calls(t,w,a);}
}
