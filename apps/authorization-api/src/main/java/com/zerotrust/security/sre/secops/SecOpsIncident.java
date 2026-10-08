package com.zerotrust.security.sre.secops;
import java.time.OffsetDateTime;
import java.util.UUID;
public record SecOpsIncident(UUID id, UUID tenantId, String incidentKey, String title,
 String severity, String status, String source, OffsetDateTime openedAt, String owner, String summary) {
 }
