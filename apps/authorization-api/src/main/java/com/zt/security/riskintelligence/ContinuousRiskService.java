package com.zt.security.riskintelligence;

import com.zt.security.runtime.*;
import com.zerotrust.security.config.RiskScoringProperties;
import com.zt.security.common.TenantSession;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.stream.*;

@Service
public class ContinuousRiskService {
    @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.critical-threshold:85.0}")
    private double criticalThreshold;
    @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.high-threshold:65.0}")
    private double highThreshold;
    @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.elevated-threshold:40.0}")
    private double elevatedThreshold;

    private final AgentRuntimeEventRepository events;
    private final TenantSession tenant;
    private final RiskScoringProperties riskConfig;
    ContinuousRiskService(AgentRuntimeEventRepository events, TenantSession tenant, RiskScoringProperties riskConfig){
        this.events=events;
        this.tenant=tenant;
        this.riskConfig=riskConfig;
        }

    public Map<String,Object> overview(UUID t,int hours){
        tenant.set(t);
        List<AgentRuntimeEvent> rows=events.findTop10000ByTenantIdOrderByCreatedAtDesc(t);
        Instant cutoff=Instant.now().minus(hours, java.time.temporal.ChronoUnit.HOURS);
        rows=rows.stream().filter(e->e.getCreatedAt()!=null && e.getCreatedAt().isAfter(cutoff)).toList();
        Map<String,List<AgentRuntimeEvent>> grouped=rows.stream().filter(e->e.getAgentExternalId()!=
        null).collect(Collectors.groupingBy(AgentRuntimeEvent::getAgentExternalId));
        List<Map<String,Object>> agents=grouped.entrySet().stream().map(x->score(x.getKey(),
        x.getValue(),hours)).sorted((a,b)->Double.compare(((Number)b.get("riskScore")).doubleValue(),
        ((Number)a.get("riskScore")).doubleValue())).toList();
        double avg=agents.stream().mapToDouble(x->((Number)x.get("riskScore")).doubleValue()).average().orElse(0);
        return Map.of("windowHours",hours,"eventCount",rows.size(),"agentCount",
        agents.size(),"averageRisk",round(avg),"highRiskAgents",agents.stream().filter(x->
        ((Number)x.get("riskScore")).doubleValue()>=highThreshold).count(),
        "criticalAgents",agents.stream().filter(x->((Number)x.get("riskScore")).doubleValue()>=criticalThreshold).count(),
        "agents",agents);
    }

    public Map<String,Object> agent(UUID t,String id,int hours){
        tenant.set(t);
        Instant cutoff=Instant.now().minus(hours, java.time.temporal.ChronoUnit.HOURS);
        List<AgentRuntimeEvent> rows=events.findTop10000ByTenantIdOrderByCreatedAtDesc(t).stream().filter(e->
        id.equals(e.getAgentExternalId()) && e.getCreatedAt()!=null &&
e.getCreatedAt().isAfter(cutoff)).sorted(Comparator.
        comparing(AgentRuntimeEvent::getCreatedAt)).toList();
        Map<String,Object> current=score(id,rows,hours);
        List<Map<String,Object>> timeline=timeline(id,rows);
        List<String> drivers=drivers(rows);
        current.put("timeline",timeline);
        current.put("riskDrivers",drivers);
        current.put("earlyWarning",warning(current,timeline));
        current.put("recommendedAction",recommend(current));
        return current;
    }

    private Map<String,Object> score(String agent,List<AgentRuntimeEvent> rows,int hours){
        double base=riskConfig.getContinuousBaseRisk();
        long denied=rows.stream().filter(e->"DENY".equalsIgnoreCase(e.getDecision())).count();
        long step=rows.stream().filter(e->"STEP_UP".equalsIgnoreCase(e.getDecision())).count();
        double avgRisk=rows.stream().filter(e->e.getRiskScore()!=null).mapToDouble(AgentRuntimeEvent::getRiskScore).average().orElse(0);
        long high=rows.stream().filter(e->e.getRiskScore()!=null && e.getRiskScore()>=70).count();
        double decisionPressure=Math.min(riskConfig.getContinuousDecisionPressureMax(), denied*riskConfig.getContinuousDeniedWeight()+step*riskConfig.getContinuousStepUpWeight());
        double riskPressure=Math.min(riskConfig.getContinuousRiskPressureMax(),
        avgRisk*riskConfig.getContinuousRiskWeight());
        double anomalyPressure=Math.min(riskConfig.getContinuousAnomalyPressureMax(),high*riskConfig.getContinuousAnomalyWeight());
        double score=Math.min(riskConfig.getContinuousScoreMax(),base+decisionPressure+riskPressure+anomalyPressure);
        double previous=rows.size()>1 ? rollingScore(rows.subList(0,Math.max(1,rows.size()/2))) : score;
        double drift=score-previous;
        String state=score>=criticalThreshold?"CRITICAL":score>=highThreshold?"HIGH":score>=elevatedThreshold?"ELEVATED":"NORMAL";
        String direction=drift>=riskConfig.getContinuousDirectionThreshold()?"RISING":drift<=-riskConfig.getContinuousDirectionThreshold()?"FALLING":"STABLE";
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("agent",
        agent);
        out.put("riskScore",round(score));
        out.put("riskState",state);
        out.put("riskDirection",direction);
        out.put("riskDrift",round(drift));
        out.put("eventCount",rows.size());
        out.put("denyCount",denied);
        out.put("stepUpCount",
        step);
        out.put("highRiskEvents",high);
        out.put("averageEventRisk",round(avgRisk));
        out.put("windowHours",hours);
        return out;
    }
    private double rollingScore(List<AgentRuntimeEvent> rows){
        double avg=rows.stream().filter(e->e.getRiskScore()!=null).mapToDouble(AgentRuntimeEvent::getRiskScore).average().orElse(0);
        long d=rows.stream().filter(e->"DENY".equalsIgnoreCase(e.getDecision())).count();
        return Math.min(riskConfig.getContinuousScoreMax(),riskConfig.getContinuousBaseRisk()+Math.min(riskConfig.getContinuousDecisionPressureMax(),d*riskConfig.getContinuousDeniedWeight())+Math.min(riskConfig.getContinuousRiskPressureMax(),avg*riskConfig.getContinuousRiskWeight()));
    }
    private List<Map<String,Object>> timeline(String agent,List<AgentRuntimeEvent> rows){
        List<Map<String,Object>> out=new ArrayList<>();
        int step=Math.max(1,
        rows.size()/12);
        for(int i=0;i<rows.size();i+=step){
            List<AgentRuntimeEvent> w=rows.subList(i,
            Math.min(rows.size(),i+step));
            out.add(Map.of("timestamp",w.get(w.size()-1).getCreatedAt(),
            "riskScore",round(rollingScore(w)),"events",w.size(),"deny",w.stream().filter(e->
            "DENY".equalsIgnoreCase(e.getDecision())).count(),
            "highRisk",w.stream().filter(e->e.getRiskScore()!=null&&e.getRiskScore()>=70).count()));
        }
        return out;
    }
    private List<String> drivers(List<AgentRuntimeEvent> rows){
        List<String> d=new ArrayList<>();
        if(rows.stream().anyMatch(e->"DENY".equalsIgnoreCase(e.getDecision())))d.
        add("Repeated policy DENY decisions");
        if(rows.stream().anyMatch(e->"STEP_UP".equalsIgnoreCase(e.getDecision())))d.add("Step-up pressure increased");
        if(rows.stream().anyMatch(e->e.getRiskScore()!=null&&e.getRiskScore()>=
        70))d.add("High-risk runtime events detected");
        long tools=rows.stream().map(AgentRuntimeEvent::getToolId).filter(Objects::nonNull).distinct().count();
if(tools>=3)d.add("Broad tool usage / delegation surface");
if(d.isEmpty())d.add("No material risk driver detected");
        return d;
    }
    private boolean warning(Map<String,Object> current,List<Map<String,
    Object>> timeline){
        return "RISING".equals(current.get("riskDirection")) ||
        ((Number)current.get("riskScore")).doubleValue()>=highThreshold;
    }
    private String recommend(Map<String,Object> c){
        double s=((Number)c.get("riskScore")).doubleValue();
        if(s>=criticalThreshold)return "CONTAIN_AGENT_AND_REVIEW";
        if(s>=highThreshold)return "STEP_UP_AND_REVIEW";
        if(s>=elevatedThreshold)return "MONITOR";
        return "NO_ACTION";
        }
    private double round(double x){
        return Math.round(x*10.0)/10.0;
    }
}
