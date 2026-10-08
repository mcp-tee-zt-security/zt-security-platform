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
        if(workspaceClaim!=null&&!workspaceClaim.equalsIgnoreCase(req.getHeader("X-Workspace-Id"))){
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
