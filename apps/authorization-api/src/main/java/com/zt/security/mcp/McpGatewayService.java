package com.zt.security.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.action.ActionEvaluationService;
import com.zerotrust.security.config.RiskScoringProperties;
import com.zt.security.action.EvaluateModels;
import com.zt.security.agent.AgentTool;
import com.zt.security.agent.AgentToolRepository;
import com.zt.security.event.SecurityEventService;
import com.zt.security.runtime.RuntimeThreatDetectionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class McpGatewayService {
    private final ActionEvaluationService actions;
    private final AgentToolRepository tools;
    private final RuntimeThreatDetectionService threats;
    private final SecurityEventService events;
    private final ObjectMapper mapper;
    private final RiskScoringProperties riskConfig;

    McpGatewayService(ActionEvaluationService actions, AgentToolRepository tools,
                      RuntimeThreatDetectionService threats, SecurityEventService events,
                      ObjectMapper mapper, RiskScoringProperties riskConfig) {
        this.actions = actions;
        this.tools = tools;
        this.threats = threats;
        this.events = events;
        this.mapper = mapper;
        this.riskConfig = riskConfig;
    }

    public Map<String,Object> capabilities(UUID tenant, UUID workspace) {
        return Map.of("protocol", "MCP", "gatewayVersion", "2.8.0",
                "security", List.of("identity-bound-tools", "policy-enforcement", "threat-detection", "audit-events"),
                "tenant", tenant, "workspace", workspace == null ? "" : workspace.toString());
    }

    public List<Map<String,Object>> listTools(UUID tenant) {
        return tools.findByTenantId(tenant).stream().filter(AgentTool::isEnabled).map(t -> Map.<String,Object>of(
                "name", t.getName(), "description", Optional.ofNullable(t.getDescription()).orElse(""),
                "riskLevel", Optional.ofNullable(t.getRiskLevel()).orElse("MEDIUM"),
                "toolId", t.getId().toString())).toList();
    }

    @Transactional
    public Map<String,Object> authorize(UUID tenant, UUID workspace, McpAuthorizeRequest r) {
        AgentTool tool = tools.findById(r.toolId()).orElseThrow(() -> new SecurityException("MCP tool not found"));
        if (!tenant.equals(tool.getTenantId())) throw new SecurityException("MCP tool tenant mismatch");
        Map<String,Object> context = new LinkedHashMap<>();
        if (r.context() != null) context.putAll(r.context());
        context.put("mcp.tool", tool.getName());
        context.put("mcp.tool_risk_level", tool.getRiskLevel());
        context.put("tool_id", tool.getId().toString());
        context.put("mcp.method", "tools/call");
        context.put("tool_poisoning_score", toolPoisoningScore(tool));
        EvaluateModels.EvaluateRequest req = new EvaluateModels.EvaluateRequest(
                new EvaluateModels.Principal(r.agent(), "AI_AGENT", Map.of("source", "MCP_GATEWAY")),
                new EvaluateModels.Action("mcp.tool.call"),
                new EvaluateModels.Resource("mcp_tool", tool.getId().toString(),
                Map.of("name", tool.getName(), "risk", tool.getRiskLevel())),
                context);
        var threat = threats.detect(req);
        String decision = threat.blocked() ? "DENY" : threat.decision().equals("STEP_UP") ?
        "STEP_UP" : actions.evaluate(tenant,
        req).decision();
        UUID eventId = events.enqueue(tenant, workspace, "MCP_TOOL_DECISION",
        "MCP_TOOL", tool.getId().toString(), json(Map.of(
                "agent", r.agent(), "tool", tool.getName(), "decision", decision, "threatScore", threat.score(),
                "signals", threat.signals())));
        return Map.of("decision", decision, "tool", tool.getName(), "toolId", tool.getId(),
                "threatScore", threat.score(), "signals", threat.signals(), "auditEventId", eventId,
                "allowed", "ALLOW".equals(decision));
    }

    public Map<String,Object> jsonRpc(UUID tenant, UUID workspace, Map<String,Object> rpc) {
        String method = String.valueOf(rpc.getOrDefault("method", ""));
        Object id = rpc.get("id");
        try {
            Map<String,Object> result;
            if ("initialize".equals(method)) result = Map.of("protocolVersion",
            "2025-06-18", "capabilities", Map.of("tools", Map.of()), "serverInfo",
            Map.of("name", "zt-mcp-security-gateway", "version", "2.8.0"));
            else if ("tools/list".equals(method)) result = Map.of("tools", listTools(tenant));
            else if ("tools/call".equals(method)) {
                Map<String,Object> p = (Map<String,Object>) rpc.getOrDefault("params", Map.of());
                String name = String.valueOf(p.get("name"));
                AgentTool tool = tools.findByTenantId(tenant).stream().filter(AgentTool::isEnabled).filter(x -> name.equals(x.getName())).findFirst().orElseThrow(() ->
                new SecurityException("unknown MCP tool"));
                Map<String,Object> args = p.get("arguments") instanceof Map<?,?> m ? (Map<String,Object>) m : Map.of();
                String agent = String.valueOf(args.getOrDefault("agent", "mcp-client"));
                var auth = authorize(tenant, workspace, new McpAuthorizeRequest(agent, tool.getId(), args));
                if (!Boolean.TRUE.equals(auth.get("allowed"))) result = Map.of("isError",
                true, "content", List.of(Map.of("type", "text", "text", "MCP security decision: " +
                auth.get("decision"))),
                "security", auth);
                else result = Map.of("isError", false, "content", List.of(Map.of("type",
                "text", "text", "MCP tool authorized: " + name)), "security", auth);
            }
            else throw new IllegalArgumentException("unsupported MCP method: " + method);
            return Map.of("jsonrpc", "2.0", "id", id == null ? UUID.randomUUID() : id, "result", result);
        }
        catch (Exception e) {
            return Map.of("jsonrpc", "2.0", "id", id == null ? UUID.randomUUID() : id,
            "error", Map.of("code", -32001, "message", e.getMessage()));
        }
    }

    private double toolPoisoningScore(AgentTool t) {
        String d = Optional.ofNullable(t.getDescription()).orElse("").toLowerCase(Locale.ROOT);
        long suspicious = List.of("ignore previous", "system prompt", "send secret",
        "exfiltrate", "disable security", "bypass policy").stream().filter(d::contains).count();
        double base = "CRITICAL".equalsIgnoreCase(t.getRiskLevel()) ? riskConfig.getMcpToolPoisoningCriticalBase() :
        "HIGH".equalsIgnoreCase(t.getRiskLevel()) ? riskConfig.getMcpToolPoisoningHighBase()
                : riskConfig.getMcpToolPoisoningNormalBase();
        return Math.min(riskConfig.getMcpToolPoisoningMaxScore(),
                base + suspicious * riskConfig.getMcpToolPoisoningSuspiciousWeight());
    }
    private String json(Object x) {
        try {
            return mapper.writeValueAsString(x);
        }
    catch (Exception e) {
        return "{}";
    }
    }
    public record McpAuthorizeRequest(String agent, UUID toolId, Map<String,Object> context) {
    }
}
