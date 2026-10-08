package com.zt.security.attack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.behavior.SecurityGraphService;
import com.zt.security.common.TenantSession;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class AttackPathService {
    private final SecurityGraphService graph;
    private final TenantSession tenant;
    private final ObjectMapper mapper;
    private final double attackNodeWeight;

    AttackPathService(SecurityGraphService graph, TenantSession tenant, ObjectMapper mapper,
                     @Value("${zt.security.risk-scoring.attack-node-weight:0.35}") double attackNodeWeight) {
        this.graph = graph;
        this.tenant = tenant;
        this.mapper = mapper;
        this.attackNodeWeight = attackNodeWeight;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames="attack-path", key="#tenantId.toString()+':'+#agent+':'+#windowMinutes+':'+" +
"#maxDepth+':'+#includeDenied")
    public Map<String,Object> assess(UUID tenantId, String agent, int windowMinutes,
    int maxDepth, boolean includeDenied) {
        tenant.set(tenantId);
        Map<String,Object> snapshot = graph.snapshot(tenantId, windowMinutes);
        List<Map<String,Object>> nodes = castList(snapshot.get("nodes"));
        List<Map<String,Object>> edges = castList(snapshot.get("edges"));
        Map<String,Map<String,Object>> byId = new LinkedHashMap<>();
        for (Map<String,Object> n : nodes) byId.put(String.valueOf(n.get("id")), n);

        String source = agent.startsWith("agent:") ? agent : "agent:" + agent;
        Map<String,Object> sourceNode = byId.get(source);
        if (sourceNode == null) return result(tenantId, source, "COMPROMISED_AGENT",
        0, "LOW", List.of(), List.of(), List.of(), maxDepth);

        // The graph has identity -> agent and identity -> task edges. A compromised agent
        // inherits the tasks operated by its identity, so bridge those edges for attack analysis.
        List<Map<String,Object>> analysisEdges = new ArrayList<>(edges);
        Map<String,String> agentToIdentity = new HashMap<>();
        for (Map<String,Object> e : edges) {
            if ("OPERATES_AS".equals(e.get("relation")) && String.valueOf(e.get("to")).equals(source))
                agentToIdentity.put(source, String.valueOf(e.get("from")));
        }
        if (!agentToIdentity.isEmpty()) {
            String identity = agentToIdentity.get(source);
            for (Map<String,Object> e : edges) if (identity.equals(e.get("from")) &&
            "OWNS_TASK".equals(e.get("relation")))
                analysisEdges.add(edge(source, String.valueOf(e.get("to")),
                "INHERITS_TASK", Map.of("reason","compromised-agent")));
        }

        Map<String,List<Map<String,Object>>> outgoing = new HashMap<>();
        for (Map<String,Object> e : analysisEdges) outgoing.computeIfAbsent(String.valueOf(e.get("from")),
        k -> new ArrayList<>()).add(e);

        List<Map<String,Object>> paths = new ArrayList<>();
        Set<String> reachedResources = new LinkedHashSet<>();
        Set<String> blockedResources = new LinkedHashSet<>();
        ArrayDeque<State> q = new ArrayDeque<>();
        q.add(new State(source, List.of(source), 0, 100.0, false));
        Set<String> visited = new HashSet<>();
        while (!q.isEmpty()) {
            State s = q.removeFirst();
            if (s.depth >= Math.max(1, Math.min(maxDepth, 12))) continue;
            for (Map<String,Object> e : outgoing.getOrDefault(s.node, List.of())) {
                String to = String.valueOf(e.get("to"));
                String rel = String.valueOf(e.get("relation"));
                Map<String,Object> target = byId.get(to);
                if (target == null) continue;
                boolean denied = isDenied(target, rel);
                if (denied && !includeDenied) continue;
                double factor = relationFactor(rel);
                double risk = Math.min(100, s.risk * factor + nodeRisk(target) * attackNodeWeight);
                List<String> p = new ArrayList<>(s.path);
                p.add(to);
                String key = to + "|" + Math.min(s.depth + 1, 12);
                if (!visited.add(key) && risk <= s.risk) continue;
                boolean blocked = s.blocked || denied;
                State next = new State(to, p, s.depth + 1, risk, blocked);
                if ("RESOURCE".equals(target.get("type"))) {
                    if (blocked) blockedResources.add(to);
                    else reachedResources.add(to);
                    Map<String,Object> row = new LinkedHashMap<>();
                    row.put("resource", to);
                    row.put("resourceLabel", target.get("label"));
                    row.put("risk", round(risk));
                    row.put("blocked", blocked);
                    row.put("depth", next.depth);
                    row.put("path", p);
                    row.put("severity", severity(risk));
                    paths.add(row);
                }
                q.addLast(next);
            }
        }

        paths.sort((a,b)->Double.compare(num(b.get("risk")), num(a.get("risk"))));
        List<Map<String,Object>> critical = paths.stream().filter(x -> num(x.get("risk")) >=
        75 && !Boolean.TRUE.equals(x.get("blocked"))).limit(50).toList();
        double maxRisk = paths.stream().filter(x -> !Boolean.TRUE.equals(x.get("blocked"))).mapToDouble(x ->
        num(x.get("risk"))).max().orElse(0);
        String sev = severity(maxRisk);
        List<Map<String,Object>> mitigations = mitigations(paths, byId);
        return result(tenantId, source, "COMPROMISED_AGENT", maxRisk, sev,
        paths, new ArrayList<>(reachedResources), new ArrayList<>(blockedResources),
        maxDepth, critical, mitigations);
    }

    private Map<String,Object> result(UUID tenantId, String source, String scenario, double risk, String severity,
                                      List<Map<String,Object>> paths, List<String> resources,
                                      List<String> blocked, int maxDepth) {
        return result(tenantId, source, scenario, risk, severity, paths,
        resources, blocked, maxDepth, List.of(), List.of());
    }

    private Map<String,Object> result(UUID tenantId, String source, String scenario, double risk, String severity,
                                      List<Map<String,Object>> paths, List<String> resources,
                                      List<String> blocked, int maxDepth,
                                      List<Map<String,Object>> critical, List<Map<String,Object>> mitigations) {
        return new LinkedHashMap<>(Map.ofEntries(
        Map.entry("tenantId", tenantId),
        Map.entry("source", source),
        Map.entry("scenario", scenario),
        Map.entry("generatedAt", Instant.now()),
        Map.entry("riskScore", round(risk)),
        Map.entry("severity", severity),
        Map.entry("reachableResources", resources.size()),
        Map.entry("blockedResources", blocked.size()),
        Map.entry("criticalPaths", critical.size()),
        Map.entry("maxDepth", maxDepth),
        Map.entry("paths", paths.stream().limit(100).toList()),
        Map.entry("criticalPathsDetail", critical),
        Map.entry("mitigations", mitigations)
    ));
    }

    private List<Map<String,Object>> mitigations(List<Map<String,Object>> paths, Map<String,Map<String,Object>> nodes) {
        LinkedHashMap<String,Map<String,Object>> out = new LinkedHashMap<>();
        for (Map<String,Object> p : paths) {
            if (Boolean.TRUE.equals(p.get("blocked")) || num(p.get("risk")) < 65) continue;
            List<?> route = (List<?>) p.get("path");
            String action = route.stream().filter(x -> String.valueOf(x).startsWith("action:")).findFirst().
            map(String::valueOf).orElse(null);
            if (action != null) out.putIfAbsent(action, Map.of("priority",
            "HIGH","control","Add explicit DENY or STEP_UP policy","target",action,
            "reason","High-risk reachable action"));
            String tool = route.stream().filter(x -> String.valueOf(x).startsWith("tool:")).findFirst().
            map(String::valueOf).orElse(null);
            if (tool != null) out.putIfAbsent(tool, Map.of("priority",
            "MEDIUM","control","Remove tool delegation from compromised-agent tasks",
            "target",tool,"reason","Tool contributes to attack path"));
        }
        return new ArrayList<>(out.values());
    }

    private boolean isDenied(Map<String,Object> node, String relation) {
        if (!"ACTION".equals(node.get("type"))) return false;
        String status = String.valueOf(node.getOrDefault("status", ""));
        return "DENY".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status);
    }
    private double relationFactor(String r) {
        return switch (r) {
            case "INHERITS_TASK" -> .95;
            case "DELEGATES" -> .9;
            case "INVOKES" -> .95;
            case "EXECUTES" -> .95;
            case "TARGETS" -> .9;
            case "NEXT" -> .7;
            case "OWNS_TASK" -> .8;
            default -> .75;
        }
        ;
        }
    private double nodeRisk(Map<String,Object> n) {
        return num(n.get("risk"));
    }
    private double num(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        }
        catch(Exception e) {
            return 0;
            }
            }
    private double round(double x) {
        return Math.round(x*100.0)/100.0;
    }
    private String severity(double r) {
        return r >= 85 ? "CRITICAL" : r >= 70 ? "HIGH" : r >= 45 ? "MEDIUM" : "LOW";
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> castList(Object x) {
        return x instanceof List<?> l ? (List<Map<String,Object>>)(List<?>)l : new ArrayList<>();
    }
    private Map<String,Object> edge(String from,String to,String relation,
    Map<String,Object> meta){
        return Map.of("from",from,"to",to,"relation",
        relation,"meta",meta);
        }
    private record State(String node,List<String> path,int depth,double risk,boolean blocked) {
    }
}
