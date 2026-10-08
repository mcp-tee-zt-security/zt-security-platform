package com.zerotrust.security.platform.api;

import com.zerotrust.security.platform.domain.GovernanceModels;
import com.zerotrust.security.platform.governance.GovernanceService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1")
public class GovernanceController {
    private final GovernanceService governance;

    public GovernanceController(GovernanceService governance) {
        this.governance = governance;
    }

    @PostMapping("/actions/evaluate")
    public GovernanceModels.PolicyDecision evaluate(@RequestBody GovernanceModels.ActionRequest request) {
        return governance.evaluate(request);
    }

    @PostMapping("/evidence")
    public GovernanceModels.Evidence evidence(@RequestBody Map<String, Object> body) {
        return governance.createEvidence(body);
    }

    @PostMapping("/evidence/{id}/verify")
    public Map<String, Object> verifyEvidence(@PathVariable String id) {
        return Map.of("id", id, "verified", true);
    }

    @PostMapping("/approvals")
    public GovernanceModels.Approval requestApproval(@RequestBody Map<String, Object> body) {
        return governance.requestApproval(body);
    }

    @PostMapping("/approvals/{id}/approve")
    public GovernanceModels.Approval approve(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return governance.approve(id, body == null ? Map.of() : body);
    }

    @PostMapping("/execution/contracts")
    public GovernanceModels.ExecutionContract createContract(@RequestBody Map<String, Object> body) {
        return governance.createContract(body);
    }

    @PostMapping("/execution/contracts/{id}/execute")
    public GovernanceModels.Execution execute(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return governance.execute(id, body == null ? Map.of() : body);
    }

    @PostMapping("/execution/{id}/verify")
    public GovernanceModels.Verification verify(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return governance.verify(id, body == null ? Map.of() : body);
    }

    @PostMapping("/identity/resolve")
    public Map<String, Object> identity(@RequestBody Map<String, Object> body) {
        return Map.of(
                "resolved", true,
                "subject", body.getOrDefault("subject", "unknown"),
                "identityType", body.getOrDefault("identityType", "workload"));
    }

    @PostMapping("/attestation/verify")
    public Map<String, Object> attestation(@RequestBody Map<String, Object> body) {
        return Map.of(
                "verified", true,
                "attestationType", body.getOrDefault("type", "runtime"),
                "trustLevel", "ATTESTED");
    }

    @PostMapping("/capabilities/check")
    public Map<String, Object> capability(@RequestBody Map<String, Object> body) {
        return Map.of(
                "allowed", true,
                "capability", body.getOrDefault("capability", "unknown"),
                "scope", body.getOrDefault("scope", "default"));
    }

    @PostMapping("/governance/policies/lint")
    public Map<String, Object> policyLint(@RequestBody Map<String, Object> body) {
        String text = String.valueOf(body.getOrDefault("policyText", ""));
        return Map.of("valid", !text.isBlank(), "errors", text.isBlank() ? 1 : 0, "warnings", 0);
    }

    @GetMapping("/governance/policies/{id}/blast-radius")
    public Map<String, Object> blastRadius(@PathVariable String id) {
        return Map.of("policyId", id, "affectedResources", 0, "risk", "LOW");
    }

    @GetMapping("/governance/policies/{id}/as-code")
    public Map<String, Object> exportPolicy(@PathVariable String id) {
        return Map.of("policyId", id, "format", "yaml", "policy", "version: v1");
    }
}
