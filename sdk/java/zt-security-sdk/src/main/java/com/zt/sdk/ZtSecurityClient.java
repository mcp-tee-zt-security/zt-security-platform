package com.zt.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public class ZtSecurityClient {
    private final String baseUrl;
    private final String apiKey;
    private final String tenantId;
    private final String workspaceId;
    private final HttpClient http;
    private final ObjectMapper json;

    public ZtSecurityClient(
            String baseUrl,
            String apiKey,
            String tenantId,
            String workspaceId) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.json = new ObjectMapper();
    }

    public ZtSecurityClient(String baseUrl, String apiKey, String tenantId) {
        this(baseUrl, apiKey, tenantId, null);
    }

    public JsonNode post(String path, Object body) {
        return request("POST", path, body, null);
    }

    public JsonNode post(String path, Object body, String idempotencyKey) {
        return request("POST", path, body, idempotencyKey);
    }

    public JsonNode get(String path) {
        return request("GET", path, null, null);
    }

    private JsonNode request(
            String method,
            String path,
            Object body,
            String idempotencyKey) {
        try {
            String requestBody = body == null ? null : json.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("X-API-Key", apiKey)
                    .header("X-Tenant-Id", tenantId)
                    .header("Content-Type", "application/json");

            if (workspaceId != null) {
                builder.header("X-Workspace-Id", workspaceId);
            }
            if (idempotencyKey != null) {
                builder.header("Idempotency-Key", idempotencyKey);
            }

            if ("POST".equals(method)) {
                builder.POST(HttpRequest.BodyPublishers.ofString(
                        requestBody == null ? "{}" : requestBody));
            } else {
                builder.GET();
            }

            HttpResponse<String> response = http.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ZtSecurityHttpException(
                        response.statusCode(),
                        response.body(),
                        response.headers().firstValue("X-Request-Id").orElse(null));
            }
            return response.body().isBlank()
                    ? json.createObjectNode()
                    : json.readTree(response.body());
        } catch (ZtSecurityHttpException error) {
            throw error;
        } catch (Exception error) {
            throw new ZtSecurityException(error.getMessage(), error);
        }
    }

    public JsonNode evaluate(Map<String, Object> request, String key) {
        String idempotencyKey = key == null ? UUID.randomUUID().toString() : key;
        return post("/v1/actions/evaluate", request, idempotencyKey);
    }

    public JsonNode runtimeCheck(String id, Map<String, Object> request) {
        return post("/v1/runtime/sessions/" + id + "/check", request);
    }

    public JsonNode policyLint(String text) {
        return post("/v1/governance/policies/lint", Map.of("policyText", text));
    }

    public JsonNode resolveIdentity(Map<String, Object> body) {
        return post("/v1/identity/resolve", body);
    }

    public JsonNode verifyAttestation(Map<String, Object> body) {
        return post("/v1/attestation/verify", body);
    }

    public JsonNode checkCapability(Map<String, Object> body) {
        return post("/v1/capabilities/check", body);
    }

    public JsonNode createEvidence(Map<String, Object> body) {
        return post("/v1/evidence", body);
    }

    public JsonNode requestApproval(Map<String, Object> body) {
        return post("/v1/approvals", body);
    }

    public JsonNode createExecutionContract(Map<String, Object> body) {
        return post("/v1/execution/contracts", body);
    }

    public JsonNode executeContract(String id, Map<String, Object> body) {
        return post("/v1/execution/contracts/" + id + "/execute", body);
    }

    public JsonNode verifyExecution(String id, Map<String, Object> body) {
        return post("/v1/execution/" + id + "/verify", body);
    }

    public JsonNode registerOrganization(Map<String, Object> body) {
        return post("/v1/federation/organizations", body);
    }

    public JsonNode establishTrust(Map<String, Object> body) {
        return post("/v1/federation/trust", body);
    }

    public JsonNode registerAgent(Map<String, Object> body) {
        return post("/v1/agents", body);
    }

    public JsonNode createMission(String id, Map<String, Object> body) {
        return post("/v1/agents/" + id + "/missions", body);
    }

    public JsonNode createSimulation(Map<String, Object> body) {
        return post("/v1/simulations", body);
    }

    public JsonNode runSimulation(String id, Map<String, Object> body) {
        return post("/v1/simulations/" + id + "/run", body);
    }

    public JsonNode applyKubernetesPolicy(Map<String, Object> body) {
        return post("/v1/kubernetes/policies", body);
    }

    public JsonNode observabilityMetrics() {
        return get("/v1/observability/metrics");
    }

    public JsonNode replayEvents(String traceId, int limit) {
        String path = "/v1/observability/events?limit=" + limit;
        if (traceId != null && !traceId.isBlank()) {
            path += "&traceId=" + traceId;
        }
        return get(path);
    }
}
