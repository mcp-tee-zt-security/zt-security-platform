package com.zerotrust.security.sre;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/operations/sre")
public class SreOperationsController {
    private final SreIncidentService service;

    public SreOperationsController(SreIncidentService service) {
        this.service = service;
    }

    @PostMapping("/incidents/{incidentId}/events")
    public SreIncidentEvent record(
            @PathVariable String incidentId,
            @RequestParam UUID tenantId,
            @RequestParam String eventType,
            @RequestParam String severity,
            @RequestParam String source,
            @RequestParam(required = false) String correlationKey,
            @RequestBody(required = false) String details) {
        return service.record(
                tenantId,
                incidentId,
                eventType,
                severity,
                source,
                correlationKey,
                details);
    }

    @GetMapping("/incidents/{incidentId}/events")
    public List<SreIncidentEvent> events(@PathVariable String incidentId) {
        return service.list(incidentId);
    }
}
