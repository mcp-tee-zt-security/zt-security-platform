package com.zt.security.client;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('PLATFORM','ADMIN')")
@RestController @RequestMapping("/v1/clients") public class ApiClientController {
    final ApiClientService service;
    final ApiClientRepository repo;
    ApiClientController(ApiClientService s,
    ApiClientRepository r){
        service=s;
        repo=r;
    }
    @GetMapping List<ApiClient> list(@RequestHeader("X-Tenant-Id") UUID t){
        return service.list(t);
        }
        @PostMapping Map<String,Object> create(@RequestHeader("X-Tenant-Id") UUID t,
    @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,@RequestBody ApiClientService.Create x){
        if(workspace!=null&&!workspace.equals(x.workspaceId()))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Workspace header and body must match");
        return service.create(t,x);
    }
    @PostMapping("/{id}/revoke") Map<String,
    Object> revoke(@RequestHeader("X-Tenant-Id") UUID t,@PathVariable UUID id){
        return service.revoke(t,id);
    }
    }
