package com.zerotrust.security.platform.observability;

import com.zerotrust.security.platform.domain.GovernanceModels.EventEnvelope;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ObservabilityService {
    private final List<EventEnvelope> events = new CopyOnWriteArrayList<>();
    private final AtomicLong policyDecisions = new AtomicLong();
    private final AtomicLong executions = new AtomicLong();
    private final AtomicLong executionFailures = new AtomicLong();
    private final AtomicLong verifications = new AtomicLong();

    public EventEnvelope publish(
            String eventType,
            String tenantId,
            String subject,
            String correlationId,
            String causationId,
            String traceId,
            Map<String, Object> payload,
            List<String> evidenceRefs) {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                Instant.now(),
                tenantId,
                subject,
                correlationId,
                causationId,
                traceId,
                payload == null ? Map.of() : Map.copyOf(payload),
                evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs));
        events.add(event);
        return event;
    }

    public void countPolicyDecision() {
        policyDecisions.incrementAndGet();
    }

    public void countExecution(boolean success) {
        executions.incrementAndGet();
        if (!success) {
            executionFailures.incrementAndGet();
        }
    }

    public void countVerification() {
        verifications.incrementAndGet();
    }

    public List<EventEnvelope> replay(String traceId, int limit) {
        List<EventEnvelope> result = new ArrayList<>();
        for (int i = events.size() - 1; i >= 0 && result.size() < limit; i--) {
            EventEnvelope event = events.get(i);
            if (traceId == null || traceId.equals(event.traceId())) {
                result.add(event);
            }
        }
        return result;
    }

    public Map<String, Object> metrics() {
        long total = executions.get();
        long failures = executionFailures.get();
        double failureRate = total == 0 ? 0.0 : (double) failures / total;
        return Map.of(
                "policyDecisions", policyDecisions.get(),
                "executions", total,
                "executionFailures", failures,
                "executionFailureRate", failureRate,
                "verifications", verifications.get(),
                "eventCount", events.size());
    }
}
