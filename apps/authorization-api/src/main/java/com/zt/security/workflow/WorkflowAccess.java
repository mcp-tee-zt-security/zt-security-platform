package com.zt.security.workflow;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class WorkflowAccess {
    @Value("${zt.workflow.require-oidc:false}") private boolean requireOidc;
    @Value("${zt.security.mcp.audience:zt-mcp-gateway}") private String audience="zt-mcp-gateway";
    public boolean has(Authentication auth,String... roles){
        return auth!=null&&auth.getAuthorities().stream().anyMatch(a->Arrays.asList(roles).contains(a.getAuthority()));
    }
    public String human(Authentication auth,UUID tenant,UUID workspace){
        if(!(auth instanceof JwtAuthenticationToken jwt)||!auth.isAuthenticated()
            ||jwt.getToken().getSubject()==null||jwt.getToken().getSubject().isBlank()
            ||jwt.getToken().getExpiresAt()==null||!jwt.getToken().getExpiresAt().isAfter(java.time.Instant.now())
            ||!jwt.getToken().getAudience().contains(audience)
            ||!tenant.toString().equalsIgnoreCase(jwt.getToken().getClaimAsString("tenant_id"))
            ||workspace==null||!workspace.toString().equalsIgnoreCase(jwt.getToken().getClaimAsString("workspace_id"))
            ||!"HUMAN".equals(jwt.getToken().getClaimAsString("actor_type")))
            throw new AccessDeniedException("Human OIDC authentication with matching audience, tenant and workspace required");
        return jwt.getToken().getSubject();
    }
    public void scoped(Authentication auth,UUID tenant,UUID workspace){if(requireOidc)human(auth,tenant,workspace);}
    public void request(Authentication auth,UUID tenant,UUID workspace){
        human(auth,tenant,workspace);
        if(!has(auth,"ROLE_REQUESTER","ROLE_ADMIN","ROLE_PLATFORM"))throw new AccessDeniedException("Workflow requester role required");
    }
}
