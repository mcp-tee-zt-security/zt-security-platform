package com.zerotrust.security.platform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TraceFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        TraceContext.set(traceId);
        response.setHeader("X-Trace-Id", traceId);
        response.setHeader("X-Correlation-Id", correlationId(request, traceId));
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }

    private String correlationId(HttpServletRequest request, String fallback) {
        String correlationId = request.getHeader("X-Correlation-Id");
        return correlationId == null || correlationId.isBlank() ? fallback : correlationId;
    }
}
