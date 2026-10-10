package com.zt.security.stitch;

import com.zt.security.action.EvaluateModels.*;
import com.zt.security.policy.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Published Policy Studio DSL, evaluated with server-derived resource context. */
@Component @ConditionalOnProperty(name="zt.stitch.enabled",havingValue="true")
public class RetrievalPolicyGate {
    private final PolicyRepository policies;private final PolicyEvaluator evaluator;
    private final NamedParameterJdbcTemplate jdbc;private final RetrievalPolicyInvalidator invalidator;
    public RetrievalPolicyGate(PolicyRepository policies,PolicyEvaluator evaluator,NamedParameterJdbcTemplate jdbc,RetrievalPolicyInvalidator invalidator){this.policies=policies;this.evaluator=evaluator;this.jdbc=jdbc;this.invalidator=invalidator;}
    public static final class Snapshot {
        final List<Policy> policies;final String connection;final Map<String,PolicyEvaluator.Result> decisions=new LinkedHashMap<>();
        Snapshot(List<Policy> policies,String connection){this.policies=policies;this.connection=connection;}
    }
    public Snapshot prepare(UUID t,UUID w){
        var p=new MapSqlParameterSource().addValue("tenant",t).addValue("workspace",w);
        var connections=jdbc.queryForList("SELECT connection_id FROM retrieval_connections WHERE tenant_id=:tenant AND workspace_id=:workspace AND enabled=true",p);
        if(connections.size()!=1)throw new AccessDeniedException("Register an enabled retrieval connection in ZT for this workspace");
        var active=policies.findByTenantIdAndStatusOrderByPriorityAsc(t,"ACTIVE").stream().filter(x->x.getWorkspaceId()==null||w.equals(x.getWorkspaceId())).toList();
        if(active.size()>200)throw new AccessDeniedException("Retrieval policy scope exceeds 200 active policies");
        for(var policy:active)try{PolicyDsl.parse(policy.getPolicyText());}catch(IllegalArgumentException ex){throw new AccessDeniedException("Invalid active ZT policy; retrieval fails closed");}
        String connection=(String)connections.get(0).get("connection_id");
        String fingerprint=digest(connection+active.stream().sorted(Comparator.comparing(x->x.getId().toString())).map(x->x.getId()+":"+x.getVersion()+":"+x.getEffect()+":"+x.getPolicyText()).toList());
        String previous=jdbc.queryForObject("SELECT policy_fingerprint FROM stitch_integration_scopes WHERE tenant_id=:tenant AND workspace_id=:workspace",p,String.class);
        if(!fingerprint.equals(previous)){
            invalidator.invalidate(t,w,"ZT_POLICY_CHANGED");
            jdbc.update("UPDATE stitch_integration_scopes SET policy_fingerprint=:fingerprint WHERE tenant_id=:tenant AND workspace_id=:workspace",p.addValue("fingerprint",fingerprint));
        }
        return new Snapshot(active,connection);
    }
    public boolean permits(Snapshot snapshot,UUID w,String id,String permission,String ai,String human,Set<String> groups,UUID session,Map<String,Map<String,Object>> resources){
        String key=permission+":"+id;var result=snapshot.decisions.get(key);
        if(result==null){
            List<String> ancestors=new ArrayList<>();String current=id;
            while(current!=null){if(ancestors.contains(current)||ancestors.size()>=64||!resources.containsKey(current))return false;ancestors.add(current);current=(String)resources.get(current).get("parent_id");}
            var row=resources.get(id);
            var attributes=Map.<String,Object>of("id",id,"kind",row.get("kind"),"ancestors",ancestors,"connectionId",snapshot.connection);
            var context=Map.<String,Object>of("connectionId",snapshot.connection,"workspaceId",w.toString(),"humanSubject",human,"groups",new TreeSet<>(groups),"sessionId",session.toString());
            var request=new EvaluateRequest(new Principal(ai,"AI_AGENT",Map.of("humanSubject",human)),new Action("retrieval."+permission.toLowerCase(Locale.ROOT)),new Resource("retrieval_resource",id,attributes),context);
            result=evaluator.evaluate(snapshot.policies,request);snapshot.decisions.put(key,result);
        }
        return "ALLOW".equals(result.decision()); // STEP_UP never releases content without an approval flow.
    }
    public String reason(Snapshot snapshot){
        if(snapshot==null)return "Source user ACL evaluated; no AI policy required for human access";
        var denied=snapshot.decisions.values().stream().filter(x->!"ALLOW".equals(x.decision())).map(PolicyEvaluator.Result::reason).distinct().limit(5).toList();
        return denied.isEmpty()?"Source ACL and active ZT retrieval policy evaluated":String.join("; ",denied);
    }
    public List<MatchedPolicy> matched(Snapshot snapshot){return snapshot==null?List.of():snapshot.decisions.values().stream().flatMap(x->x.matched().stream()).distinct().toList();}
    private String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
