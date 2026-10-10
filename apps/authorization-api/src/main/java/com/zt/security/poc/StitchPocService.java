package com.zt.security.poc;

import com.zt.security.common.TenantSession;
import com.zt.security.common.WorkspaceSession;
import com.zt.security.policy.*;
import com.zt.security.action.EvaluateModels;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

/** Synthetic, scoped retrieval PoC. Resource settings are authoritative server data. */
@Service @Transactional
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="zt.poc.stitch.enabled",havingValue="true")
public class StitchPocService {
    private static final String SCOPE="tenant_id=:tenant AND workspace_id=:workspace";
    private final NamedParameterJdbcTemplate jdbc;
    private final TenantSession tenants;
    private final WorkspaceSession workspaces;
    private final PolicyService policies;
    private final PolicyEvaluator evaluator;
    @org.springframework.beans.factory.annotation.Value("${zt.poc.stitch.audience:zt-stitch-poc}") private String audience;
    public StitchPocService(NamedParameterJdbcTemplate jdbc,TenantSession tenants,WorkspaceSession workspaces,PolicyService policies,PolicyEvaluator evaluator){this.jdbc=jdbc;this.tenants=tenants;this.workspaces=workspaces;this.policies=policies;this.evaluator=evaluator;}
    private record Actor(String subject,String type,boolean admin){}
    private Actor actor(Authentication a){
        if(a==null||!a.isAuthenticated())throw new AccessDeniedException("Authenticated caller required");
        var roles=a.getAuthorities().stream().map(x->x.getAuthority()).toList();
        boolean admin=roles.contains("ROLE_PLATFORM")||roles.contains("ROLE_ADMIN");
        boolean ai=roles.contains("ROLE_SERVICE_CLIENT")||roles.contains("ROLE_AI_AGENT");
        if(a instanceof JwtAuthenticationToken jwt){
            String kind=jwt.getToken().getClaimAsString("actor_type");
            if(!jwt.getToken().getAudience().contains(audience)||!List.of("HUMAN","AI_AGENT").contains(kind==null?"":kind))throw new AccessDeniedException("PoC JWT requires trusted actor_type and matching audience");
            if("AI_AGENT".equals(kind))ai=true;
            if(!ai&&!admin&&!"HUMAN".equals(kind))throw new AccessDeniedException("PoC requires actor_type HUMAN/AI_AGENT or registered service credentials");
        }else if(!ai&&!admin)throw new AccessDeniedException("Unsupported PoC authentication");
        return new Actor(a.getName(),ai?"AI_AGENT":"HUMAN",admin&&!ai);
    }
    public Object context(Authentication a){var x=actor(a);return Map.of("subject",x.subject(),"actorType",x.type(),"canManage",x.admin(),"humanMode","HUMAN".equals(x.type())?"Authenticated administrator or HUMAN-claimed JWT; synthetic human ACL only":"Not a human session");}
    private Actor scopedActor(UUID tenant,UUID workspace,Authentication auth){
        if(auth instanceof JwtAuthenticationToken jwt){
            if(!tenant.toString().equalsIgnoreCase(jwt.getToken().getClaimAsString("tenant_id"))||!workspace.toString().equalsIgnoreCase(jwt.getToken().getClaimAsString("workspace_id")))throw new AccessDeniedException("PoC JWT requires matching tenant_id and workspace_id");
        }
        return actor(auth);
    }
    private MapSqlParameterSource scope(UUID t,UUID w){
        if(w==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Workspace required");
        tenants.set(t);workspaces.set(w);
        if(!Integer.valueOf(1).equals(jdbc.queryForObject("SELECT count(*) FROM workspaces WHERE tenant_id=:tenant AND id=:workspace",Map.of("tenant",t,"workspace",w),Integer.class)))throw new AccessDeniedException("Workspace does not belong to tenant");
        // Serialize synthetic PoC operations within one workspace, including configuration writes.
        // This small demo does not claim production retrieval throughput.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))",Map.of("key","stitch-poc:"+t+":"+w),Object.class);
        return new MapSqlParameterSource().addValue("tenant",t).addValue("workspace",w);
    }
    private List<Map<String,Object>> areas(MapSqlParameterSource p){return jdbc.queryForList("SELECT area_id,parent_id,name,kind,agent_access,human_access FROM stitch_poc_areas WHERE "+SCOPE+" ORDER BY area_id FOR SHARE",p);}
    private Map<String,Map<String,Object>> index(List<Map<String,Object>> rows){Map<String,Map<String,Object>> map=new HashMap<>();for(var r:rows)map.put((String)r.get("area_id"),r);return map;}
    private boolean permitted(String id,Map<String,Map<String,Object>> rows,boolean ai){
        Set<String> seen=new HashSet<>();String current=id;
        while(current!=null){if(!seen.add(current))return false;var r=rows.get(current);if(r==null)return false;if(ai?!"ALLOW".equals(r.get("agent_access")):!Boolean.TRUE.equals(r.get("human_access")))return false;current=(String)r.get("parent_id");}return true;
    }
    private List<Policy> effectivePolicies(UUID tenant,UUID workspace){
        List<Policy> result=new ArrayList<>();
        for(var p:policies.forEvaluation(tenant,UUID.randomUUID()))if(p.getWorkspaceId()==null||workspace.equals(p.getWorkspaceId()))result.add(p);
        // PoC guardrails are mandatory and never published into tenant production policy tables.
        result.add(guard("stitch_poc_ai_deny","deny","AI_AGENT","resource.agentAccess == \"DENY\""));
        result.add(guard("stitch_poc_ai_allow","allow","AI_AGENT","resource.agentAccess == \"ALLOW\""));
        result.add(guard("stitch_poc_human_deny","deny","HUMAN","resource.humanAccess == false"));
        result.add(guard("stitch_poc_human_allow","allow","HUMAN","resource.humanAccess == true"));return result;
    }
    private Policy guard(String name,String effect,String type,String condition){
        Policy p=new Policy();p.setId(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));p.setName(name);p.setVersion(1);p.setEffect(effect);p.setPriority(0);
        p.setPolicyText("policy \""+name+"\" { effect "+effect+" principal.type == \""+type+"\" condition { "+condition+" } }");return p;
    }
    private PolicyEvaluator.Result evaluate(Actor a,String operation,String resource,String area,Map<String,Map<String,Object>> rows,List<Policy> active){
        boolean ai=permitted(area,rows,true),human=permitted(area,rows,false);
        return evaluator.evaluate(active,new EvaluateModels.EvaluateRequest(new EvaluateModels.Principal(a.subject(),a.type(),Map.of()),new EvaluateModels.Action("stitch."+operation),new EvaluateModels.Resource("stitch_document",resource,Map.of("agentAccess",ai?"ALLOW":"DENY","humanAccess",human)),Map.of("source","STITCH_SYNTHETIC_POC")));
    }
    public Object management(UUID t,UUID w,Authentication auth){if(!scopedActor(t,w,auth).admin())throw new AccessDeniedException("Human administrator required for private metadata");var p=scope(t,w);var rows=areas(p);var map=index(rows);List<Map<String,Object>> output=new ArrayList<>();for(var r:rows){var x=new LinkedHashMap<>(r);x.put("effectiveAgentAccess",permitted((String)r.get("area_id"),map,true)?"ALLOW":"DENY");output.add(x);}return Map.of("areas",output,"documents",jdbc.queryForList("SELECT document_id,area_id,title FROM stitch_poc_documents WHERE "+SCOPE+" ORDER BY document_id",p),"synthetic",true);}
    public Object setup(UUID t,UUID w,Authentication auth){
        var a=scopedActor(t,w,auth);if(!a.admin())throw new AccessDeniedException("Administrator required");var p=scope(t,w);
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))",Map.of("key","stitch-poc:"+t+":"+w),Object.class);
        seedArea(p,"general",null,"General documents","FOLDER","ALLOW");
        seedArea(p,"payroll",null,"Payroll","FOLDER","DENY");
        seedArea(p,"payroll-archive","payroll","Payroll archive (inherits parent deny)","FOLDER","ALLOW");
        seedArea(p,"general-channel",null,"General channel","CHANNEL","ALLOW");
        seedArea(p,"payroll-channel",null,"Payroll channel","CHANNEL","DENY");
        seedDocument(p,"handbook","general","Company handbook","SYNTHETIC DEMO: handbook and payroll process overview; no salary details.");
        seedDocument(p,"payroll-2026","payroll","Synthetic payroll October","SYNTHETIC DEMO: payroll Alice 5000, Bob 6000. Not real personal data.");
        seedDocument(p,"payroll-archive-2025","payroll-archive","Synthetic payroll archive","SYNTHETIC DEMO: historical payroll salary 4500.");
        seedDocument(p,"general-message","general-channel","General channel message","SYNTHETIC DEMO: payroll procedures are published in the handbook.");
        seedDocument(p,"payroll-message","payroll-channel","Restricted channel message","SYNTHETIC DEMO: payroll salary adjustment for Alice.");
        record(t,w,a,"setup","fixtures","ALLOW","Synthetic fixtures initialized; existing settings and data preserved",0,0);return management(t,w,auth);
    }
    private void seedArea(MapSqlParameterSource p,String id,String parent,String name,String kind,String access){jdbc.update("INSERT INTO stitch_poc_areas(tenant_id,workspace_id,area_id,parent_id,name,kind,agent_access) VALUES(:tenant,:workspace,:id,:parent,:name,:kind,:access) ON CONFLICT DO NOTHING",new MapSqlParameterSource(p.getValues()).addValue("id",id).addValue("parent",parent).addValue("name",name).addValue("kind",kind).addValue("access",access));}
    private void seedDocument(MapSqlParameterSource p,String id,String area,String title,String content){jdbc.update("INSERT INTO stitch_poc_documents(tenant_id,workspace_id,document_id,area_id,title,content) VALUES(:tenant,:workspace,:id,:area,:title,:content) ON CONFLICT DO NOTHING",new MapSqlParameterSource(p.getValues()).addValue("id",id).addValue("area",area).addValue("title",title).addValue("content",content));}
    public Object access(UUID t,UUID w,String id,String access,Authentication auth){var a=scopedActor(t,w,auth);if(!a.admin())throw new AccessDeniedException("Administrator required");var p=scope(t,w).addValue("id",id).addValue("access",access);if(!List.of("ALLOW","DENY").contains(access))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid access value");if(jdbc.update("UPDATE stitch_poc_areas SET agent_access=:access WHERE "+SCOPE+" AND area_id=:id",p)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Area not found");record(t,w,a,"configureAccess",id,"ALLOW","AI access set to "+access,0,0);return management(t,w,auth);}
    public Object read(UUID t,UUID w,String id,Authentication auth){
        var a=scopedActor(t,w,auth);var p=scope(t,w).addValue("id",id);var rows=index(areas(p));var meta=jdbc.queryForList("SELECT document_id,area_id FROM stitch_poc_documents WHERE "+SCOPE+" AND document_id=:id",p);
        if(meta.isEmpty())return response(t,w,a,"readDocument",id,"DENY","Unavailable or inaccessible resource",List.of(),0);
        var d=evaluate(a,"readDocument",id,(String)meta.get(0).get("area_id"),rows,effectivePolicies(t,w));
        if(!"ALLOW".equals(d.decision()))return response(t,w,a,"readDocument",id,d.decision(),"Access blocked by server policy",List.of(),0);
        var data=jdbc.queryForList("SELECT document_id,title,content FROM stitch_poc_documents WHERE "+SCOPE+" AND document_id=:id",p);
        return response(t,w,a,"readDocument",id,"ALLOW","Authorized content returned",data,data.size());
    }
    public Object search(UUID t,UUID w,String query,Authentication auth){
        var a=scopedActor(t,w,auth);var p=scope(t,w);var rows=index(areas(p));var active=effectivePolicies(t,w);var metadata=jdbc.queryForList("SELECT document_id,area_id FROM stitch_poc_documents WHERE "+SCOPE,p);List<String> ids=new ArrayList<>();
        for(var m:metadata)if("ALLOW".equals(evaluate(a,"searchDocuments",(String)m.get("document_id"),(String)m.get("area_id"),rows,active).decision()))ids.add((String)m.get("document_id"));
        List<Map<String,Object>> data=List.of();if(!ids.isEmpty()){p.addValue("ids",ids).addValue("query",query);data=jdbc.queryForList("SELECT document_id,title,content FROM stitch_poc_documents WHERE "+SCOPE+" AND document_id IN (:ids) AND (position(lower(:query) in lower(title))>0 OR position(lower(:query) in lower(content))>0) ORDER BY document_id LIMIT 50",p);}
        // No hidden names, counts, snippets or scores are returned to AI callers.
        return response(t,w,a,"searchDocuments",null,"ALLOW","Search filtered to authorized resources before content retrieval",data,data.size());
    }
    public Object channel(UUID t,UUID w,String id,Authentication auth){
        var a=scopedActor(t,w,auth);var p=scope(t,w).addValue("id",id);var rows=index(areas(p));var area=rows.get(id);
        if(area==null||!"CHANNEL".equals(area.get("kind")))return response(t,w,a,"listChannelMessages",id,"DENY","Unavailable or inaccessible resource",List.of(),0);
        var d=evaluate(a,"listChannelMessages",id,id,rows,effectivePolicies(t,w));if(!"ALLOW".equals(d.decision()))return response(t,w,a,"listChannelMessages",id,d.decision(),"Access blocked by server policy",List.of(),0);
        var data=jdbc.queryForList("SELECT document_id,title,content FROM stitch_poc_documents WHERE "+SCOPE+" AND area_id=:id ORDER BY document_id LIMIT 50",p);return response(t,w,a,"listChannelMessages",id,"ALLOW","Authorized messages returned",data,data.size());
    }
    private Object response(UUID t,UUID w,Actor a,String op,String id,String decision,String reason,List<Map<String,Object>> data,int reads){UUID call=record(t,w,a,op,id,decision,reason,data.size(),reads);return Map.of("callId",call,"subject",a.subject(),"actorType",a.type(),"decision",decision,"reason",reason,"contentReads",reads,"results",data,"synthetic",true);}
    private UUID record(UUID t,UUID w,Actor a,String op,String id,String decision,String reason,int count,int reads){UUID call=UUID.randomUUID();jdbc.update("INSERT INTO stitch_poc_calls(id,tenant_id,workspace_id,subject,actor_type,operation,resource_id,decision,reason,returned_count,content_reads) VALUES(:id,:tenant,:workspace,:subject,:type,:operation,:resource,:decision,:reason,:count,:reads)",new MapSqlParameterSource().addValue("id",call).addValue("tenant",t).addValue("workspace",w).addValue("subject",a.subject()).addValue("type",a.type()).addValue("operation",op).addValue("resource",id).addValue("decision",decision).addValue("reason",reason).addValue("count",count).addValue("reads",reads));return call;}
    public Object calls(UUID t,UUID w,Authentication auth){var a=scopedActor(t,w,auth);var p=scope(t,w).addValue("subject",a.subject());return jdbc.queryForList("SELECT id,subject,actor_type,operation,resource_id,decision,reason,returned_count,content_reads,created_at FROM stitch_poc_calls WHERE "+SCOPE+(a.admin()?"":" AND subject=:subject")+" ORDER BY created_at DESC LIMIT 100",p);}
}
