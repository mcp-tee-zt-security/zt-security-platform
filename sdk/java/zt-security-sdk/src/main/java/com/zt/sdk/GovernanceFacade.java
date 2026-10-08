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
        return execute(subject, action, resource, tenantId, Map.of(), evidence, approval);
    }

    public JsonNode execute(String subject, String action, String resource, String tenantId,
            Map<String, Object> attributes, Map<String, Object> evidence, Map<String, Object> approval) {
        JsonNode decision = evaluate(subject, action, resource, tenantId, attributes);
        if ("DENY".equals(decision.path("decision").asText())) {
            return decision;
        }

        JsonNode evidenceResult = evidence == null ? null : client.createEvidence(evidence);
        boolean requiresApproval = "STEP_UP".equals(decision.path("decision").asText())
                || "REQUIRE_APPROVAL".equals(decision.path("decision").asText());
        JsonNode approvalResult = approval == null ? null : client.toJson(approval);
        if (requiresApproval && approvalResult == null) {
            approvalResult = client.requestApproval(Map.of("decision", decision, "action", action, "resource", resource));
        }
        Map<String, Object> contract = new HashMap<>();
        contract.put("action", action);
        contract.put("resource", resource);
        contract.put("policyDecision", decision);
        if (evidenceResult != null && evidenceResult.has("id")) {
            contract.put("evidenceIds", List.of(evidenceResult.get("id").asText()));
        }
        if (approvalResult != null) {
            contract.put("approval", approvalResult);
        }

        JsonNode contractResult = client.createExecutionContract(contract);
        if (requiresApproval && !"APPROVED".equals(approvalResult.path("status").asText())) {
            return client.toJson(Map.of("decision", decision, "approval", approvalResult, "contract", contractResult));
        }
        JsonNode execution = client.executeContract(
                contractResult.path("id").asText(),
                Map.of());
        JsonNode verification = client.verifyExecution(execution.path("id").asText(), Map.of());
        Map<String, Object> result = new HashMap<>();
        result.put("decision", decision);result.put("evidence", evidenceResult);result.put("approval", approvalResult);
        result.put("contract", contractResult);result.put("execution", execution);result.put("verification", verification);
        return client.toJson(result);
    }
}
