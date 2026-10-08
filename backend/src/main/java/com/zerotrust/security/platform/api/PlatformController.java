package com.zerotrust.security.platform.api;

import com.zerotrust.security.platform.observability.ObservabilityService;
import com.zerotrust.security.platform.store.PlatformStore;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class PlatformController {
    private final PlatformStore store;
    private final ObservabilityService observability;

    public PlatformController(PlatformStore store, ObservabilityService observability) {
        this.store = store;
        this.observability = observability;
    }

    @PostMapping("/federation/organizations")
    public Map<String, Object> organization(@RequestBody Map<String, Object> body) {
        return register("organization", body);
    }

    @PostMapping("/federation/trust")
    public Map<String, Object> trust(@RequestBody Map<String, Object> body) {
        return register("trust", body);
    }

    @PostMapping("/agents")
    public Map<String, Object> agent(@RequestBody Map<String, Object> body) {
        return register("agent", body);
    }

    @PostMapping("/agents/{id}/missions")
    public Map<String, Object> mission(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>(register("mission", body));
        result.put("agentId", id);
        return result;
    }

    @PostMapping("/simulations")
    public Map<String, Object> simulation(@RequestBody Map<String, Object> body) {
        return register("simulation", body);
    }

    @PostMapping("/simulations/{id}/run")
    public Map<String, Object> runSimulation(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return Map.of(
                "id", UUID.randomUUID().toString(),
                "simulationId", id,
                "status", "COMPLETED",
                "riskDelta", 0.0,
                "startedAt", Instant.now().toString(),
                "input", body == null ? Map.of() : body);
    }

    @PostMapping("/kubernetes/policies")
    public Map<String, Object> kubernetesPolicy(@RequestBody Map<String, Object> body) {
        return register("kubernetes-policy", body);
    }

    @PostMapping("/runtime/sessions")
    public Map<String, Object> runtimeSession(@RequestBody Map<String, Object> body) {
        return register("runtime-session", body);
    }

    @GetMapping("/runtime/sessions/{id}")
    public Map<String, Object> runtimeSession(@PathVariable String id) {
        return Map.of("id", id, "status", "ACTIVE");
    }

    @PostMapping("/runtime/sessions/{id}/check")
    public Map<String, Object> runtimeCheck(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return Map.of("sessionId", id, "allowed", true, "request", body);
    }

    @PostMapping("/runtime/sessions/{id}/end")
    public Map<String, Object> endRuntimeSession(@PathVariable String id) {
        return Map.of("id", id, "status", "ENDED");
    }

    private Map<String, Object> register(String type, Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        store.resources.put(id, body);
        String traceId = UUID.randomUUID().toString();
        observability.publish(
                type.toUpperCase().replace('-', '_') + "_REGISTERED",
                String.valueOf(body.getOrDefault("tenantId", "unknown")),
                String.valueOf(body.getOrDefault("subject", type)),
                traceId,
                null,
                traceId,
                Map.of("id", id, "type", type),
                null);
        return Map.of("id", id, "type", type, "status", "REGISTERED", "traceId", traceId);
    }
}
