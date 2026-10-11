package com.zt.security.runtime;

import com.zt.security.behavior.*;
import com.zt.security.common.TenantSession;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
@org.springframework.transaction.annotation.Transactional(readOnly=true)
public class RuntimeIntelligenceService {
    @org.springframework.beans.factory.annotation.Autowired private com.zt.security.event.SecurityActivityLedger ledger;
    private final AgentRuntimeSessionRepository sessions;
    private final AgentRuntimeEventRepository runtimeEvents;
    private final AgentBehaviorService behavior;
    private final TenantSession tenant;
    RuntimeIntelligenceService(AgentRuntimeSessionRepository s, AgentRuntimeEventRepository e,
    AgentBehaviorService b, TenantSession t){
        sessions=s;
        runtimeEvents=e;
        behavior=b;
        tenant=t;
        }

    public Map<String,Object> agent(UUID t,String agent){
        tenant.set(t);
        List<AgentRuntimeSession> ss=scopedSessions(t).stream().filter(x->
        agent.equals(x.getAgentExternalId())).toList();
        var requestIds=requestIds(t);
        List<AgentAnomaly> anomalies=behavior.anomalies(t,agent).stream().filter(a->requestIds.contains(a.getRequestId())).toList();
        List<AdaptiveDecision> decisions=behavior.decisions(t,agent).stream().filter(a->requestIds.contains(a.getRequestId())).toList();
        long active=ss.stream().filter(x->"ACTIVE".equals(x.getStatus())).count();
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("agent",
        agent);
        out.put("activeSessions",active);
        out.put("sessions",ss.stream().map(this::session).toList());
        out.put("anomalies",anomalies.stream().limit(20).toList());
        out.put("adaptiveDecisions",
        decisions.stream().limit(20).toList());
        out.put("threatSummary",summary(anomalies));
        return out;
    }
    public Map<String,Object> overview(UUID t){
        tenant.set(t);
        List<AgentRuntimeSession> ss=scopedSessions(t);
        var requestIds=requestIds(t);
        List<AgentAnomaly> all=behavior.anomalies(t,null).stream().filter(a->requestIds.contains(a.getRequestId())).toList();
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("activeSessions",
        ss.stream().filter(x->"ACTIVE".equals(x.getStatus())).count());
        out.put("agents",
        ledger.analysis(t,ledger.selectedWorkspace()).stream().map(AgentRuntimeEvent::getAgentExternalId).filter(Objects::nonNull).distinct().count());
        out.put("recentAnomalies",all.stream().limit(20).toList());
        out.put("threatSummary",
        summary(all));
        return out;
    }
    private Map<String,Object> summary(List<AgentAnomaly> a){
        Map<String,
        Long> by=new LinkedHashMap<>();
        for(AgentAnomaly x:a) by.merge(x.getAnomalyType(),
        1L,Long::sum);
        return Map.of("total",a.size(),"byType",by,"highOrCritical",
        a.stream().filter(x->"HIGH".equals(x.getSeverity())||"CRITICAL".equals(x.getSeverity())).count());
    }
    public Object records(UUID t,String view,String severity){
        tenant.set(t);
        if(!Set.of("SESSIONS","AGENTS","ANOMALIES").contains(view)||!Set.of("ALL","HIGH_CRITICAL").contains(severity))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid runtime record filter");
        var ss=scopedSessions(t);
        List<?> rows;
        if("SESSIONS".equals(view))rows=ss.stream().filter(s->"ACTIVE".equals(s.getStatus())).map(this::session).toList();
        else if("AGENTS".equals(view))rows=ledger.analysis(t,ledger.selectedWorkspace()).stream().map(AgentRuntimeEvent::getAgentExternalId).filter(Objects::nonNull).distinct().map(agent->Map.of("agent",agent,"activeSessions",ss.stream().filter(s->agent.equals(s.getAgentExternalId())&&"ACTIVE".equals(s.getStatus())).count())).toList();
        else {var visible=requestIds(t);rows=behavior.anomalies(t,null).stream().filter(a->visible.contains(a.getRequestId())).filter(a->"ALL".equals(severity)||"HIGH".equals(a.getSeverity())||"CRITICAL".equals(a.getSeverity())).limit(500).toList();}
        return Map.of("items",rows,"scope","WORKSPACE","limit","ANOMALIES".equals(view)?500:50);
    }
    private List<AgentRuntimeSession> scopedSessions(UUID t){UUID workspace=ledger.selectedWorkspace();var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();boolean all=auth!=null&&auth.getAuthorities().stream().anyMatch(a->Set.of("ROLE_PLATFORM","ROLE_ADMIN","ROLE_AUDITOR","ROLE_SOC_ANALYST","ROLE_AGENT_MANAGER").contains(a.getAuthority()));return sessions.findTop50ByTenantIdOrderByLastSeenAtDesc(t).stream().filter(s->Objects.equals(workspace,s.getWorkspaceId())&&(all||auth!=null&&auth.getName().equals(s.getAgentExternalId()))).toList();}
    private Set<UUID> requestIds(UUID t){return ledger.analysis(t,ledger.selectedWorkspace()).stream().map(AgentRuntimeEvent::getRequestId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());}
    private Map<String,Object> session(AgentRuntimeSession s){
        return Map.of("id",
        s.getId(),"agent",s.getAgentExternalId(),"task",String.valueOf(s.getTaskExternalId()),
        "status",s.getStatus(),"startedAt",s.getStartedAt(),"lastSeenAt",s.getLastSeenAt());
    }
}
