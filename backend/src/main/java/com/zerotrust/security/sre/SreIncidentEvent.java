package com.zerotrust.security.sre;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SreIncidentEvent(
        UUID id,
        UUID tenantId,
        String incidentId,
        String eventType,
        String severity,
        String source,
        OffsetDateTime eventTime,
        String correlationKey,
        String details) {
}
