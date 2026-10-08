package com.zt.security.behavior;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.agent.*;
import com.zt.security.common.TenantSession;
import com.zt.security.identity.Identity;
import com.zt.security.identity.IdentityRepository;
import com.zt.security.policy.Policy;
import com.zt.security.policy.PolicyDsl;
import com.zt.security.policy.PolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SecurityGraphService {
    private final ProfileRepo profiles;
    private final EventRepo events;
    private final AnomalyRepo anomalies;
    private final AdaptiveDecisionRepository decisions;
    private final AgentTaskRepository tasks;
    private final AgentToolRepository tools;
    private final TaskToolRepository taskTools;
    private final IdentityRepository identities;
    private final PolicyService policies;
    private final TenantSession tenant;
    private final ObjectMapper mapper;

    SecurityGraphService(ProfileRepo profiles, EventRepo events, AnomalyRepo anomalies,
                         AdaptiveDecisionRepository decisions, AgentTaskRepository tasks,
                         AgentToolRepository tools, TaskToolRepository taskTools,
                         IdentityRepository identities, PolicyService policies,
                         TenantSession tenant, ObjectMapper mapper) {
        this.profiles = profiles;
        this.events = events;
        this.anomalies = anomalies;
        this.decisions = decisions;
        this.tasks = tasks;
        this.tools = tools;
        this.taskTools = taskTools;
        this.identities = identities;
        this.policies = policies;
        this.tenant = tenant;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> snapshot(UUID tenantId, int windowMinutes) {
        tenant.set(tenantId);
        Instant since = Instant.now().minusSeconds(Math.max(5, Math.min(windowMinutes, 10080)) * 60L);
        List<AgentBehaviorEvent> ev = events.findByTenantIdAndCreatedAtAfterOrderByCreatedAtAsc(tenantId, since);
        List<AgentAnomaly> an = anomalies.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(x -> x.getCreatedAt() != null && x.getCreatedAt().isAfter(since)).toList();
        List<AdaptiveDecision> ds = decisions.findByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(tenantId, since);
        List<AgentTask> ts = tasks.findByTenantId(tenantId);
        List<AgentTool> tls = tools.findByTenantId(tenantId);
        List<Identity> ids = identities.findByTenantId(tenantId);

        Map<String,Map<String,Object>> nodes = new LinkedHashMap<>();
        List<Map<String,Object>> edges = new ArrayList<>();
        Map<String,Double> risk = new HashMap<>();
        Map<String,String> identityNames = ids.stream().collect(Collectors.toMap(i -> i.getId().toString(),
        i -> Optional.ofNullable(i.getName()).orElse(i.getExternalId()), (a,b)->a));
        Map<String,String> toolNames = tls.stream().collect(Collectors.toMap(x -> x.getId().toString(),
        AgentTool::getName, (a,b)->a));

        for (AgentBehaviorProfile p : profiles.findAll()) {
            String agent = "agent:" + p.getAgentExternalId();
            add(nodes, agent, "AGENT", p.getAgentExternalId(), p.getPeerGroup(), 0);
        }
        for (Identity i : ids) {
            if ("AI_AGENT".equals(i.getIdentityType())) {
                String agent = "agent:" + i.getExternalId();
                add(nodes, agent, "AGENT", i.getExternalId(), "", 0);
                add(nodes, "identity:" + i.getId(), "IDENTITY", Optional.ofNullable(i.getName()).orElse(i.
                getExternalId()),
                "", 0);
                edge(edges, "identity:" + i.getId(), agent, "OPERATES_AS", Map.of());
            }
        }
        // MCP is treated as an explicit trust boundary in the graph.
        // This makes Agent -> Task -> MCP Gateway -> Tool -> Action -> Resource
        // visible and allows attack-path analysis to account for the gateway boundary.
        String mcpGateway = "mcp:gateway";
        add(nodes, mcpGateway, "MCP_GATEWAY", "MCP Security Gateway", "ENFORCED", 0);

        for (AgentTask t : ts) {
            String task = "task:" + t.getExternalTaskId();
            String identity = "identity:" + t.getAgentIdentityId();
            add(nodes, task, "TASK", t.getPurpose(), t.getStatus(), 0);
            edge(edges, identity, task, "OWNS_TASK", Map.of("status", t.getStatus()));
            for (UUID toolId : taskTools.toolIds(t.getId())) {
                String tool = "tool:" + toolId;
                add(nodes, tool, "TOOL", toolNames.getOrDefault(toolId.toString(), toolId.toString()), "", 0);
                edge(edges, task, mcpGateway, "ROUTES_THROUGH", Map.of("boundary", "MCP"));
                edge(edges, mcpGateway, tool, "EXPOSES_TOOL", Map.of("toolId", toolId.toString()));
            }
        }

        Map<String,String> lastAction = new HashMap<>();
        for (AgentBehaviorEvent e : ev) {
            String agent = "agent:" + e.getAgentExternalId();
            String action = "action:" + e.getAction();
            String resource = "resource:" + e.getResourceType();
            add(nodes, action, "ACTION", e.getAction(), e.getDecision(),
            e.getRiskScore() == null ? 0 : e.getRiskScore());
            add(nodes, resource, "RESOURCE", e.getResourceType(), "", e.getRiskScore() == null ? 0 : e.getRiskScore());
            add(nodes, agent, "AGENT", e.getAgentExternalId(), "", e.getRiskScore() == null ? 0 : e.getRiskScore());
            edge(edges, agent, action, "EXECUTES", Map.of("decision", e.getDecision()));
            if (e.getToolId() != null) {
                String tool = "tool:" + e.getToolId();
                add(nodes, tool, "TOOL", toolNames.getOrDefault(e.getToolId().toString(),
                e.getToolId().toString()), "", e.getRiskScore()==null?0:e.getRiskScore());
                edge(edges, tool, action, "INVOKES", Map.of("requestId", String.valueOf(e.getRequestId())));
            }
            edge(edges, action, resource, "TARGETS", Map.of());
            String prev = lastAction.put(e.getAgentExternalId(), action);
            if (prev != null && !prev.equals(action)) edge(edges, prev,
            action, "NEXT", Map.of("agent", e.getAgentExternalId()));
        }

        for (Policy p : policies.active(tenantId)) {
            try {
                PolicyDsl d = PolicyDsl.parse(p.getPolicyText());
                if (d.action() != null) {
                    String action = "action:" + d.action();
                    add(nodes, action, "ACTION", d.action(), p.getEffect(), risk.getOrDefault(action, 0.0));
                    edge(edges, "policy:" + p.getId(), action, "GOVERNS",
                    Map.of("effect", p.getEffect(), "policy", p.getName()));
                    add(nodes, "policy:" + p.getId(), "POLICY", p.getName(), p.getStatus(), 0);
                }
            }
            catch (Exception ignored) {
            }
        }

        for (AgentAnomaly a : an) {
            String agent = "agent:" + a.getAgentExternalId();
            String anomaly = "behavior:" + a.getId();
            add(nodes, anomaly, "BEHAVIOR", a.getAnomalyType(), a.getSeverity(), a.getScore());
            edge(edges, agent, anomaly, "ANOMALY", Map.of("score", a.getScore(), "type", a.getAnomalyType()));
            risk.merge(agent, a.getScore(), Math::max);
        }
        for (AdaptiveDecision d : ds) risk.merge("agent:" + d.getAgentExternalId(), d.getBehaviorScore(), Math::max);

        // Risk propagation: behavior -> agent -> task/tool -> action/resource.
        for (Map.Entry<String,Double> x : risk.entrySet()) {
            Map<String,Object> n = nodes.get(x.getKey());
            if (n != null) n.put("risk", round(x.getValue()));
        }
        propagateRisk(nodes, edges);
        double maxRisk = nodes.values().stream().map(n -> ((Number)n.getOrDefault("risk",
        0)).doubleValue()).max(Double::compare).orElse(0.0);
        return Map.of("tenantId", tenantId, "generatedAt", Instant.now(), "windowMinutes", windowMinutes,
                "nodes", nodes.values(), "edges", edges, "metrics", Map.of("nodeCount",
                nodes.size(),"edgeCount",edges.size(),"maxRisk",round(maxRisk),"anomalies",
                an.size()));
    }

    private void propagateRisk(Map<String,Map<String,Object>> nodes, List<Map<String,Object>> edges) {
        for (int pass=0; pass<3; pass++) {
            for (Map<String,Object> e : edges) {
                String from=String.valueOf(e.get("from")), to=String.valueOf(e.get("to"));
                double r=((Number)nodes.getOrDefault(from,Map.of("risk",0)).getOrDefault("risk",0)).doubleValue();
                if (r <= 0) continue;
                double factor = switch(String.valueOf(e.get("relation"))) {
                    case "ANOMALY" -> 1.0;
                    case "EXECUTES","DELEGATES","OWNS_TASK" -> 0.75;
                    case "NEXT" -> 0.55;
                    case "TARGETS","GOVERNS" -> 0.45;
                    default -> 0.35;
                }
                ;
                Map<String,Object> n=nodes.get(to);
                if(n!=null){
                    double old=((Number)n.getOrDefault("risk",
                    0)).doubleValue();
                    n.put("risk",round(Math.max(old,r*factor)));
                    }
            }
        }
    }
    private void add(Map<String,Map<String,Object>> n,String id,String type,
    String label,String status,double r){
        Map<String,Object> x=n.computeIfAbsent(id,
        k->{
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("id",id);
            m.put("type",
            type);
            m.put("label",label);
            m.put("status",status);
            m.put("risk",0.0);
            return m;
        }
        );
        double old=((Number)x.getOrDefault("risk",0)).doubleValue();
        x.put("risk",
    round(Math.max(old,r)));
    }
    private void edge(List<Map<String,Object>> e,String from,String to,
    String relation,Map<String,Object> meta){
        if(from==null||to==null)return;
        e.add(Map.of("from",from,"to",to,"relation",relation,"meta",meta));
        }
    private double round(double x){
        return Math.round(x*100.0)/100.0;
    }
}
