package com.zt.security.common;
import com.zt.security.client.ApiClientService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.
core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
@Component public class ApiKeyFilter extends OncePerRequestFilter {
    @Value("${zt.security.api-key:dev-master-key}")
String master;
    @Value("${zt.security.allow-development-api-key:true}") boolean allowDevelopmentKey=true;
    @Value("${zt.security.policy-bundle-key:}") String policyBundleKey="";
    @Value("${zt.security.policy-bundle-tenant:11111111-1111-1111-1111-111111111111}") String policyBundleTenant="11111111-1111-1111-1111-111111111111";
    @Value("${zt.security.policy-bundle-workspace:88888888-8888-8888-8888-888888888801}") String policyBundleWorkspace="88888888-8888-8888-8888-888888888801";
    final ApiClientService clients;
    ApiKeyFilter(ApiClientService c){
        clients=c;
    }
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,
FilterChain c)throws ServletException,IOException {
    // A bearer request must never inherit privileges from a simultaneously supplied API key.
    if(SecurityContextHolder.getContext().getAuthentication()==null&&r.getHeader("Authorization")==null){
        String k=r.getHeader("X-API-Key");
        if(k!=null&&!policyBundleKey.isBlank()&&k.equals(policyBundleKey)&&"GET".equals(r.getMethod())&&"/v1/policy-bundles/fast".equals(r.getRequestURI())
            &&policyBundleTenant.equalsIgnoreCase(r.getHeader("X-Tenant-Id"))&&policyBundleWorkspace.equalsIgnoreCase(r.getHeader("X-Workspace-Id"))){
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("policy-bundle-reader",null,AuthorityUtils.createAuthorityList("ROLE_POLICY_READER")));
        }
        else if(allowDevelopmentKey && k!=null && k.equals(master)) SecurityContextHolder.getContext().
        setAuthentication(new UsernamePasswordAuthenticationToken("api-key",
        null,AuthorityUtils.createAuthorityList("ROLE_PLATFORM")));
        else if(k!=null){
            String cid=r.getHeader("X-Client-Id");
            if(cid!=null){
                java.util.UUID clientTenant;
                try{clientTenant=java.util.UUID.fromString(r.getHeader("X-Tenant-Id"));}catch(Exception ex){s.sendError(400,"Valid X-Tenant-Id required");return;}
                var p=clients.authenticate(clientTenant,cid,k);
                if(p.isPresent()){
                    var pc=p.get().client();
                    if(r.getHeader("X-Tenant-Id")==null){
                        s.sendError(400,"X-Tenant-Id required for service client");
                        return;
                        }
                    if(!pc.getTenantId().toString().equalsIgnoreCase(r.getHeader("X-Tenant-Id"))){
                        s.sendError(403,"service client tenant mismatch");return;
                    }
                    if(pc.getWorkspaceId()!=null&&!pc.getWorkspaceId().toString().equalsIgnoreCase(r.getHeader("X-Workspace-Id"))){
                        s.sendError(403,"service client workspace mismatch");return;
                    }
                        SecurityContextHolder.getContext().setAuthentication(new
UsernamePasswordAuthenticationToken("client:"+
                        pc.getClientId(),
                    null,AuthorityUtils.createAuthorityList("ROLE_SERVICE_CLIENT")));
                    }
                    }
                    }
                    }
                    c.doFilter(r,
    s);
    }
    }
