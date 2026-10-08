package com.zt.security.replay;
import com.fasterxml.jackson.databind.*;
import com.zt.security.action.EvaluateModels;
import com.zt.security.audit.*;
import com.zt.security.common.TenantSession;
import com.zt.security.policy.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service public class ReplayService {
    final AuditRepository audits;
    final PolicyService policies;
    final PolicyEvaluator evaluator;
    final ObjectMapper mapper;
    final ActionReplayRepository replays;
    final TenantSession session;
    ReplayService(AuditRepository a,PolicyService p,
    PolicyEvaluator e,ObjectMapper m,ActionReplayRepository r,TenantSession s){
        audits=a;
        policies=p;
        evaluator=e;
        mapper=m;
        replays=r;
        session=s;
        }
 @Transactional public List<ActionReplay> replay(UUID tenant,int limit){
     session.set(tenant);
     List<AuditLog> src=audits.findTop100ByTenantIdOrderByCreatedAtDesc(tenant).
     stream().limit(Math.max(1,
     Math.min(100,limit))).toList();
     List<ActionReplay> out=new ArrayList<>();
     for(AuditLog a:src){
         try{
             JsonNode m=mapper.readTree(a.getMetadata());
             var p=m.get("principal");
             var c=m.get("context");
             var principal=new EvaluateModels.Principal(p.get("id").asText(),
             p.get("type").asText(),Map.of());
             var req=new EvaluateModels.EvaluateRequest(principal,
             new EvaluateModels.Action(a.getAction()),new EvaluateModels.Resource(a.getResourceType(),
             a.getResourceId(),Map.of()),mapper.convertValue(c==null?mapper.createObjectNode():c,
             Map.class));
             var d=evaluator.evaluate(policies.active(tenant),req);
             ActionReplay x=new ActionReplay();
             x.setTenantId(tenant);
             x.setSourceAuditId(a.getId());
             x.setPolicySet("ACTIVE");
             x.setDecision(d.decision());
             x.setReason(d.reason());
             out.add(replays.save(x));
         }
         catch(Exception ignored){
         }
         }
         return out;
         }
}
