package com.zerotrust.security.platform.api;

import com.zerotrust.security.platform.domain.GovernanceModels.EventEnvelope;
import com.zerotrust.security.platform.observability.ObservabilityService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/observability")
public class ObservabilityController {
    private final ObservabilityService observability;

    public ObservabilityController(ObservabilityService observability) {
        this.observability = observability;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return observability.metrics();
    }

    @GetMapping("/events")
    public List<EventEnvelope> events(
            @RequestParam(required = false) String traceId,
            @RequestParam(defaultValue = "100") int limit) {
        return observability.replay(traceId, Math.max(1, Math.min(limit, 500)));
    }
}
