package com.zt.security.copilot;

import com.zt.security.audit.AuditRepository;
import com.zt.security.behavior.SecurityGraphService;
import com.zt.security.common.TenantSession;
import com.zt.security.policy.Policy;
import com.zt.security.policy.PolicyRepository;
import com.zt.security.blast.BlastRadiusAssessmentRepository;
import com.zt.security.incident.SecurityCaseRepository;
import com.zt.security.response.SecurityResponseActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SecurityCopilotService {
    @Value("${zt.security.risk-scoring.copilot-confidence:0.92}")
    private double copilotConfidence;
    private final SecurityGraphService graph;
    private final PolicyRepository policies;
    private final AuditRepository audits;
    private final SecurityCaseRepository cases;
    private final SecurityResponseActionRepository responses;
    private final BlastRadiusAssessmentRepository blast;
    private final TenantSession tenant;

    SecurityCopilotService(SecurityGraphService graph, PolicyRepository policies, AuditRepository audits,
                           SecurityCaseRepository cases, SecurityResponseActionRepository responses,
                           BlastRadiusAssessmentRepository blast, TenantSession tenant) {
        this.graph = graph;
        this.policies = policies;
        this.audits = audits;
        this.cases = cases;
        this.responses = responses;
        this.blast = blast;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> analyze(UUID tenantId, int windowMinutes) {
        tenant.set(tenantId);
        Map<String,Object> g = graph.snapshot(tenantId, Math.max(5, Math.min(windowMinutes, 10080)));
        List<Policy> active = policies.findByTenantIdAndStatusOrderByPriorityAsc(tenantId, "ACTIVE");
        List<Map<String,Object>> findings = new ArrayList<>();

        long criticalNodes = ((List<?>) g.getOrDefault("nodes", List.of())).stream()
                .filter(x -> x instanceof Map && severity(((Map<?,?>)x).get("risk")) >= 85).count();
        long highNodes = ((List<?>) g.getOrDefault("nodes", List.of())).stream()
                .filter(x -> x instanceof Map && severity(((Map<?,?>)x).get("risk")) >= 65).count();
        long denyPolicies = active.stream().filter(p -> "deny".equalsIgnoreCase(p.getEffect())).count();
        long stepUpPolicies = active.stream().filter(p -> "step_up".equalsIgnoreCase(p.getEffect())).count();
        long weakAgentPolicies = active.stream().filter(p -> contains(p.getPolicyText(),
        "AI_AGENT") && !contains(p.getPolicyText(), "risk.")).count();

        if (criticalNodes > 0) finding(findings, "CRITICAL", "CRITICAL_GRAPH_RISK",
        "Critical-risk nodes exist in the security graph.",
                "Review affected agents, tools and resources; calculate blast radius before allowing new delegation.",
                "Security Graph", criticalNodes);
        if (highNodes > 0) finding(findings, "HIGH", "HIGH_GRAPH_RISK",
        "High-risk identities, agents or resources are present.",
                "Add risk-aware enforcement or STEP_UP around high-impact actions.", "Security Graph", highNodes);
        if (active.isEmpty()) finding(findings, "CRITICAL", "NO_ACTIVE_POLICY",
        "No ACTIVE policies were found for this tenant.",
                "Create and approve a deny-by-default baseline before production enforcement.",
                "Policy Control Plane", 0);
        if (denyPolicies == 0) finding(findings, "HIGH", "NO_DENY_GUARD", "No active DENY policy is present.",
                "Add explicit deny rules for data exfiltration, credential access and high-value financial actions.",
                "Policy Control Plane", 0);
        if (stepUpPolicies == 0) finding(findings, "MEDIUM", "NO_STEP_UP", "No active STEP_UP policy is present.",
                "Introduce step-up approval for high-value or anomalous AI-agent actions.", "Policy Control Plane", 0);
        if (weakAgentPolicies > 0) finding(findings, "MEDIUM", "STATIC_AGENT_POLICY",
        "Some AI-agent policies do not reference risk context.",
                "Bind high-impact AI-agent actions to risk.score, behavior or threat signals.",
                "Policy Control Plane", weakAgentPolicies);

        long recentCases = cases.findTop100ByTenantIdOrderByUpdatedAtDesc(tenantId).stream().filter(c ->
        c.getUpdatedAt()!=null && c.getUpdatedAt().isAfter(Instant.now().minusSeconds(windowMinutes*60L))).count();
        long recentResponses = responses.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId).stream().
        filter(r -> r.getCreatedAt()!=null && r.getCreatedAt().isAfter(Instant.now().minusSeconds(windowMinutes*60L))).
        count();
        if (recentCases > 0 && recentResponses == 0) finding(findings,
        "HIGH", "INCIDENT_RESPONSE_GAP", "Security cases exist without corresponding response" +
" actions in the analysis window.",
                "Create a human-approved containment plan for open high/critical cases.",
                "Incident Response", recentCases);

        findings.sort(Comparator.comparingInt(x -> severityRank(String.valueOf(x.get("severity")))));
        double risk = Math.min(100, criticalNodes*18 + highNodes*7 + (denyPolicies==
        0?22:0) + (active.isEmpty()?35:0) + weakAgentPolicies*4);
        String posture = risk >= 70 ? "CRITICAL" : risk >= 45 ? "HIGH" : risk >= 20 ? "ELEVATED" : "HEALTHY";
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("engine", "EVIDENCE_GROUNDED_SECURITY_COPILOT");
        out.put("mode", "READ_ONLY_ANALYSIS");
        out.put("tenantId", tenantId);
        out.put("windowMinutes", windowMinutes);
        out.put("generatedAt", Instant.now());
        out.put("posture", posture);
        out.put("riskScore", Math.round(risk*10.0)/10.0);
        out.put("activePolicies", active.size());
        out.put("denyPolicies",
        denyPolicies);
        out.put("stepUpPolicies", stepUpPolicies);
        out.put("criticalGraphNodes", criticalNodes);
        out.put("highGraphNodes", highNodes);
        out.put("recentCases", recentCases);
        out.put("recentResponseActions",
        recentResponses);
        out.put("findings", findings);
        out.put("guardrail", "Copilot never publishes or executes a security change directly. Changes " +
"must pass Policy Control Plane / Response approval.");
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> recommendPolicy(UUID tenantId, String scenario) {
        tenant.set(tenantId);
        String s = Optional.ofNullable(scenario).orElse("high-risk AI agent action").toLowerCase(Locale.ROOT);
        String name = s.contains("exfil") || s.contains("data") ? "ai_agent_data_exfiltration_guard" :
                s.contains("credential") || s.contains("secret") ? "ai_agent_credential_access_guard" :
                s.contains("payment") || s.contains("transfer") ? "ai_agent_high_value_transfer_guard" :
                "ai_agent_risk_step_up_guard";
        String text;
        if (name.contains("exfiltration")) text = policy(name, "deny",
        "AI agent data export to external destinations", "action == \"data.export\"",
        "resource.classification in [\"CONFIDENTIAL\", \"RESTRICTED\"] and destination.type == \"external\"");
        else if (name.contains("credential")) text = policy(name, "deny",
        "Block AI agent credential or secret access", "action in [\"credential.read\", \"secret.read\"]",
        "risk.score >= 60 or agent.behavior == \"ANOMALOUS\"");
        else if (name.contains("transfer")) text = policy(name, "step_up",
        "Step up high-value AI agent transfers", "action == \"payment.transfer\"",
        "context.amount > 10000000 or risk.score >= 70");
        else text = policy(name, "step_up", "Step up risky AI agent actions",
        "principal.type == \"AI_AGENT\"", "risk.score >= 65 or agent.behavior == \"ANOMALOUS\"");
        return Map.of("recommendationType", "POLICY_DRAFT", "scenario",
        scenario, "name", name, "confidence", copilotConfidence, "policyText", text,
                "reasoning", List.of("Matches the requested threat scenario",
                "Uses existing Policy DSL v2", "Adds runtime risk context where applicable",
                "Requires validation, simulation and human approval before enforcement"),
                "nextSteps", List.of("Validate DSL", "Simulate against recent runtime decisions",
                "Review semantic diff", "Request approval", "Canary", "Publish"));
    }

    private String policy(String name,String effect,String desc,String match,
    String condition){
        return "policy \""+name+"\" {\n  priority 20\n  effect "+effect+"\n  description \""+
            desc+"\"\n  mode \"enforce\"\n  tags [\"ai-agent\", \"recommended\"]\n\n  "+match+"\n\n  condition {\n    "+
                condition+"\n  }\n}";
    }
    private boolean contains(String s,String x){
        return s!=null && s.toLowerCase(Locale.ROOT).contains(x.
        toLowerCase(Locale.ROOT));
    }
    private double severity(Object x){
        return x instanceof Number n?n.doubleValue():
        Double.parseDouble(String.valueOf(x==null?0:x));
    }
    private int severityRank(String s){
        return switch(s){
            case "CRITICAL"->0;
            case "HIGH"->1;
            case "MEDIUM"->2;
            default->3;
            }
            ;
            }
    private void finding(List<Map<String,Object>> f,String sev,String code,
    String title,String rec,String source,long count){
        f.add(new LinkedHashMap<>(Map.of("severity",
        sev,"code",code,"title",title,"recommendation",rec,"source",source,"count",
        count)));
        }
}
