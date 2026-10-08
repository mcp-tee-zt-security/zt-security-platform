package com.zt.security.riskintelligence;

import com.zt.security.runtime.AgentRuntimeEvent;
import com.zerotrust.security.config.RiskScoringProperties;
import com.zt.security.runtime.AgentRuntimeEventRepository;
import com.zt.security.common.TenantSession;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RiskForecastService {
    @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.critical-threshold:85.0}")
    private double criticalThreshold;
    @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.high-threshold:65.0}")
    private double highThreshold;
    @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.elevated-threshold:40.0}")
    private double elevatedThreshold;

    private final AgentRuntimeEventRepository events;
    private final TenantSession tenant;
    private final RiskScoringProperties riskConfig;
    RiskForecastService(AgentRuntimeEventRepository events, TenantSession tenant, RiskScoringProperties riskConfig){
        this.events=events;
        this.tenant=tenant;
        this.riskConfig=riskConfig;
        }

    public Map<String,Object> overview(UUID t,int hours,int horizon){
        tenant.set(t);
        Instant cutoff=Instant.now().minus(hours, ChronoUnit.HOURS);
        List<AgentRuntimeEvent> rows=events.findTop10000ByTenantIdOrderByCreatedAtDesc(t).stream().filter(e->
        e.getCreatedAt()!=null&&e.getCreatedAt().isAfter(cutoff)).toList();
        Map<String,List<AgentRuntimeEvent>> grouped=rows.stream().filter(e->e.getAgentExternalId()!=
        null).collect(Collectors.groupingBy(AgentRuntimeEvent::getAgentExternalId));
        List<Map<String,Object>> forecasts=grouped.entrySet().stream().map(x->forecast(x.getKey(),
        x.getValue(),hours,horizon)).sorted((a,b)->Double.compare(((Number)b.get("forecastScore")).doubleValue(),
        ((Number)a.get("forecastScore")).doubleValue())).toList();
        return Map.of("windowHours",hours,"forecastHours",horizon,"eventCount",
        rows.size(),"agentCount",forecasts.size(),"highProbabilityAgents",forecasts.stream().filter(x->
        ((Number)x.get("highProbability")).doubleValue()>=0.6).count(),
        "forecasts",forecasts);
    }

    public Map<String,Object> agent(UUID t,String id,int hours,int horizon){
        tenant.set(t);
        Instant cutoff=Instant.now().minus(hours,ChronoUnit.HOURS);
        List<AgentRuntimeEvent> rows=events.findTop10000ByTenantIdOrderByCreatedAtDesc(t).stream().filter(e->
        id.equals(e.getAgentExternalId())&&e.getCreatedAt()!=null&&e.getCreatedAt().isAfter(cutoff)).sorted(Comparator.
        comparing(AgentRuntimeEvent::getCreatedAt)).toList();
        return forecast(id,rows,hours,horizon);
    }

    private Map<String,Object> forecast(String id,List<AgentRuntimeEvent> rows,int hours,int horizon){
        double current=score(rows);
        double previous=rows.size()>1?score(rows.subList(0,
        Math.max(1,rows.size()/2))):current;
        double drift=current-previous;
        double hourlyDrift=hours<=0?0:drift/Math.max(1.0,hours/2.0);
        double projected=Math.min(riskConfig.getMaxScore(),
        Math.max(0,current+hourlyDrift*horizon));
        double probability=probability(projected,drift,rows.size());
        String band=projected>=criticalThreshold?"CRITICAL":projected>=highThreshold?"HIGH":projected>=elevatedThreshold?"ELEVATED":"NORMAL";
        List<String> drivers=drivers(rows);
        List<String> paths=new ArrayList<>();
        if(rows.stream().anyMatch(e->e.getToolId()!=null)) paths.add("Agent → MCP Tool → Action");
        if(rows.stream().anyMatch(e->"DENY".equalsIgnoreCase(e.getDecision())))
paths.add("Agent → Policy Enforcement → DENY pressure");
        if(rows.stream().anyMatch(e->e.getRiskScore()!=null&&e.getRiskScore()>=
        70)) paths.add("Agent → High-risk runtime event → Resource exposure");
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("agent",
        id);
        out.put("currentScore",round(current));
        out.put("riskDrift",round(drift));
        out.put("forecastScore",round(projected));
        out.put("highProbability",round(probability));
        out.put("forecastBand",band);
        out.put("forecastHorizonHours",horizon);
        out.put("eventCount",
        rows.size());
        out.put("drivers",drivers);
        out.put("exposurePaths",paths);
        out.put("confidence",confidence(rows.size(),Math.abs(drift)));
        out.put("recommendation",
        recommend(projected,probability));
        out.put("explainability","Forecast is deterministic and evidence-grounded; it"
+
" does not directly change enforcement.");
        return out;
    }
    private double score(List<AgentRuntimeEvent> rows){
        if(rows.isEmpty())return 20;
        long denied=rows.stream().filter(e->"DENY".equalsIgnoreCase(e.getDecision())).count(),
        step=rows.stream().filter(e->"STEP_UP".equalsIgnoreCase(e.getDecision())).count(),
        high=rows.stream().filter(e->e.getRiskScore()!=null&&e.getRiskScore()>=70).count();
        double avg=rows.stream().filter(e->e.getRiskScore()!=null).mapToDouble(AgentRuntimeEvent::getRiskScore).average().orElse(0);
        return Math.min(riskConfig.getMaxScore(),riskConfig.getForecastBaseRisk()
        +Math.min(riskConfig.getForecastDeniedPressureMax(),denied*riskConfig.getForecastDeniedWeight())
        +Math.min(riskConfig.getForecastStepPressureMax(),step*riskConfig.getForecastStepWeight())
        +Math.min(riskConfig.getForecastHighPressureMax(),high*riskConfig.getForecastHighWeight())
        +Math.min(riskConfig.getForecastAveragePressureMax(),avg*riskConfig.getForecastAverageWeight()));
        }
    private double probability(double score,double drift,int n){
        double x=(score-riskConfig.getForecastProbabilityCenter())/riskConfig.getForecastProbabilityScale()+Math.max(0,
        drift)/riskConfig.getForecastProbabilityDriftScale()+Math.min(1.0,n/riskConfig.getForecastProbabilitySampleDivisor())*riskConfig.getForecastProbabilitySampleWeight();
        return 1.0/(1.0+Math.exp(-x));
        }
    private double confidence(int n,double drift){
        return round(Math.min(riskConfig.getForecastConfidenceCap(),
        riskConfig.getForecastConfidenceBase()+Math.min(riskConfig.getForecastConfidenceSampleCap(),n/riskConfig.getForecastConfidenceSampleDivisor())+Math.min(riskConfig.getForecastConfidenceDriftCap(),drift/riskConfig.getForecastConfidenceDriftDivisor())));
        }
    private List<String> drivers(List<AgentRuntimeEvent> r){
        List<String>d=new ArrayList<>();
        if(r.stream().anyMatch(e->"DENY".equalsIgnoreCase(e.getDecision())))d.add("Repeated DENY pressure");
        if(r.stream().anyMatch(e->"STEP_UP".equalsIgnoreCase(e.getDecision())))d.add("Elevated step-up pressure");
        if(r.stream().anyMatch(e->e.getRiskScore()!=null&&e.getRiskScore()>=70))d.add("High-risk runtime events");
        long tools=r.stream().map(AgentRuntimeEvent::getToolId).filter(Objects::nonNull).distinct().count();
        if(tools>=3)d.add("Broad tool exposure");
        if(d.isEmpty())d.add("No material escalation driver");
        return d;
        }
    private String recommend(double s,double p){
        if(s>=criticalThreshold||p>=riskConfig.getForecastPreventiveProbability())return "PREVENTIVE_CONTAINMENT_REVIEW";
        if(s>=highThreshold||p>=riskConfig.getForecastStepUpProbability())return "STEP_UP_AND_INVESTIGATE";
        if(s>=elevatedThreshold||p>=riskConfig.getForecastMonitorProbability())return "MONITOR_AND_REASSESS";
        return "NO_ACTION";
        }
    private double round(double x){
        return Math.round(x*100.0)/100.0;
    }
}
