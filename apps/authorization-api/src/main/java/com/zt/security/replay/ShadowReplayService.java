package com.zt.security.replay;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.action.EvaluateModels;
import com.zt.security.policy.*;
import com.zt.security.runtime.AgentRuntimeEvent;
import com.zt.security.runtime.AgentRuntimeEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShadowReplayService {
 @Value("${zt.security.risk-scoring.replay-default-score:0.20}") private double defaultReplayScore;
 private final AgentRuntimeEventRepository events;
 private final PolicyService policies;
 private final PolicyEvaluator evaluator;
 private final ObjectMapper mapper;
 public ShadowReplayService(AgentRuntimeEventRepository e,PolicyService p,
 PolicyEvaluator pe,ObjectMapper om){
     events=e;
     policies=p;
     evaluator=pe;
     mapper=om;
 }
 @Transactional(readOnly=true)
 public ShadowReplayModels.ReplayResponse replay(UUID tenant, ShadowReplayModels.ReplayRequest req){
   if(req==null||req.policyText()==null||req.policyText().isBlank()) throw new
IllegalArgumentException("policyText is required");
   int max=Math.min(Math.max(req.maxEvents()==null?1000:req.maxEvents(),
   1),10000);
   Instant to=req.to()==null?Instant.now():req.to();
   Instant from=req.from()==
   null?to.minusSeconds(86400):req.from();
   if(from.isAfter(to)) throw new IllegalArgumentException("from must be before to");
   PolicyDsl proposed=PolicyDsl.parse(req.policyText());
   String policyHash=sha256(req.policyText());
   List<AgentRuntimeEvent> source=events.findTop10000ByTenantIdOrderByCreatedAtDesc(tenant).stream().
   filter(e->e.getCreatedAt()!=null&&!e.getCreatedAt().isBefore(from)&&!e.getCreatedAt().isAfter(to)).filter(e->
   req.workspaceId()==null||Objects.equals(req.workspaceId(),
   e.getWorkspaceId())).limit(max).toList();
   List<Policy> current=policies.active(tenant);
   List<Policy> shadow=new ArrayList<>(current);
   String proposedName=proposed.name();
   shadow.removeIf(p->Objects.equals(p.getName(),proposedName));
Policy synthetic=new Policy();
synthetic.setId(UUID.nameUUIDFromBytes((tenant+":"+policyHash).getBytes(StandardCharsets.
   UTF_8)));
   synthetic.setTenantId(tenant);
   synthetic.setName(proposedName);
   synthetic.setVersion(proposed.priority());
   synthetic.setPriority(proposed.priority());
   synthetic.setEffect(proposed.effect().toUpperCase(Locale.ROOT));
   synthetic.setPolicyText(req.policyText());
   synthetic.setStatus("ACTIVE");
   shadow.add(synthetic);
   shadow.sort(Comparator.comparingInt(Policy::getPriority));
   List<ShadowReplayModels.ReplayRow> rows=new ArrayList<>();
   int changed=0,newDeny=0,newStep=0,fp=0;
   double impact=0;
   for(AgentRuntimeEvent e:source){
     String baseline=normalize(e.getDecision());
     EvaluateModels.EvaluateRequest er=toRequest(e);
     PolicyEvaluator.Result sr=evaluator.evaluate(shadow,er);
     String after=sr.decision();
     boolean ch=!Objects.equals(baseline,after);
     boolean candidate="DENY".equals(after)&&
     "ALLOW".equals(baseline)&&Optional.ofNullable(e.getRiskScore()).orElse(50d)<40;
     double bi=businessImpact(e.getAction(),e.getResourceType());
     if(ch){
         changed++;
         impact+=bi;
         }
         if("DENY".equals(after)&&!"DENY".equals(baseline))newDeny++;
     if("STEP_UP".equals(after)&&!"STEP_UP".equals(baseline))newStep++;
     if(candidate)fp++;
     rows.add(new ShadowReplayModels.ReplayRow(e.getId(),e.getAction(),
     e.getResourceType(),e.getResourceId(),baseline,after,ch,candidate,bi,e.getAgentExternalId(),
     e.getCreatedAt()));
   }
   double baselineRisk=source.stream().mapToDouble(e->Optional.ofNullable(e.getRiskScore()).orElse(50d)).
   average().orElse(0);
   double blockedRisk=rows.stream().filter(r->r.changed()&&("DENY".equals(r.shadow())||
   "STEP_UP".equals(r.shadow()))).mapToDouble(r->source.stream().filter(e->e.getId().equals(r.eventId())).
   findFirst().map(e->Optional.ofNullable(e.getRiskScore()).orElse(0d)).orElse(0d)).sum();
   double securityImprovement=baselineRisk<=0?0:Math.min(100,blockedRisk/Math.max(1,
   source.size())/baselineRisk*100);
   String resultHash=sha256(mapper.valueToTree(rows).toString());
   UUID job=UUID.randomUUID();
   return new ShadowReplayModels.ReplayResponse(job,"SHADOW",policyHash,
   resultHash,source.size(),changed,newDeny,newStep,fp,round(securityImprovement),
   round(source.isEmpty()?0:impact/source.size()),rows,"COMPLETED");
 }
 private EvaluateModels.EvaluateRequest toRequest(AgentRuntimeEvent e){
     Map<String,Object> ctx=new LinkedHashMap<>();
     try{
         if(e.getContext()!=null&&
         !e.getContext().isBlank())ctx.putAll(mapper.readValue(e.getContext(),
         new TypeReference<Map<String,Object>>(){
         }
         ));
         }
         catch(Exception ignored){
     }
     if(e.getRiskScore()!=null)ctx.put("risk.score",e.getRiskScore());
     ctx.put("replay",
 true);
 return new EvaluateModels.EvaluateRequest(new EvaluateModels.Principal(e.getAgentExternalId(),
 "AI_AGENT",Map.of()),new EvaluateModels.Action(e.getAction()),new EvaluateModels.Resource(e.getResourceType(),
 e.getResourceId(),Map.of()),ctx);
 }
 private double businessImpact(String action,String resource){
     String a=String.valueOf(action).toLowerCase(Locale.ROOT);
     String r=String.valueOf(resource).toLowerCase(Locale.ROOT);
     double v = defaultReplayScore;
     if(a.contains("payment")||a.contains("transfer"))v=.9;
     else if(a.contains("refund"))v=.7;
     else if(a.contains("export")||r.contains("database"))v=.8;
     return v;
     }
 private String normalize(String s){
     return s==null?"DENY":s.toUpperCase(Locale.ROOT);
 }
 private double round(double x){
     return Math.round(x*100.0)/100.0;
 }
 private String sha256(String s){
     try{
         byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.
         UTF_8));
         StringBuilder z=new StringBuilder();
         for(byte x:b)z.append(String.format("%02x",
         x));
         return z.toString();
         }
         catch(Exception e){
             throw new IllegalStateException(e);
     }
     }
}
