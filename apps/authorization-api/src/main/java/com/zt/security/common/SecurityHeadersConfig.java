package com.zt.security.common;
import org.springframework.context.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
@Configuration public class SecurityHeadersConfig {
    @Bean public OncePerRequestFilter securityHeaders(){
        return new OncePerRequestFilter(){
            protected void doFilterInternal(HttpServletRequest r,
            HttpServletResponse s,FilterChain c)throws ServletException,IOException{
                s.setHeader("X-Content-Type-Options","nosniff");
                s.setHeader("X-Frame-Options",
                "DENY");
                s.setHeader("Referrer-Policy","no-referrer");
                s.setHeader("Cache-Control",
                "no-store");
                s.setHeader("Permissions-Policy","geolocation=(),camera=(),microphone=()");
                c.doFilter(r,s);
                }
                }
                ;
                }
                }
