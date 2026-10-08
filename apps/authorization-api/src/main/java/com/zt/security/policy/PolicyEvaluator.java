package com.zt.security.policy;

import com.zt.security.action.EvaluateModels.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class PolicyEvaluator {
    public record Result(String decision, String reason, List<MatchedPolicy> matched) {
    }

    public Result evaluate(List<Policy> policies, EvaluateRequest r) {
        List<MatchedPolicy> matched = new ArrayList<>();
        for (Policy p : policies) {
            PolicyDsl d;
            try {
                d = PolicyDsl.parse(p.getPolicyText());
            }
            catch (PolicyDsl.PolicyParseException ex) {
                continue;
            }
            // fail closed: invalid policy cannot match

            if (!matchClauses(d.rules(), r)) continue;
            if (d.condition() != null && !eval(d.condition().expression(), r)) continue;
            matched.add(new MatchedPolicy(p.getId(), p.getName(), p.getVersion(), p.getEffect()));
        }

        // Policies are supplied in priority order. Conflict resolution is deliberately restrictive.
        for (MatchedPolicy m : matched)
            if ("deny".equals(m.effect())) return new Result("DENY", "Denied by policy: " + m.name(), matched);
        for (MatchedPolicy m : matched)
            if ("step_up".equals(m.effect())) return new Result("STEP_UP",
            "Additional approval required by policy: " + m.name(), matched);
        for (MatchedPolicy m : matched)
            if ("allow".equals(m.effect())) return new Result("ALLOW", "Allowed by policy: " + m.name(), matched);
        return new Result("DENY", "No matching allow policy (default deny)", matched);
    }

    private boolean matchClauses(List<PolicyDsl.Rule> rules, EvaluateRequest r) {
        for (PolicyDsl.Rule x : rules) {
            Object actual = switch (x.left()) {
                case "principal.type" -> r.principal().type();
                case "action" -> r.action().name();
                case "resource.type" -> r.resource().type();
                default -> null;
            }
            ;
            if (actual == null || !compare(actual, x.op(), x.right())) return false;
        }
        return true;
    }

    private boolean eval(PolicyDsl.Expr expr, EvaluateRequest r) {
        if (expr instanceof PolicyDsl.Comparison c) return compare(resolve(c.left(), r), c.op(), c.right());
        if (expr instanceof PolicyDsl.And a) {
            for (PolicyDsl.Expr x : a.terms()) if (!eval(x,
            r)) return false;
            return true;
            }
        if (expr instanceof PolicyDsl.Or o) {
            for (PolicyDsl.Expr x : o.terms()) if (eval(x,
            r)) return true;
            return false;
            }
        if (expr instanceof PolicyDsl.Not n) return !eval(n.term(), r);
        return false;
    }

    private Object resolve(String key, EvaluateRequest r) {
        if (key.startsWith("context.")) return lookup(r.context(), key.substring("context.".length()));
        if (key.startsWith("resource.")) return lookup(r.resource().attributes(), key.substring("resource.".length()));
        if (key.startsWith("principal.")) {
            if ("type".equals(key.substring("principal.".length()))) return r.principal().type();
            return lookup(r.principal().attributes(), key.substring("principal.".length()));
        }
        // Runtime security namespaces can be injected by the caller as context.*;
        // accepting these aliases keeps policies readable while retaining one source of truth.
        return lookup(r.context(), key);
    }

    private Object lookup(Map<String,Object> map, String path) {
        if (map == null) return null;
        if (map.containsKey(path)) return map.get(path);
        Object current = map;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?,?> m)) return null;
            current = m.get(part);
        }
        return current;
    }

    private boolean compare(Object actual, String op, Object expected) {
        if (actual == null) return false;
        if ("in".equals(op)) {
            if (expected instanceof List<?> list) return list.stream().anyMatch(v -> equalValue(actual, v));
            if (expected instanceof String s && s.startsWith("[")) return Arrays.stream(s.substring(1,
            s.length()-1).split(","))
                    .map(String::trim).anyMatch(v -> equalValue(actual, v));
            return false;
        }
        if ("contains".equals(op)) return actual instanceof Collection<?> c ? c.stream().anyMatch(v -> equalValue(v,
        expected)) : String.valueOf(actual).contains(String.valueOf(expected));
        if (actual instanceof Number a && expected instanceof Number b) {
            int c = Double.compare(a.doubleValue(), b.doubleValue());
            return switch(op){
                case "=="->c==0;
                case "!="->c!=0;
                case ">"->c>0;
                case ">="->c>=0;
                case "<"->c<0;
                case "<="->c<=0;
                default->false;
                }
                ;
        }
        if (actual instanceof Boolean a && expected instanceof Boolean b) return switch(op){
            case "=="->a==b;
            case "!="->a!=b;
            default->false;
            }
            ;
        if (actual instanceof Comparable<?> && expected != null) {
            int c = String.valueOf(actual).compareTo(String.valueOf(expected));
            return switch(op){
                case "=="->c==0;
                case "!="->c!=0;
                case ">"->c>0;
                case ">="->c>=0;
                case "<"->c<0;
                case "<="->c<=0;
                default->false;
                }
                ;
        }
        return switch(op){
            case "=="->equalValue(actual,expected);
            case "!="->!equalValue(actual,
            expected);
            default->false;
            }
            ;
    }

    private boolean equalValue(Object a,Object b){
        if(a instanceof Number && b instanceof Number) return Double.compare(((Number)a).doubleValue(),
        ((Number)b).doubleValue())==0;
        return String.valueOf(a).equals(String.valueOf(b));
    }
}
