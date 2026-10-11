package com.zt.security.workflow;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1/session")
public class WorkflowSessionController {
    private final WorkflowAccess access;
    public WorkflowSessionController(WorkflowAccess access){this.access=access;}
    @GetMapping public Object session(@RequestHeader("X-Tenant-Id") UUID tenant,@RequestHeader("X-Workspace-Id") UUID workspace,Authentication auth){
        String subject=access.human(auth,tenant,workspace);
        var jwt=(JwtAuthenticationToken)auth;
        return Map.of("subject",subject,"username",Optional.ofNullable(jwt.getToken().getClaimAsString("preferred_username")).orElse(subject),
            "roles",auth.getAuthorities().stream().map(a->a.getAuthority()).toList(),
            "canRequest",access.has(auth,"ROLE_REQUESTER","ROLE_ADMIN","ROLE_PLATFORM"),
            "canApprove",access.has(auth,"ROLE_APPROVER","ROLE_ADMIN","ROLE_PLATFORM"),
            "canExecute",access.has(auth,"ROLE_EXECUTOR","ROLE_ADMIN","ROLE_PLATFORM"),
            "canConfigure",access.has(auth,"ROLE_ADMIN","ROLE_PLATFORM"));
    }
}
