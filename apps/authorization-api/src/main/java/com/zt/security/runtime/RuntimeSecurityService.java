package com.zt.security.runtime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.action.*;
import com.zt.security.agent.*;
import com.zt.security.common.*;
import com.zt.security.identity.*;
import com.zt.security.workspace.*;
import com.zt.security.idempotency.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service public class RuntimeSecurityService {
 private final AgentRuntimeSessionRepository sessions;
 private final AgentRuntimeEventRepository events;
 private final ActionEvaluationService actions;
 private final TenantSession tenant;
 private final WorkspaceSession workspace;
 private final ObjectMapper mapper;
 private final IdentityRepository identities;
 private final AgentTaskRepository tasks;
 private final AgentToolRepository tools;
 private final TaskToolRepository taskTools;
 private final WorkspaceRepository workspaces;
 private final IdempotencyService idempotency;
 private final long sessionTtlSeconds;
 RuntimeSecurityService(AgentRuntimeSessionRepository s,AgentRuntimeEventRepository e,
 ActionEvaluationService a,TenantSession t,WorkspaceSession w,ObjectMapper m,
 IdentityRepository i,AgentTaskRepository tr,AgentToolRepository ar,TaskToolRepository tt,
 WorkspaceRepository wr,IdempotencyService idem,@org.springframework.beans.factory.annotation.Value("${zt.security.runtime.session-ttl-seconds:3600}")
long ttl){
     sessions=s;
     events=e;
     actions=a;
     tenant=t;
     workspace=w;
     mapper=m;
     identities=i;
     tasks=tr;
     tools=ar;
     taskTools=tt;
     workspaces=wr;
     idempotency=idem;
     sessionTtlSeconds=ttl;
 }
 @Transactional public Map<String,Object> start(UUID t,UUID w,StartRequest r){
     tenant.set(t);
     if(w!=null){
         workspaces.findByTenantIdAndId(t,w).orElseThrow(()->
         new org.springframework.security.access.AccessDeniedException("workspace not found"));
         workspace.set(w);
         }
         if(r.agent()==null||r.agent().isBlank())throw new
IllegalArgumentException("agent is required");
     var identity=identities.findByTenantIdAndExternalId(t,r.agent()).orElseThrow(()->
     new org.springframework.security.access.AccessDeniedException("agent identity not found"));
     if(!"AI_AGENT".equalsIgnoreCase(identity.getIdentityType())||!"ACTIVE".equalsIgnoreCase(identity.
getStatus()))throw new org.springframework.security.access.AccessDeniedException("identity is not an active AI agent");
     if(r.taskId()!=null&&!r.taskId().isBlank()){
         var task=tasks.findByTenantIdAndExternalTaskId(t,
         r.taskId()).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("task not found"));
         if(!identity.getId().equals(task.getAgentIdentityId()))throw new org.springframework.security.
         access.AccessDeniedException("task does not belong to agent");
         if(!"ACTIVE".equalsIgnoreCase(task.getStatus())||(task.getExpiresAt()!=
null&&task.getExpiresAt().isBefore(Instant.now())))throw new org.springframework.security.access.AccessDeniedException("task is not active");
     }
     AgentRuntimeSession s=new AgentRuntimeSession();
     s.setTenantId(t);
     s.setWorkspaceId(w);
 s.setAgentExternalId(r.agent());
 s.setTaskExternalId(r.taskId());
 s.setSource(r.source()==null?"SDK":r.source());
 s.setMetadata(json(r.metadata()));
 sessions.save(s);
 return session(s);
 }
 @Transactional public EvaluateModels.EvaluateResponse check(UUID t,UUID w,
 UUID sid,String idemKey,EvaluateModels.EvaluateRequest req){
     tenant.set(t);
     if(w!=null)workspace.set(w);
     AgentRuntimeSession s=sessions.findByIdAndTenantId(sid,
     t).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("runtime session not found"));
if(!"ACTIVE".equals(s.getStatus()))throw new org.springframework.security.access.AccessDeniedException("runtime session is not active");
     if(s.getLastSeenAt().plusSeconds(sessionTtlSeconds).isBefore(Instant.now())){
         s.setStatus("EXPIRED");
         s.setEndedAt(Instant.now());
         sessions.save(s);
         throw new org.springframework.security.access.AccessDeniedException("runtime session expired");
     }
     if(s.getWorkspaceId()!=null&&(w==null||!s.getWorkspaceId().equals(w)))throw new org.springframework.
     security.access.AccessDeniedException("runtime session workspace mismatch");
 if(!s.getAgentExternalId().equals(req.principal().id()))throw new org.springframework.security.access.
 AccessDeniedException("runtime session agent mismatch");
 if(!"AI_AGENT".equalsIgnoreCase(req.principal().type()))throw new org.springframework.security.access.
 AccessDeniedException("runtime principal must be AI_AGENT");
 var identity=identities.findByTenantIdAndExternalId(t,req.principal().id()).orElseThrow(()->
 new org.springframework.security.access.AccessDeniedException("agent identity not found"));
 if(!"AI_AGENT".equalsIgnoreCase(identity.getIdentityType())||!"ACTIVE".equalsIgnoreCase(identity.getStatus()))throw
