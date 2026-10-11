package com.zt.security.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

@Component
public class TenantSecurityFilter extends OncePerRequestFilter {
  @org.springframework.beans.factory.annotation.Autowired private com.zt.security.workspace.WorkspaceAccess workspaceAccess;
  public static final String TENANT = "zt.tenant";
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,
  FilterChain chain)throws ServletException,IOException {
    String header=req.getHeader("X-Tenant-Id");
    Authentication a=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    String claim=null;
    if(a instanceof JwtAuthenticationToken jwt) claim=jwt.getToken().getClaimAsString("tenant_id");
    String tenant=claim!=null?claim:header;
    if(claim!=null && header!=null && !claim.equalsIgnoreCase(header)) {
        res.sendError(403,"tenant mismatch");
        return;
    }
    if(a instanceof JwtAuthenticationToken jwt){
        String workspaceClaim=jwt.getToken().getClaimAsString("workspace_id");
        boolean discovery="GET".equals(req.getMethod())&&"/v1/workspaces/accessible".equals(req.getRequestURI());
        if("HUMAN".equals(jwt.getToken().getClaimAsString("actor_type"))&&!discovery){
            try{
                UUID t=UUID.fromString(tenant),w=UUID.fromString(req.getHeader("X-Workspace-Id"));
                workspaceAccess.require(a,t,w);
                req.setAttribute(com.zt.security.workspace.WorkspaceAccess.VERIFIED_SCOPE,new com.zt.security.workspace.WorkspaceAccess.Verified(t,w,jwt.getToken().getSubject(),jwt.getToken().getTokenValue()));
            }catch(IllegalArgumentException|NullPointerException ex){res.sendError(400,"A valid selected workspace is required");return;}
            catch(org.springframework.security.access.AccessDeniedException ex){res.sendError(403,"No access to selected workspace");return;}
        }else if(!discovery&&workspaceClaim!=null&&!workspaceClaim.equalsIgnoreCase(req.getHeader("X-Workspace-Id"))){
            res.sendError(403,"workspace mismatch");return;
        }
    }
    if(tenant!=null) {
        try {
            UUID.fromString(tenant);
            req.setAttribute(TENANT,
            UUID.fromString(tenant));
            }
            catch(IllegalArgumentException e){
                res.sendError(400,
            "invalid tenant id");
            return;
            }
            }
    chain.doFilter(req,res);
  }
}
