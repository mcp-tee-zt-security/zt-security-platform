package com.zerotrust.security.platform.governance;

import com.zerotrust.security.platform.domain.GovernanceModels;
import com.zerotrust.security.platform.observability.ObservabilityService;
import com.zerotrust.security.platform.store.PlatformStore;
import com.zerotrust.security.platform.observability.TraceContext;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GovernanceService {
    private final PlatformStore store;
    private final ObservabilityService observability;

    public GovernanceService(PlatformStore store, ObservabilityService observability) {
        this.store = store;
        this.observability = observability;
    }

    public GovernanceModels.PolicyDecision evaluate(GovernanceModels.ActionRequest request) {
        String traceId = TraceContext.getOrCreate();
        String decision = isHighRisk(request) ? "REQUIRE_APPROVAL" : "ALLOW";
        String reason = isHighRisk(request)
                ? "High-risk action requires explicit approval"
                : "Action satisfies baseline governance policy";
        GovernanceModels.PolicyDecision result = new GovernanceModels.PolicyDecision(
                UUID.randomUUID().toString(),
                decision,
                reason,
                "baseline-governance-v1",
                traceId,
                Instant.now());
        store.decisions.put(result.id(), result);
        observability.countPolicyDecision();
        observability.publish(
                "GOVERNANCE_POLICY_DECISION",
                request.tenantId(),
                request.subject(),
                traceId,
                null,
                traceId,
                Map.of("decisionId", result.id(), "decision", decision, "action", request.action()),
                List.of());
        return result;
    }

    public GovernanceModels.Evidence createEvidence(Map<String, Object> body) {
        String traceId = stringValue(body, "traceId", UUID.randomUUID().toString());
        String id = UUID.randomUUID().toString();
        String payload = body.toString();
        GovernanceModels.Evidence evidence = new GovernanceModels.Evidence(
                id,
                stringValue(body, "type", "SECURITY_EVIDENCE"),
                stringValue(body, "subject", "sdk-client"),
                body,
                traceId,
                Instant.now(),
                sha256(payload));
        store.evidence.put(id, evidence);
        observability.publish(
                "EVIDENCE_CREATED",
                stringValue(body, "tenantId", "unknown"),
                evidence.subject(),
                traceId,
                null,
                traceId,
                Map.of("evidenceId", id, "digest", evidence.digest()),
                List.of(id));
        return evidence;
    }

    public GovernanceModels.Approval requestApproval(Map<String, Object> body) {
        String traceId = stringValue(body, "traceId", UUID.randomUUID().toString());
        GovernanceModels.Approval approval = new GovernanceModels.Approval(
                UUID.randomUUID().toString(),
                "PENDING",
                stringValue(body, "action", "unknown"),
                stringValue(body, "resource", "unknown"),
                traceId,
                Instant.now(),
                null);
        store.approvals.put(approval.id(), approval);
        observability.publish(
                "APPROVAL_REQUESTED",
                stringValue(body, "tenantId", "unknown"),
                approval.action(),
                traceId,
                null,
                traceId,
                Map.of("approvalId", approval.id(), "status", approval.status()),
                List.of());
        return approval;
    }

    public GovernanceModels.Approval approve(String id, Map<String, Object> body) {
        GovernanceModels.Approval current = store.approvals.get(id);
        if (current == null) {
            throw new IllegalArgumentException("Approval not found: " + id);
        }
        GovernanceModels.Approval updated = new GovernanceModels.Approval(
                current.id(),
                "APPROVED",
                current.action(),
                current.resource(),
                current.traceId(),
                current.createdAt(),
                Instant.now());
        store.approvals.put(id, updated);
        observability.publish(
                "APPROVAL_GRANTED",
                stringValue(body, "tenantId", "unknown"),
                current.action(),
                current.traceId(),
                id,
                current.traceId(),
                Map.of("approvalId", id, "status", "APPROVED"),
                List.of());
        return updated;
    }

    public GovernanceModels.ExecutionContract createContract(Map<String, Object> body) {
        Map<String, Object> decision = mapValue(body, "policyDecision");
        String decisionId = stringValue(decision, "id", "unknown");
        String traceId = stringValue(decision, "traceId", UUID.randomUUID().toString());
        List<String> evidenceIds = listValue(body, "evidenceIds");
        String approvalId = approvalId(body.get("approval"));
        GovernanceModels.ExecutionContract contract = new GovernanceModels.ExecutionContract(
                UUID.randomUUID().toString(),
                stringValue(body, "action", "unknown"),
                stringValue(body, "resource", "unknown"),
                decisionId,
                evidenceIds,
                approvalId,
                "READY",
                traceId,
                Instant.now());
        store.contracts.put(contract.id(), contract);
        observability.publish(
                "EXECUTION_CONTRACT_CREATED",
                stringValue(body, "tenantId", "unknown"),
                contract.action(),
                traceId,
                decisionId,
                traceId,
                Map.of("contractId", contract.id(), "status", contract.status()),
                evidenceIds);
        return contract;
    }

    public GovernanceModels.Execution execute(String id, Map<String, Object> body) {
        GovernanceModels.ExecutionContract contract = store.contracts.get(id);
        if (contract == null) {
            throw new IllegalArgumentException("Execution contract not found: " + id);
        }
        boolean approved = contract.approvalId() == null
                || "APPROVED".equals(store.approvals.getOrDefault(
                contract.approvalId(),
                new GovernanceModels.Approval("", "", "", "", "", Instant.EPOCH, null)).status());
        boolean success = approved;
        GovernanceModels.Execution execution = new GovernanceModels.Execution(
                UUID.randomUUID().toString(),
                contract.id(),
                success ? "SUCCEEDED" : "BLOCKED",
                contract.traceId(),
                Instant.now(),
                Instant.now(),
                Map.of("governed", true, "action", contract.action(), "approved", approved));
        store.executions.put(execution.id(), execution);
        observability.countExecution(success);
        observability.publish(
                success ? "EXECUTION_COMPLETED" : "EXECUTION_BLOCKED",
                stringValue(body, "tenantId", "unknown"),
                contract.action(),
                contract.traceId(),
                contract.id(),
                contract.traceId(),
                Map.of("executionId", execution.id(), "status", execution.status()),
                contract.evidenceIds());
        return execution;
    }

    public GovernanceModels.Verification verify(String id, Map<String, Object> body) {
        GovernanceModels.Execution execution = store.executions.get(id);
        if (execution == null) {
            throw new IllegalArgumentException("Execution not found: " + id);
        }
        boolean verified = "SUCCEEDED".equals(execution.status());
        GovernanceModels.Verification verification = new GovernanceModels.Verification(
                UUID.randomUUID().toString(),
                execution.id(),
                verified,
                execution.traceId(),
                Instant.now(),
                verified ? "Execution completed under governed contract" : "Execution did not complete successfully");
        store.verifications.put(verification.id(), verification);
        observability.countVerification();
        observability.publish(
                "EXECUTION_VERIFIED",
                stringValue(body, "tenantId", "unknown"),
                execution.id(),
                execution.traceId(),
                execution.id(),
                execution.traceId(),
                Map.of("verificationId", verification.id(), "verified", verified),
                List.of());
        return verification;
    }

    private boolean isHighRisk(GovernanceModels.ActionRequest request) {
        String action = request.action() == null ? "" : request.action().toLowerCase();
        return action.contains("delete") || action.contains("isolate") || action.contains("rotate")
                || action.contains("block") || action.contains("remediate");
    }

    private static String stringValue(Map<String, Object> map, String key, String fallback) {
        Object value = map == null ? null : map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private static List<String> listValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private static String approvalId(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Object id = ((Map<String, Object>) map).get("id");
        return id == null ? null : String.valueOf(id);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to hash evidence", error);
        }
    }
}
