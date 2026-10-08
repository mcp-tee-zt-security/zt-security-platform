package com.zt.sdk;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Canonical Identity -> Policy -> Evidence -> Approval -> Execution -> Verification workflow. */
public final class GovernanceFacade {
    private final ZtSecurityClient client;

    public GovernanceFacade(ZtSecurityClient client) {
        this.client = client;
    }

    public JsonNode evaluate(
            String subject,
            String action,
            String resource,
            String tenantId,
            Map<String, Object> attributes) {
        Map<String, Object> request = new HashMap<>();
        request.put("subject", subject);
        request.put("action", action);
        request.put("resource", resource);
        request.put("tenantId", tenantId);
        request.put("attributes", attributes == null ? Map.of() : attributes);
        return client.evaluate(request, null);
    }

    public JsonNode execute(
            String subject,
            String action,
            String resource,
            String tenantId,
            Map<String, Object> evidence,
            Map<String, Object> approval) {
        JsonNode decision = evaluate(subject, action, resource, tenantId, Map.of());
        if ("DENY".equals(decision.path("decision").asText())) {
            return decision;
        }

        JsonNode evidenceResult = evidence == null ? null : client.createEvidence(evidence);
        Map<String, Object> contract = new HashMap<>();
        contract.put("action", action);
        contract.put("resource", resource);
        contract.put("policyDecision", decision);
        if (evidenceResult != null && evidenceResult.has("id")) {
            contract.put("evidenceIds", List.of(evidenceResult.get("id").asText()));
        }
        if (approval != null) {
            contract.put("approval", approval);
        }

        JsonNode contractResult = client.createExecutionContract(contract);
        JsonNode execution = client.executeContract(
                contractResult.path("id").asText(),
                Map.of());
        return client.verifyExecution(execution.path("id").asText(), Map.of());
    }
}
