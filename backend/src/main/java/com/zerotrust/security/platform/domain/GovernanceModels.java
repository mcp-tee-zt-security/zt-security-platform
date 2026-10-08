package com.zerotrust.security.platform.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GovernanceModels {
    private GovernanceModels() {
    }

    public record ActionRequest(
            String subject,
            String action,
            String resource,
            String tenantId,
            Map<String, Object> attributes) {
    }

    public record PolicyDecision(
            String id,
            String decision,
            String reason,
            String policyId,
            String traceId,
            Instant decidedAt) {
    }

    public record Evidence(
            String id,
            String type,
            String subject,
            Map<String, Object> payload,
            String traceId,
            Instant createdAt,
            String digest) {
    }

    public record Approval(
            String id,
            String status,
            String action,
            String resource,
            String traceId,
            Instant createdAt,
            Instant approvedAt) {
    }

    public record ExecutionContract(
            String id,
            String action,
            String resource,
            String decisionId,
            List<String> evidenceIds,
            String approvalId,
            String status,
            String traceId,
            Instant createdAt) {
    }

    public record Execution(
            String id,
            String contractId,
            String status,
            String traceId,
            Instant startedAt,
            Instant completedAt,
            Map<String, Object> result) {
    }

    public record Verification(
            String id,
            String executionId,
            boolean verified,
            String traceId,
            Instant verifiedAt,
            String reason) {
    }

    public record EventEnvelope(
            UUID eventId,
            String eventType,
            Instant occurredAt,
            String tenantId,
            String subject,
            String correlationId,
            String causationId,
            String traceId,
            Map<String, Object> payload,
            List<String> evidenceRefs) {
    }
}
