package com.zt.security.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
 private final StringRedisTemplate redis;
 private final int limit;
 private final Duration window;
 RateLimitFilter(StringRedisTemplate r,@Value("${zt.security.rate-limit.requests-per-minute:120}") int l,
 @Value("${zt.security.rate-limit.window-seconds:60}") long seconds){
     redis=r;
     limit=l;
     window=Duration.ofSeconds(seconds);
     }
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,
 FilterChain chain)throws ServletException,IOException{
   String principal=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()==
   null?"anonymous":org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().
   getName();
   String tenant=req.getHeader("X-Tenant-Id");
   String key="rl:"+(tenant==null?"global":tenant)+":"+principal;
   Long count=redis.opsForValue().increment(key);
   if(count!=null && count==1) redis.expire(key,window);
   res.setHeader("X-RateLimit-Limit",String.valueOf(limit));
   res.setHeader("X-RateLimit-Remaining",
   String.valueOf(Math.max(0,limit-(count==null?0:count))));
   if(count!=null && count>limit){
       res.setStatus(429);
       res.setHeader("Retry-After",
       String.valueOf(window.toSeconds()));
       res.getWriter().write("rate limit exceeded");
       return;
       }
       chain.doFilter(req,res);
 }
}
