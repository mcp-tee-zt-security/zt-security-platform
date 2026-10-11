package com.zt.security.runtime;

import com.zt.security.behavior.*;
import com.zt.security.common.TenantSession;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class RuntimeIntelligenceService {
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
        List<AgentRuntimeSession> ss=sessions.findTop50ByTenantIdOrderByLastSeenAtDesc(t).stream().filter(x->
        agent.equals(x.getAgentExternalId())).toList();
        List<AgentAnomaly> anomalies=behavior.anomalies(t,agent);
        List<AdaptiveDecision> decisions=behavior.decisions(t,
        agent);
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
        List<AgentRuntimeSession> ss=sessions.findTop50ByTenantIdOrderByLastSeenAtDesc(t);
        List<AgentAnomaly> all=behavior.anomalies(t,null);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("activeSessions",
        ss.stream().filter(x->"ACTIVE".equals(x.getStatus())).count());
        out.put("agents",
        ss.stream().map(AgentRuntimeSession::getAgentExternalId).filter(Objects::nonNull).distinct().count());
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
        var ss=sessions.findTop50ByTenantIdOrderByLastSeenAtDesc(t);
        List<?> rows;
        if("SESSIONS".equals(view))rows=ss.stream().filter(s->"ACTIVE".equals(s.getStatus())).map(this::session).toList();
        else if("AGENTS".equals(view))rows=ss.stream().map(AgentRuntimeSession::getAgentExternalId).filter(Objects::nonNull).distinct().map(agent->Map.of("agent",agent,"activeSessions",ss.stream().filter(s->agent.equals(s.getAgentExternalId())&&"ACTIVE".equals(s.getStatus())).count())).toList();
        else rows=behavior.anomalies(t,null).stream().filter(a->"ALL".equals(severity)||"HIGH".equals(a.getSeverity())||"CRITICAL".equals(a.getSeverity())).limit(500).toList();
        return Map.of("items",rows,"scope","TENANT","limit","ANOMALIES".equals(view)?500:50);
    }
    private Map<String,Object> session(AgentRuntimeSession s){
        return Map.of("id",
        s.getId(),"agent",s.getAgentExternalId(),"task",String.valueOf(s.getTaskExternalId()),
        "status",s.getStatus(),"startedAt",s.getStartedAt(),"lastSeenAt",s.getLastSeenAt());
    }
}
