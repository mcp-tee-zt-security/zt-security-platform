package com.zt.security.agent;
import com.zt.security.policy.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/agents") public class AgentGraphController {
    final PolicyService policies;
    AgentGraphController(PolicyService p){
        policies=p;
    }
 @GetMapping("/{agentId}/permission-graph") Map<String,Object> graph(@RequestHeader("X-Tenant-Id") UUID tenant,
 @PathVariable String agentId){
     List<Map<String,Object>> nodes=new ArrayList<>(),
     edges=new ArrayList<>();
     nodes.add(Map.of("id",agentId,"type","AI_AGENT"));
     for(Policy p:policies.active(tenant)){
         PolicyDsl d=PolicyDsl.parse(p.getPolicyText());
         if(!"AI_AGENT".equals(d.principalType())||d.action()==null)continue;
         String action=d.action();
         nodes.add(Map.of("id",action,"type","ACTION","effect",p.getEffect()));
         edges.add(Map.of("from",agentId,"to",action,"relation",p.getEffect().equals("deny")?"DENIED":"CAN_REQUEST",
         "policy",p.getName()));
         if(d.resourceType()!=null){
             nodes.add(Map.of("id",
             d.resourceType(),"type","RESOURCE_TYPE"));
             edges.add(Map.of("from",action,
             "to",d.resourceType(),"relation","TARGETS"));
             }
             }
             return Map.of("tenantId",
     tenant,"agentId",agentId,"nodes",nodes,"edges",edges);
     }
}
