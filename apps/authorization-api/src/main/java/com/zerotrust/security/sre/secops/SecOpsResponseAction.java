package com.zerotrust.security.sre.secops;
import java.time.OffsetDateTime;
import java.util.UUID;
public record SecOpsResponseAction(UUID id, UUID tenantId, UUID incidentId, String actionType,
 String requestedBy, String approvalStatus, String executionStatus, OffsetDateTime requestedAt, String rationale) {
 }
