package com.zt.security.blast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.attack.AttackPathService;
import com.zt.security.common.TenantSession;
import com.zt.security.decision.SecurityAsset;
import com.zt.security.decision.SecurityAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class BlastRadiusService {
    private final double pathRiskWeight;
    private final double criticalityWeight;
    private final AttackPathService attackPaths;
    private final SecurityAssetRepository assets;
    private final BlastRadiusAssessmentRepository assessments;
    private final TenantSession tenant;
    private final ObjectMapper mapper;
    private final double maxScore;
    private final double attackWeight;
    private final double impactWeight;

    BlastRadiusService(AttackPathService attackPaths, SecurityAssetRepository assets,
                       BlastRadiusAssessmentRepository assessments,
                       TenantSession tenant,
                       ObjectMapper mapper,
                       @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.max-score:100.0}") double maxScore,
                       @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.blast-attack-weight:0.55}") double attackWeight,
                       @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.blast-impact-weight:0.45}") double impactWeight,
                       @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.blast-path-risk-weight:0.60}") double pathRiskWeight,
                       @org.springframework.beans.factory.annotation.Value("${zt.security.risk-scoring.blast-criticality-weight:0.40}") double criticalityWeight){
        this.attackPaths=attackPaths;
        this.assets=assets;
        this.assessments=assessments;
        this.tenant=tenant;
        this.mapper = mapper;
        this.maxScore = maxScore;
        this.attackWeight = attackWeight;
        this.impactWeight = impactWeight;
        this.pathRiskWeight = pathRiskWeight;
        this.criticalityWeight = criticalityWeight;
    }

    @Transactional
    public Map<String,Object> assess(UUID tenantId,String agent,int windowMinutes,int maxDepth,boolean includeDenied){
        tenant.set(tenantId);
        Map<String,Object> attack=attackPaths.assess(tenantId,agent,windowMinutes,maxDepth,includeDenied);
        List<Map<String,Object>> paths=list(attack.get("paths"));
        Map<String,SecurityAsset> matched=new LinkedHashMap<>();
        for(Map<String,Object> p:paths){
            if(Boolean.TRUE.equals(p.get("blocked"))) continue;
            String resourceNode=String.valueOf(p.get("resource"));
            String type=resourceNode.startsWith("resource:")?resourceNode.substring(9):resourceNode;
            for(SecurityAsset a:assets.findByTenantIdOrderByCriticalityDesc(tenantId)){
                if(type.equalsIgnoreCase(a.getResourceType()) || type.equalsIgnoreCase(normalize(a.getResourceType())))
matched.
                put(a.getResourceType()+":"+a.getResourceId(),
                a);
            }
        }
        List<Map<String,Object>> impacted=new ArrayList<>();
        int critical=0, restricted=0;
        for(SecurityAsset a:matched.values()){
            String classification=Optional.ofNullable(a.getDataClassification()).orElse("INTERNAL").toUpperCase(Locale.
            ROOT);
            if(a.getCriticality()>=80) critical++;
            if("RESTRICTED".equals(classification) || "CONFIDENTIAL".equals(classification)) restricted++;
            impacted.add(asset(a, paths));
        }
        double attackRisk=num(attack.get("riskScore"));
        double impactFactor = impacted.isEmpty()
                ? 0
                : Math.min(
                        maxScore,
                        impacted.stream()
                                .mapToDouble(x -> num(x.get("impactScore")))
                                .max()
                                .orElse(0));
        double blastScore = round(
                Math.min(
                        maxScore,
                        attackRisk * attackWeight + impactFactor * impactWeight));
        String severity=severity(blastScore);
        List<Map<String,Object>> recommendations=recommend(agent,paths,impacted,blastScore);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("tenantId",tenantId);
        result.put("source","agent:"+agent);
        result.put("generatedAt",Instant.now());
        result.put("riskScore",attackRisk);
        result.put("blastRadiusScore",blastScore);
        result.put("severity",severity);
        result.put("impactedAssets",impacted.size());
        result.put("criticalAssets",
        critical);
        result.put("restrictedAssets",restricted);
        result.put("potentialDataExposure",restricted>0?"HIGH":"MEDIUM");
        result.put("potentialPaymentImpact",containsType(impacted,"bank_account","payment")?"CRITICAL":"LOW");
        result.put("assets",impacted);
        result.put("recommendations",recommendations);
        result.put("attackPaths",paths.stream().limit(50).toList());
        result.put("attackAssessment",attack);
        BlastRadiusAssessment row=new BlastRadiusAssessment();
        row.setTenantId(tenantId);
        row.setSourceNode("agent:"+agent);
        row.setRiskScore(blastScore);
        row.setSeverity(severity);
        row.setImpactedAssets(impacted.size());
        row.setCriticalAssets(critical);
        row.setRestrictedAssets(restricted);
        row.setRecommendedActions(recommendations.size());
        row.setResult(json(result));
        assessments.save(row);
        result.put("assessmentId",row.getId());
        return result;
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> history(UUID tenantId,String agent){
        tenant.set(tenantId);
        return assessments.findTop20ByTenantIdAndSourceNodeOrderByGeneratedAtDesc(tenantId,
        "agent:"+agent).stream().map(x->Map.<String,Object>of(
                "id",x.getId(),"riskScore",x.getRiskScore(),"severity",
                x.getSeverity(),"impactedAssets",x.getImpactedAssets(),"criticalAssets",
                x.getCriticalAssets(),"restrictedAssets",x.getRestrictedAssets(),"recommendedActions",
                x.getRecommendedActions(),"generatedAt",x.getGeneratedAt())).toList();
    }

    private Map<String,Object> asset(SecurityAsset a,List<Map<String,Object>> paths){
        double pathRisk=paths.stream().filter(p->String.valueOf(p.get("resource")).toLowerCase(Locale.
        ROOT).contains(a.getResourceType().toLowerCase(Locale.ROOT))).mapToDouble(p->
        num(p.get("risk"))).max().orElse(0);
        double score = round(
                Math.min(
                        maxScore,
                        pathRisk * pathRiskWeight + a.getCriticality() * criticalityWeight));
        return new LinkedHashMap<>(Map.of("id",a.getId(),"resourceType",
        a.getResourceType(),"resourceId",a.getResourceId(),"criticality",a.getCriticality(),
        "classification",Optional.ofNullable(a.getDataClassification()).orElse("INTERNAL"),
        "owner",Optional.ofNullable(a.getOwner()).orElse("—"),"impactScore",score,
        "severity",severity(score)));
    }
    private List<Map<String,Object>> recommend(String agent,List<Map<String,
    Object>> paths,List<Map<String,Object>> impacted,double score){
        LinkedHashMap<String,Map<String,Object>> out=new LinkedHashMap<>();
        String tool=paths.stream().flatMap(p->((List<?>)p.getOrDefault("path",
        List.of())).stream()).map(String::valueOf).filter(x->x.startsWith("tool:")).findFirst().orElse(null);
        String action=paths.stream().flatMap(p->((List<?>)p.getOrDefault("path",
        List.of())).stream()).map(String::valueOf).filter(x->x.startsWith("action:")).findFirst().orElse(null);
        if(tool!=null) out.put("tool",Map.of("priority","HIGH","type",
        "MCP_CONTAINMENT","title","Revoke MCP tool exposure","control","Disable the tool at the MCP security boundary",
        "target",tool));
        if(action!=null) out.put("action",Map.of("priority",score>=80?"CRITICAL":"HIGH",
        "type","POLICY_CHANGE","title","Protect reachable action","control","Add explicit DENY/STEP_UP policy",
        "target",action));
        if(!impacted.isEmpty()) out.put("asset",Map.of("priority","HIGH",
        "type","ASSET_PROTECTION","title","Review impacted assets","control",
        "Verify owner, classification and least-privilege access",
        "count",impacted.size()));
        if(score>=75) out.put("isolate",Map.of("priority","CRITICAL","type",
        "RUNTIME_CONTAINMENT","title","Contain compromised agent","control",
        "Terminate active runtime sessions and require re-authentication",
        "target","agent:"+agent));
        if(impacted.stream().anyMatch(x->"RESTRICTED".equalsIgnoreCase(String.valueOf(x.get("classification"))))) out.
        put("credential",
        Map.of("priority","HIGH","type","CREDENTIAL_ROTATION","title","Rotate exposed credentials",
        "control","Rotate credentials reachable through affected tools/resources",
        "target","agent:"+agent));
        out.put("review",Map.of("priority","MEDIUM","type","HUMAN_REVIEW",
        "title","Open security review","control","Capture incident evidence and approve remediation",
        "target","agent:"+agent));
        return new ArrayList<>(out.values());
    }
    private boolean containsType(List<Map<String,Object>> xs,String... terms){
        return xs.stream().anyMatch(x->{
            String s=(String.valueOf(x.get("resourceType"))+" "+
            String.valueOf(x.get("resourceId"))).toLowerCase(Locale.ROOT);
            return Arrays.stream(terms).anyMatch(t->s.contains(t.toLowerCase(Locale.ROOT)));
        }
        );
        }
    private String normalize(String x){
        return x==null?"":x.replace("-","_");
    }
    private double num(Object x){
        if(x instanceof Number n)return n.doubleValue();
        try{
            return Double.parseDouble(String.valueOf(x));
        }
        catch(Exception e){
            return 0;
        }
        }
    private double round(double x){
        return Math.round(x*100.0)/100.0;
    }
    private String severity(double x){
        return x>=85?"CRITICAL":x>=70?"HIGH":x>=45?"MEDIUM":"LOW";
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> list(Object x){
        return x instanceof List<?> l?(List<Map<String,Object>>)(List<?>)l:new ArrayList<>();
    }
    private String json(Object x){
        try{
            return mapper.writeValueAsString(x);
        }
    catch(Exception e){
        return "{}";
    }
    }
}
