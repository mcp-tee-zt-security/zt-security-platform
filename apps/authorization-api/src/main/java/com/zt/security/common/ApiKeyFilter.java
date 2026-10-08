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
    final ApiClientService clients;
    ApiKeyFilter(ApiClientService c){
        clients=c;
    }
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,
FilterChain c)throws ServletException,IOException {
    if(SecurityContextHolder.getContext().getAuthentication()==null){
        String k=r.getHeader("X-API-Key");
        if(k!=null && k.equals(master)) SecurityContextHolder.getContext().
        setAuthentication(new UsernamePasswordAuthenticationToken("api-key",
        null,AuthorityUtils.createAuthorityList("ROLE_PLATFORM")));
        else if(k!=null){
            String cid=r.getHeader("X-Client-Id");
            if(cid!=null){
                var p=clients.authenticate(cid,
                k);
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