new org.
 springframework.security.access.AccessDeniedException("agent identity is not active");
 String taskId=s.getTaskExternalId();
 if(taskId!=null){
     var task=tasks.findByTenantIdAndExternalTaskId(t,
     taskId).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("task not found"));
     if(!identity.getId().equals(task.getAgentIdentityId()))throw new org.springframework.security.access.
     AccessDeniedException("task agent mismatch");
     if(!"ACTIVE".equalsIgnoreCase(task.getStatus())||(task.getExpiresAt()!=null&&
task.getExpiresAt().isBefore(Instant.now())))throw new org.springframework.security.access.AccessDeniedException("task is not active");
 }
 if(req.context()!=null&&req.context().get("tool_id")!=null&&taskId!=null){
 try{
     UUID toolId=UUID.fromString(String.valueOf(req.context().get("tool_id")));
     if(!tools.existsById(toolId)||!taskTools.toolIds(tasks.findByTenantIdAndExternalTaskId(t,
taskId).orElseThrow().getId()).contains(toolId))throw new org.springframework.security.access.AccessDeniedException("tool is not attached to runtime task");
}
catch(IllegalArgumentException ex){
    throw new org.springframework.security.access.AccessDeniedException("invalid tool_id");
 }
 }
 if(idemKey!=null&&!idemKey.isBlank()){
     var existing=idempotency.existing(t,
 idemKey,req);
 if(existing.isPresent())try{
     return mapper.readValue(existing.get(),
     EvaluateModels.EvaluateResponse.class);
     }
     catch(Exception ex){
         throw new IllegalStateException(ex);
 }
 }
 Map<String,Object> c=new LinkedHashMap<>();
 if(req.context()!=null)c.putAll(req.context());
 c.put("session_id",sid.toString());
 if(s.getTaskExternalId()!=null)c.putIfAbsent("task_id",
 s.getTaskExternalId());
 EvaluateModels.EvaluateRequest enriched=new EvaluateModels.EvaluateRequest(req.principal(),
 req.action(),req.resource(),c);
 EvaluateModels.EvaluateResponse out=actions.evaluate(t,
 enriched);
 s.setLastSeenAt(Instant.now());
 sessions.save(s);
 AgentRuntimeEvent ev=new AgentRuntimeEvent();
 ev.setTenantId(t);
 ev.setWorkspaceId(w);
 ev.setSessionId(sid);
 ev.setRequestId(out.requestId());
 ev.setAgentExternalId(req.principal().id());
 ev.setTaskExternalId(String.valueOf(c.getOrDefault("task_id",
 s.getTaskExternalId())));
 ev.setToolId(String.valueOf(c.getOrDefault("tool_id",
 "")));
 ev.setAction(req.action().name());
 ev.setResourceType(req.resource().type());
 ev.setResourceId(req.resource().id());
 ev.setDecision(out.decision());
 ev.setRiskScore(out.risk().score());
 ev.setLatencyMs(out.latencyMs());
 ev.setContext(json(c));
 events.save(ev);
 idempotency.complete(t,idemKey,out);
 return out;
 }
 @Transactional public Map<String,Object> end(UUID t,UUID sid){
     tenant.set(t);
     AgentRuntimeSession s=sessions.findByIdAndTenantId(sid,t).orElseThrow();
     s.setStatus("ENDED");
     s.setEndedAt(Instant.now());
     s.setLastSeenAt(Instant.now());
     sessions.save(s);
     return session(s);
     }
 @Transactional public Map<String,Object> detail(UUID t,UUID sid){
     tenant.set(t);
     AgentRuntimeSession s=sessions.findByIdAndTenantId(sid,t).orElseThrow();
     return Map.of("session",session(s),"eventCount",events.countByTenantIdAndSessionId(t,
     sid),"events",events.findTop100ByTenantIdAndSessionIdOrderByCreatedAtDesc(t,
     sid));
     }
 @Transactional public List<Map<String,Object>> list(UUID t){
     tenant.set(t);
     return sessions.findTop50ByTenantIdOrderByLastSeenAtDesc(t).stream().map(this::session).toList();
 }
 private Map<String,Object> session(AgentRuntimeSession s){
     Map<String,
     Object> m=new LinkedHashMap<>();
     m.put("id",s.getId());
     m.put("tenantId",
     s.getTenantId());
     m.put("workspaceId",s.getWorkspaceId());
     m.put("agent",
     s.getAgentExternalId());
     m.put("task",s.getTaskExternalId());
     m.put("status",
     s.getStatus());
     m.put("source",s.getSource());
     m.put("startedAt",s.getStartedAt());
     m.put("lastSeenAt",s.getLastSeenAt());
     m.put("endedAt",s.getEndedAt());
     return m;
     }
 private String json(Object x){
     try{
         return mapper.writeValueAsString(x==null?Map.of():x);
     }
     catch(Exception e){
         return "{}";
     }
     }
 public record StartRequest(String agent,String taskId,String source,Map<String,Object> metadata){
 }
}
