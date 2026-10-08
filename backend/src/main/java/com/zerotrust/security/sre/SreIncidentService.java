package com.zerotrust.security.sre;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SreIncidentService {
    private final List<SreIncidentEvent> events = new CopyOnWriteArrayList<>();

    public SreIncidentEvent record(
            UUID tenantId,
            String incidentId,
            String eventType,
            String severity,
            String source,
            String correlationKey,
            String details) {
        SreIncidentEvent event = new SreIncidentEvent(
                UUID.randomUUID(),
                tenantId,
                incidentId,
                eventType,
                severity,
                source,
                OffsetDateTime.now(),
                correlationKey,
                details == null ? "{}" : details);
        events.add(event);
        return event;
    }

    public List<SreIncidentEvent> list(String incidentId) {
        return events.stream()
                .filter(event -> event.incidentId().equals(incidentId))
                .toList();
    }
}
