package com.zt.security.mcp;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1/mcp")
public class McpGatewayController {
    private final McpGatewayService service;
    McpGatewayController(McpGatewayService service){
        this.service=service;
    }

    @GetMapping("/capabilities")
    public Map<String,Object> capabilities(@RequestHeader("X-Tenant-Id") UUID tenant,
                                            @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace){
        return service.capabilities(tenant, workspace);
    }
    @GetMapping("/tools")
    public List<Map<String,Object>> tools(@RequestHeader("X-Tenant-Id") UUID tenant){
        return service.listTools(tenant);
    }
    @PostMapping("/authorize")
    public Map<String,Object> authorize(@RequestHeader("X-Tenant-Id") UUID tenant,
                                         @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,
                                         @RequestBody McpGatewayService.McpAuthorizeRequest request){
                                             return service.authorize(tenant,workspace,request);
                                             }
    @PostMapping("/json-rpc")
    public Map<String,Object> jsonRpc(@RequestHeader("X-Tenant-Id") UUID tenant,
                                      @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,
                                      @RequestBody Map<String,Object> request){
                                          return service.jsonRpc(tenant,workspace,request);
                                          }
}
