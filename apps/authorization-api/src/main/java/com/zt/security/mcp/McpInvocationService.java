package com.zt.security.mcp;

import com.fasterxml.jackson.databind.*;
import com.zt.security.action.*;
import com.zt.security.approval.*;
import com.zt.security.event.SecurityEventService;
import com.zerotrust.security.config.RiskScoringProperties;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;

/** Commits the execution claim before any network call; locks approval resumes across replicas. */
@Service
@Transactional
public class McpInvocationService {
    public record Plan(UUID callId, McpToolRegistry.Binding binding, JsonNode arguments, String status,
        String decision, String reason, UUID approvalId, UUID requestId, String authenticatedSubject, String policySubject, String agentBindingRevision) {
        public Plan(UUID callId,McpToolRegistry.Binding binding,JsonNode arguments,String status,String decision,String reason,UUID approvalId,UUID requestId) {
            this(callId,binding,arguments,status,decision,reason,approvalId,requestId,null,null,null);
        }
        boolean executable() { return "EXECUTING".equals(status); }
        Map<String, Object> security() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("callId", callId); value.put("toolId", binding.toolId()); value.put("decision", decision);
            value.put("status", status); value.put("reason", reason); value.put("requestId", requestId);
            value.put("authenticatedSubject",authenticatedSubject);value.put("policySubject",policySubject);
            value.put("approvalId", approvalId); value.put("allowed", "ALLOW".equals(decision));
            return value;
        }
    }
    private final McpToolRegistry registry;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ActionEvaluationService actions;
    private final ApprovalRepository approvals;
    private final SecurityEventService events;
    private final McpGatewayProperties properties;
    private final RiskScoringProperties riskConfig;
    private final McpAgentBindingService agentBindings;
    private static final String SCOPE = "tenant_id=:tenant AND workspace_id IS NOT DISTINCT FROM CAST(:workspace AS uuid)";

    McpInvocationService(McpToolRegistry registry, NamedParameterJdbcTemplate jdbc, ObjectMapper mapper,
            ActionEvaluationService actions, ApprovalRepository approvals, SecurityEventService events,
            McpGatewayProperties properties, RiskScoringProperties riskConfig, McpAgentBindingService agentBindings) {
        this.registry = registry; this.jdbc = jdbc; this.mapper = mapper; this.actions = actions;
        this.approvals = approvals; this.events = events; this.properties = properties; this.riskConfig = riskConfig;this.agentBindings=agentBindings;
    }
    public Plan prepare(UUID tenant, UUID workspace, McpActor actor, UUID tool, JsonNode arguments, boolean execute, String requestKey) {
        registry.scope(tenant, workspace);
        var principal=agentBindings.resolve(tenant,workspace,actor);
        var binding = registry.get(tenant, workspace, tool, actor);
        McpArgumentValidator.validate(binding.config().inputSchema(), arguments);
        String argumentHash = McpJson.hash(arguments);
        if (requestKey != null) {
            // Serialize concurrent deliveries of the same caller/RPC id before evaluation and dispatch.
            String lockHash = McpJson.digest(tenant + ":" + workspace + ":" + actor.key() + ":" + requestKey);
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(:lock)",
                new MapSqlParameterSource("lock", Long.parseUnsignedLong(lockHash.substring(0, 16), 16)), Object.class);
            var existing = jdbc.queryForList("SELECT id,tool_id,arguments_hash,status,approval_id,decision_request_id,policy_subject,agent_binding_revision FROM mcp_invocations WHERE " +
                SCOPE + " AND actor_key=:actor AND request_key=:key",
                McpToolRegistry.params(tenant, workspace).addValue("actor", actor.key()).addValue("key", requestKey));
            if (!existing.isEmpty()) {
                var old = existing.get(0);
                checkPrincipal(actor,principal,old);
                if (!tool.equals(old.get("tool_id")) || !argumentHash.equals(old.get("arguments_hash"))) {
                    throw new IllegalStateException("JSON-RPC id was already used for a different MCP operation");
                }
                String recorded = (String) old.get("status");
                String status = "PENDING_APPROVAL".equals(recorded) ? recorded : "REPLAY_BLOCKED";
                String effective = "PENDING_APPROVAL".equals(recorded) ? "STEP_UP" : "DENIED".equals(recorded) ? "DENY" : "ALLOW";
                return new Plan((UUID) old.get("id"), binding, arguments.deepCopy(), status, effective,
                    "Existing MCP call is " + recorded + "; it will not be dispatched again",
                    (UUID) old.get("approval_id"), (UUID) old.get("decision_request_id"),actor.subject(),principal.subject(),principal.revision());
            }
        }
        var decision = evaluate(tenant, workspace, actor, principal, binding, arguments);
        UUID id = UUID.randomUUID();
        Instant expires = Instant.now().plusSeconds(properties.getApprovalTtlSeconds());
        boolean needsApproval = !"DENY".equals(decision.decision()) &&
            ("STEP_UP".equals(decision.decision()) || "ALLOW".equals(decision.decision()) && binding.config().requireApproval());
        boolean allow = "ALLOW".equals(decision.decision()) && !needsApproval;
        String status = needsApproval ? "PENDING_APPROVAL" : allow ? execute ? "EXECUTING" : "NOT_EXECUTED" : "DENIED";
        UUID approval = needsApproval ? approval(tenant, actor, principal, id, decision.requestId(), argumentHash,
            binding.hash(), decision.reason(), expires) : null;
        var p = McpToolRegistry.params(tenant, workspace).addValue("id", id).addValue("actor", actor.key())
            .addValue("key", requestKey)
            .addValue("subject", actor.subject()).addValue("principal",principal.subject()).addValue("agentRevision",principal.revision()).addValue("tool", tool).addValue("binding", binding.hash())
            .addValue("arguments", arguments.toString()).addValue("hash", argumentHash).addValue("request", decision.requestId())
            .addValue("policy", policyHash(decision)).addValue("approval", approval).addValue("status", status)
            .addValue("expires", Timestamp.from(expires));
        jdbc.update("""
            INSERT INTO mcp_invocations(id,tenant_id,workspace_id,actor_key,actor_subject,request_key,tool_id,binding_hash,
                arguments,arguments_hash,decision_request_id,policy_hash,approval_id,status,expires_at,policy_subject,agent_binding_revision)
            VALUES (:id,:tenant,:workspace,:actor,:subject,:key,:tool,:binding,cast(:arguments as jsonb),
                :hash,:request,:policy,:approval,:status,:expires,:principal,:agentRevision)
            """, p);
        String effective = needsApproval ? "STEP_UP" : allow ? "ALLOW" : "DENY";
        event(tenant, workspace, id, "MCP_CALL_PREPARED", Map.of(
            "actor", actor.subject(), "toolId", tool, "argumentsHash", argumentHash, "bindingHash", binding.hash(),
            "decision", effective, "requestId", decision.requestId(), "status", status,"policySubject",principal.subject()));
        return new Plan(id, binding, arguments.deepCopy(), status, effective,
            needsApproval && binding.config().requireApproval() ? "Registered tool requires human approval" : decision.reason(),
            approval, decision.requestId(),actor.subject(),principal.subject(),principal.revision());
    }
    public Plan resume(UUID tenant, UUID workspace, McpActor actor, UUID id) {
        registry.scope(tenant, workspace);
        var principal=agentBindings.resolve(tenant,workspace,actor);
        Map<String, Object> row = row(tenant, workspace, actor, id, true);
        checkPrincipal(actor,principal,row);
        if (!"PENDING_APPROVAL".equals(row.get("status"))) throw new IllegalStateException("MCP call cannot be resumed or replayed");
        Instant expires = ((Timestamp) row.get("expires_at")).toInstant();
        if (!expires.isAfter(Instant.now())) throw new IllegalStateException("MCP call expired; create a new call");
        UUID tool = (UUID) row.get("tool_id");
        var binding = registry.get(tenant, workspace, tool, actor);
        if (!binding.hash().equals(row.get("binding_hash"))) throw new IllegalStateException("Tool binding changed; create a new call");
        JsonNode arguments = parse((String) row.get("arguments_json"));
        if (!McpJson.hash(arguments).equals(row.get("arguments_hash"))) throw new AccessDeniedException("Stored MCP arguments changed");
        McpArgumentValidator.validate(binding.config().inputSchema(), arguments);
        UUID approvalId = (UUID) row.get("approval_id");
        Approval approved = approvals.findByIdAndTenantId(approvalId, tenant)
            .orElseThrow(() -> new AccessDeniedException("Bound approval unavailable"));
        UUID requestId = (UUID) row.get("decision_request_id");
        if (!requestId.equals(approved.getRequestId()) || approved.getExpiresAt() == null ||
            !approved.getExpiresAt().isAfter(Instant.now())) throw new AccessDeniedException("Bound approval expired or invalid");
        JsonNode payload = parse(approved.getPayload());
        if(!"legacy".equals(principal.revision()) && (!principal.subject().equals(payload.path("policySubject").asText()) || !principal.revision().equals(payload.path("agentBindingRevision").asText())))
            throw new AccessDeniedException("Approval Agent binding mismatch");
        if (!id.toString().equals(payload.path("mcpCallId").asText())
            || !actor.subject().equals(payload.path("requestedBy").asText())
            || !row.get("arguments_hash").equals(payload.path("argumentsHash").asText())
            || !binding.hash().equals(payload.path("bindingHash").asText())) throw new AccessDeniedException("Approval does not authorize this MCP operation");
        if (!"APPROVED".equals(approved.getStatus())) {
            String status = "REJECTED".equals(approved.getStatus()) ? "DENIED" : "PENDING_APPROVAL";
            if ("DENIED".equals(status)) {
                update(tenant, workspace, id, status, "APPROVAL_REJECTED");
                event(tenant, workspace, id, "MCP_CALL_DENIED", Map.of("reason", "APPROVAL_REJECTED"));
            }
            return new Plan(id, binding, arguments, status, "DENIED".equals(status) ? "DENY" : "STEP_UP",
                "Human approval is " + approved.getStatus(), approvalId, requestId,actor.subject(),principal.subject(),principal.revision());
        }
        if (approved.getDecidedBy() == null || actor.subject().equals(approved.getDecidedBy())) {
            throw new AccessDeniedException("An independent approver must approve this MCP call");
        }
        var current = evaluate(tenant, workspace, actor, principal, binding, arguments);
        if (!Set.of("ALLOW", "STEP_UP").contains(current.decision())) {
            update(tenant, workspace, id, "DENIED", "CURRENT_POLICY_DENY");
            event(tenant, workspace, id, "MCP_CALL_DENIED", Map.of("reason", "CURRENT_POLICY_DENY", "requestId", current.requestId()));
            return new Plan(id, binding, arguments, "DENIED", "DENY", current.reason(), approvalId, current.requestId(),actor.subject(),principal.subject(),principal.revision());
        }
        if (!policyHash(current).equals(row.get("policy_hash"))) {
            UUID replacement = approval(tenant, actor, principal, id, current.requestId(), (String) row.get("arguments_hash"),
                binding.hash(), "Policy matches changed; independent approval required again", expires);
            jdbc.update("UPDATE mcp_invocations SET approval_id=:approval,decision_request_id=:request,policy_hash=:policy,updated_at=now() WHERE " + SCOPE + " AND id=:id",
                McpToolRegistry.params(tenant, workspace).addValue("id", id).addValue("approval", replacement)
                    .addValue("request", current.requestId()).addValue("policy", policyHash(current)));
            event(tenant, workspace, id, "MCP_CALL_REAPPROVAL_REQUIRED", Map.of("requestId", current.requestId(), "approvalId", replacement));
            return new Plan(id, binding, arguments, "PENDING_APPROVAL", "STEP_UP", "Policy matches changed; approve again",
                replacement, current.requestId(),actor.subject(),principal.subject(),principal.revision());
        }
        // Row lock prevents two processes from consuming the same call approval.
        update(tenant, workspace, id, "EXECUTING", null);
        event(tenant, workspace, id, "MCP_CALL_EXECUTION_CLAIMED",
            Map.of("actor", actor.subject(), "approvalId", approvalId, "requestId", current.requestId()));
        return new Plan(id, binding, arguments, "EXECUTING", "ALLOW", "Approved operation passed current policy evaluation", approvalId, current.requestId(),actor.subject(),principal.subject(),principal.revision());
    }
    public UUID finish(UUID tenant, UUID workspace, UUID id, String status, JsonNode filteredResult, String errorCode) {
        registry.scope(tenant, workspace);
        if (!Set.of("SUCCEEDED", "TOOL_ERROR", "NOT_EXECUTED", "UNKNOWN").contains(status)) throw new IllegalArgumentException("Invalid execution outcome");
        String resultHash = filteredResult == null ? null : McpJson.hash(filteredResult);
        int count = jdbc.update("UPDATE mcp_invocations SET status=:status,result_hash=:hash,error_code=:error,updated_at=now() WHERE " + SCOPE + " AND id=:id AND status='EXECUTING'",
            McpToolRegistry.params(tenant, workspace).addValue("id", id).addValue("status", status)
                .addValue("hash", resultHash).addValue("error", errorCode));
        if (count != 1) throw new IllegalStateException("MCP execution outcome cannot be recorded");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status); payload.put("resultHash", resultHash); payload.put("errorCode", errorCode);
        return event(tenant, workspace, id, "MCP_CALL_COMPLETED", payload);
    }
    public Map<String, Object> inspect(UUID tenant, UUID workspace, McpActor actor, UUID id) {
        registry.scope(tenant, workspace);
        Map<String, Object> row = row(tenant, workspace, actor, id, false);
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : List.of("id", "tool_id", "status", "approval_id", "decision_request_id",
                "actor_subject", "policy_subject", "agent_binding_revision", "arguments_hash", "binding_hash", "result_hash", "error_code", "expires_at", "created_at", "updated_at")) {
            result.put(field, row.get(field));
        }
        return result; // Raw stored arguments and credentials never enter the inspection response.
    }
    public List<Map<String, Object>> history(UUID tenant, UUID workspace, McpActor actor, boolean manager) {
        registry.scope(tenant, workspace);
        return jdbc.queryForList("SELECT i.id AS \"callId\",i.tool_id AS \"toolId\",t.name AS \"toolName\",i.actor_subject AS \"requestedBy\",COALESCE(i.policy_subject,i.actor_subject) AS \"policySubject\"," +
            "i.status,i.approval_id AS \"approvalId\",i.error_code AS \"errorCode\",i.created_at AS \"createdAt\",i.updated_at AS \"updatedAt\" " +
            "FROM mcp_invocations i JOIN agent_tools t ON t.id=i.tool_id AND t.tenant_id=i.tenant_id " +
            "WHERE i.tenant_id=:tenant AND i.workspace_id IS NOT DISTINCT FROM CAST(:workspace AS uuid) " +
            (manager ? "" : "AND i.actor_key=:actor ") + "ORDER BY i.created_at DESC LIMIT 100",
            McpToolRegistry.params(tenant, workspace).addValue("actor", actor.key()));
    }
    public List<Map<String, Object>> approvalQueue(UUID tenant, UUID workspace) {
        registry.scope(tenant, workspace);
        return jdbc.queryForList("SELECT i.id AS \"callId\",i.approval_id AS \"approvalId\",i.actor_subject AS \"requestedBy\",COALESCE(i.policy_subject,i.actor_subject) AS \"policySubject\"," +
            "t.name AS \"toolName\",i.arguments_hash AS \"argumentsHash\",a.reason,a.expires_at AS \"expiresAt\"," +
            "a.decided_by AS \"decidedBy\",CASE WHEN a.status='PENDING' AND a.expires_at<=now() THEN 'EXPIRED' ELSE a.status END AS status " +
            "FROM mcp_invocations i JOIN approvals a ON a.id=i.approval_id AND a.tenant_id=i.tenant_id " +
            "JOIN agent_tools t ON t.id=i.tool_id AND t.tenant_id=i.tenant_id " +
            "WHERE i.tenant_id=:tenant AND i.workspace_id IS NOT DISTINCT FROM CAST(:workspace AS uuid) " +
            "AND a.approval_type='MCP_EXECUTION' ORDER BY i.updated_at DESC LIMIT 100",
            McpToolRegistry.params(tenant, workspace));
    }
    public Map<String, Object> approvalDetail(UUID tenant, UUID workspace, UUID id) {
        registry.scope(tenant, workspace);
        var rows = jdbc.queryForList("SELECT i.id AS \"callId\",i.approval_id AS \"approvalId\",i.actor_subject AS \"requestedBy\",COALESCE(i.policy_subject,i.actor_subject) AS \"policySubject\"," +
            "t.name AS \"toolName\",i.arguments::text AS arguments_json,i.arguments_hash AS \"argumentsHash\",a.reason," +
            "a.expires_at AS \"expiresAt\",CASE WHEN a.status='PENDING' AND a.expires_at<=now() THEN 'EXPIRED' ELSE a.status END AS status " +
            "FROM mcp_invocations i JOIN approvals a ON a.id=i.approval_id AND a.tenant_id=i.tenant_id " +
            "JOIN agent_tools t ON t.id=i.tool_id AND t.tenant_id=i.tenant_id " +
            "WHERE i.tenant_id=:tenant AND i.workspace_id IS NOT DISTINCT FROM CAST(:workspace AS uuid) " +
            "AND i.id=:id AND a.approval_type='MCP_EXECUTION'",
            McpToolRegistry.params(tenant, workspace).addValue("id", id));
        if (rows.isEmpty()) throw new AccessDeniedException("MCP approval unavailable in this workspace");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("arguments", parse((String) result.remove("arguments_json")));
        return result;
    }
    private Map<String, Object> row(UUID tenant, UUID workspace, McpActor actor, UUID id, boolean lock) {
        var rows = jdbc.queryForList("SELECT *, arguments::text AS arguments_json FROM mcp_invocations WHERE " + SCOPE + " AND id=:id AND actor_key=:actor" + (lock ? " FOR UPDATE" : ""),
            McpToolRegistry.params(tenant, workspace).addValue("id", id).addValue("actor", actor.key()));
        if (rows.isEmpty()) throw new AccessDeniedException("MCP call unavailable for this caller/scope");
        return rows.get(0);
    }
    private void update(UUID tenant, UUID workspace, UUID id, String status, String error) {
        jdbc.update("UPDATE mcp_invocations SET status=:status,error_code=:error,updated_at=now() WHERE " + SCOPE + " AND id=:id",
            McpToolRegistry.params(tenant, workspace).addValue("id", id).addValue("status", status).addValue("error", error));
    }
    private UUID approval(UUID tenant, McpActor actor, McpAgentBindingService.Principal principal, UUID callId, UUID request, String argumentsHash,
            String bindingHash, String reason, Instant expires) {
        Approval value = new Approval();
        value.setId(UUID.randomUUID()); value.setTenantId(tenant); value.setRequestId(request);
        value.setReason(reason); value.setApprovalType("MCP_EXECUTION"); value.setExpiresAt(expires);
        value.setPayload(mapper.valueToTree(Map.of("mcpCallId", callId, "requestedBy", actor.subject(),
            "argumentsHash", argumentsHash, "bindingHash", bindingHash,"policySubject",principal.subject(),"agentBindingRevision",principal.revision())).toString());
        // JDBC writes the referencing invocation immediately, so flush the JPA insert first.
        return approvals.saveAndFlush(value).getId();
    }
    private EvaluateModels.EvaluateResponse evaluate(UUID tenant, UUID workspace, McpActor actor, McpAgentBindingService.Principal principal, McpToolRegistry.Binding binding, JsonNode arguments) {
        Map<String, Object> args = mapper.convertValue(arguments, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        Map<String, Object> context = new LinkedHashMap<>(args);
        // Supply nested metadata as well as legacy flat aliases. Caller-supplied "mcp" cannot override it.
        context.put("mcp", Map.of("arguments", args, "method", "tools/call", "tool", binding.name(),
            "tool_risk_level", binding.riskLevel(), "actor", actor.subject(),"agent",principal.subject()));
        context.put("mcp.arguments", args); context.put("mcp.method", "tools/call");
        context.put("mcp.tool", binding.name()); context.put("mcp.tool_risk_level", binding.riskLevel());
        context.put("mcp.agent",principal.subject());
        context.put("tool_id", binding.toolId().toString()); context.put("mcp.actor", actor.subject());
        String description = binding.description().toLowerCase(Locale.ROOT);
        long suspicious = List.of("ignore previous", "system prompt", "send secret", "exfiltrate", "disable security", "bypass policy")
            .stream().filter(description::contains).count();
        double base = "CRITICAL".equalsIgnoreCase(binding.riskLevel()) ? riskConfig.getMcpToolPoisoningCriticalBase()
            : "HIGH".equalsIgnoreCase(binding.riskLevel()) ? riskConfig.getMcpToolPoisoningHighBase() : riskConfig.getMcpToolPoisoningNormalBase();
        context.put("tool_poisoning_score", Math.min(riskConfig.getMcpToolPoisoningMaxScore(),
            base + suspicious * riskConfig.getMcpToolPoisoningSuspiciousWeight()));
        var request = new EvaluateModels.EvaluateRequest(
            new EvaluateModels.Principal(principal.subject(), "AI_AGENT", Map.of("source", "MCP_GATEWAY","authenticatedSubject",actor.subject())),
            new EvaluateModels.Action("mcp.tool.call"),
            new EvaluateModels.Resource("mcp_tool", binding.toolId().toString(), Map.of("name", binding.name(), "risk", binding.riskLevel())),
            context);
        return actions.evaluate(tenant, workspace, request, false);
    }
    private void checkPrincipal(McpActor actor,McpAgentBindingService.Principal principal,Map<String,Object> row) {
        String stored=row.get("policy_subject")==null?actor.subject():row.get("policy_subject").toString();
        String revision=row.get("agent_binding_revision")==null?"legacy":row.get("agent_binding_revision").toString();
        if(!stored.equals(principal.subject())||!revision.equals(principal.revision()))
            throw new AccessDeniedException("Service Agent binding changed; create a new MCP call and approval");
    }
    public List<Map<String,Object>> agentHistory(UUID tenant,UUID workspace,String subject) {
        registry.scope(tenant,workspace);
        return jdbc.queryForList("SELECT i.id AS \"callId\",i.actor_subject AS \"requestedBy\",i.policy_subject AS \"policySubject\",t.name AS \"toolName\",i.status,i.created_at AS \"createdAt\",i.approval_id AS \"approvalId\",a.status AS \"approvalStatus\" "
            +"FROM mcp_invocations i JOIN agent_tools t ON t.id=i.tool_id AND t.tenant_id=i.tenant_id LEFT JOIN approvals a ON a.id=i.approval_id AND a.tenant_id=i.tenant_id "
            +"WHERE i.tenant_id=:tenant AND i.workspace_id IS NOT DISTINCT FROM CAST(:workspace AS uuid) AND i.policy_subject=:subject ORDER BY i.created_at DESC LIMIT 100",
            McpToolRegistry.params(tenant,workspace).addValue("subject",subject));
    }
    private String policyHash(EvaluateModels.EvaluateResponse response) {
        var matched = response.matchedPolicies().stream().sorted(Comparator.comparing(m -> m.id().toString())).toList();
        return McpJson.hash(mapper.valueToTree(matched));
    }
    private JsonNode parse(String json) {
        try { return mapper.readTree(json); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Invalid stored MCP JSON"); }
    }
    private UUID event(UUID tenant, UUID workspace, UUID id, String type, Map<String, Object> payload) {
        var scoped=jdbc.queryForList("SELECT actor_subject,policy_subject FROM mcp_invocations WHERE "+SCOPE+" AND id=:id",McpToolRegistry.params(tenant,workspace).addValue("id",id));
        Map<String,Object> value=new LinkedHashMap<>(payload);
        if(!scoped.isEmpty()){value.put("actor",scoped.get(0).get("actor_subject"));value.put("policySubject",scoped.get(0).get("policy_subject"));}
        return events.enqueue(tenant, workspace, type, "MCP_CALL", id.toString(), mapper.valueToTree(value).toString());
    }
}
