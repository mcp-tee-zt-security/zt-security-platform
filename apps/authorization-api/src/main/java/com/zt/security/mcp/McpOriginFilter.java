package com.zt.security.mcp;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class McpOriginFilter extends OncePerRequestFilter {
    private final McpGatewayProperties properties;
    McpOriginFilter(McpGatewayProperties properties) { this.properties = properties; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/v1/mcp/")) {
            String origin = request.getHeader("Origin");
            if (origin != null && !properties.getAllowedOrigins().contains(origin)) {
                response.setStatus(403); response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"MCP_ORIGIN_REJECTED\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
