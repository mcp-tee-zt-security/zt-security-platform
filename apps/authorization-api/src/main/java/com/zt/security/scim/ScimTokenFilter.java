package com.zt.security.scim;
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
import java.util.*;
@Component public class ScimTokenFilter extends OncePerRequestFilter {
    @Value("${zt.security.scim.tokens:}") String configuredTokens;
    @Override protected void
doFilterInternal(HttpServletRequest r,
    HttpServletResponse s,FilterChain c)throws ServletException,IOException {
        if(r.getRequestURI().startsWith("/scim/v2/")&&SecurityContextHolder.getContext().getAuthentication()==null){
            String h=r.getHeader("Authorization");
            String tenant=r.getHeader("X-Tenant-Id");
            if(h!=null&&h.startsWith("Bearer ")&&tenant!=null&&isAllowed(tenant,
            h.substring(7)))SecurityContextHolder.getContext().setAuthentication(new
UsernamePasswordAuthenticationToken("scim:"+
            tenant,
            null,AuthorityUtils.createAuthorityList("ROLE_SCIM")));
            }
            c.doFilter(r,s);
    }
 private boolean isAllowed(String tenant,String token){
     if(configuredTokens==null||
     configuredTokens.isBlank())return false;
     for(String entry:configuredTokens.split(";")){
         String[] p=entry.split(":",
         2);
         if(p.length==2&&tenant.equalsIgnoreCase(p[0])&&constantTimeEquals(p[1],
         token))return true;
         }
         return false;
         }
 private boolean constantTimeEquals(String a,String b){
     return java.security.MessageDigest.isEqual(a.getBytes(java.
     nio.charset.StandardCharsets.UTF_8),
     b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
     }
}
