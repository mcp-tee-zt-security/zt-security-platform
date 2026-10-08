package com.zt.security.runtime;

import com.zt.security.action.EvaluateModels;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;

/** Runtime-only threat heuristics. These are signals, not a replacement for policy. */
@Service
public class RuntimeThreatDetectionService {
    @Value("${zt.security.risk-scoring.runtime-threat-score-threshold:0.80}")
    private double threatScoreThreshold;
    public record Signal(String type, String severity, double score, String reason) {
    }
    public record Result(List<Signal> signals, double score, String decision) {
        public boolean blocked(){
            return "DENY".equals(decision);
        }
    }

    public Result detect(EvaluateModels.EvaluateRequest r) {
        if (!"AI_AGENT".equalsIgnoreCase(r.principal().type())) return new Result(List.of(),0,"NONE");
        Map<String,Object> c = r.context()==null ? Map.of() : r.context();
        List<Signal> s = new ArrayList<>();
        if (truthy(c.get("prompt_injection_detected")) || number(c.get("prompt_injection_score")) >= threatScoreThreshold) {
s.add(new Signal("PROMPT_INJECTION", "HIGH", 40, "Prompt-injection signal reported by the runtime context"));
        }
        if (truthy(c.get("tool_poisoning_detected")) || number(c.get("tool_poisoning_score")) >= threatScoreThreshold) {
            s.add(new Signal("TOOL_POISONING", "HIGH", 35, "Tool-poisoning or untrusted tool metadata signal" +
" reported"));
        }
        String destination = String.valueOf(c.getOrDefault("destination.type", c.getOrDefault("destination_type", "")));
        String classification = String.valueOf(c.getOrDefault("resource.classification",
        c.getOrDefault("classification", "")));
        if (("data.export".equals(r.action().name()) || "data.read.external".equals(r.action().name()))
                && "external".equalsIgnoreCase(destination)
                && ("CONFIDENTIAL".equalsIgnoreCase(classification) || "RESTRICTED".equalsIgnoreCase(classification))) {
            s.add(new Signal("DATA_EXFILTRATION", "CRITICAL", 50, "Restricted data is being sent to an external" +
" destination"));
        }
        if (truthy(c.get("credential_access_detected"))) {
            s.add(new Signal("CREDENTIAL_ACCESS", "CRITICAL", 50, "Runtime reported access to credential material"));
        }
        double score = Math.min(100, s.stream().mapToDouble(Signal::score).sum());
        String decision = score >= 50 ? "DENY" : score >= 30 ? "STEP_UP" : "NONE";
        return new Result(List.copyOf(s), score, decision);
    }
    private boolean truthy(Object o){
        return o instanceof Boolean b ? b : o!=
        null && Boolean.parseBoolean(String.valueOf(o));
    }
    private double number(Object o){
        try{
            return o instanceof Number n?n.doubleValue():
            Double.parseDouble(String.valueOf(o));
        }
        catch(Exception e){
            return 0;
        }
        }
}
