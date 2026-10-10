package com.zt.security.mcp;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1/mcp")
public class McpAgentBindingController {
    private final McpAgentBindingService bindings;
    private final McpInvocationService calls;
    private final McpGatewayProperties properties;
    McpAgentBindingController(McpAgentBindingService bindings,McpInvocationService calls,McpGatewayProperties properties) {
        this.bindings=bindings;this.calls=calls;this.properties=properties;
    }
    @GetMapping("/agent-bindings")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','SOC_ANALYST','AGENT_MANAGER')")
    public List<Map<String,Object>> list(@RequestHeader("X-Tenant-Id") UUID tenant,
        @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace) { return bindings.list(tenant,workspace); }

    @PostMapping("/agent-bindings")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
    public Map<String,Object> save(@RequestHeader("X-Tenant-Id") UUID tenant,
        @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,
        @RequestBody McpAgentBindingService.Registration registration,Authentication auth) {
        bindings.save(tenant,workspace,registration,auth.getName());return Map.of("saved",true);
    }
    @GetMapping("/identity")
    public Map<String,Object> identity(@RequestHeader("X-Tenant-Id") UUID tenant,
        @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,Authentication auth,@RequestHeader(value="X-ZT-Agent-Id",required=false) String selectedAgent) {
        var actor=McpActor.from(auth,tenant,workspace,properties).selecting(selectedAgent);
        var principal=bindings.resolve(tenant,workspace,actor);
        return Map.of("authenticatedSubject",actor.subject(),"policySubject",principal.subject(),
            "agentLinked",!"legacy".equals(principal.revision()));
    }
    @GetMapping("/agent-options")
    public List<Map<String,Object>> options(@RequestHeader("X-Tenant-Id") UUID tenant,
        @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,Authentication auth) {
        return bindings.options(tenant,workspace,McpActor.from(auth,tenant,workspace,properties));
    }
    @GetMapping("/agent-calls")
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','SOC_ANALYST','AGENT_MANAGER','APPROVER')")
    public List<Map<String,Object>> agentCalls(@RequestHeader("X-Tenant-Id") UUID tenant,
        @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,@RequestParam String subject) {
        return calls.agentHistory(tenant,workspace,subject);
    }
}
