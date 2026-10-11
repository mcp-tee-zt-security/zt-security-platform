package com.zt.security.behavior;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@RestController
@RequestMapping("/v1/security-graph")
public class SecurityGraphController {
    private final SecurityGraphService service;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    SecurityGraphController(SecurityGraphService service){
        this.service=service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','AGENT_MANAGER')")
    public Map<String,Object> snapshot(@RequestHeader("X-Tenant-Id") UUID tenant,
                                        @RequestParam(defaultValue="60") int windowMinutes){
        return service.snapshot(tenant, windowMinutes);
    }

    @GetMapping(value="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','AGENT_MANAGER')")
    public SseEmitter stream(@RequestHeader("X-Tenant-Id") UUID tenant,
                             @RequestHeader("X-Workspace-Id") UUID workspace,
                             @RequestParam(defaultValue="60") int windowMinutes){
        var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        SseEmitter emitter = new SseEmitter(65000L);
        final int[] count={
            0}
        ;
        ScheduledFuture<?> future=scheduler.scheduleAtFixedRate(()->{
            try {
                if(authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt&&jwt.getToken().getExpiresAt()!=null&&jwt.getToken().getExpiresAt().isBefore(java.time.Instant.now())){emitter.complete();return;}
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
                emitter.send(SseEmitter.event().name("security-graph").data(service.snapshot(tenant,workspace,windowMinutes)));
                if(++count[0]>=12) emitter.complete();
            }
            catch (Exception ex) {
                emitter.completeWithError(ex);
            }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
        }
        ,0,5,TimeUnit.SECONDS);
        emitter.onCompletion(()->future.cancel(false));
        emitter.onTimeout(()->future.cancel(false));
        emitter.onError(x->future.cancel(false));
        return emitter;
    }
}
