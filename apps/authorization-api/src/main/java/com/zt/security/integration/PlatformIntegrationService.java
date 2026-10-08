package com.zt.security.integration;

import com.zt.security.agent.*;
import com.zt.security.identity.*;
import com.zt.security.policy.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class PlatformIntegrationService {
    private final GovernanceStore records;
    private final GovernedExecutionService governance;
    private final IdentityRepository identities;
    private final AgentTaskRepository tasks;
    private final AgentToolRepository tools;
    private final TaskToolRepository taskTools;
    private final SdkActionAdapter adapter;
    private final PolicyService policies;
    private final PolicyEvaluator evaluator;
    public PlatformIntegrationService(GovernanceStore records,GovernedExecutionService governance,
            IdentityRepository identities,AgentTaskRepository tasks,AgentToolRepository tools,
            TaskToolRepository taskTools,SdkActionAdapter adapter,PolicyService policies,PolicyEvaluator evaluator) {
        this.records=records;this.governance=governance;this.identities=identities;this.tasks=tasks;
        this.tools=tools;this.taskTools=taskTools;this.adapter=adapter;this.policies=policies;this.evaluator=evaluator;
    }
    public Map<String,Object> identity(UUID tenant,UUID workspace,Map<String,Object> body) {
        records.scope(tenant,workspace);governance.checkTenant(tenant,body);
        String subject=SdkActionAdapter.required(body,"subject");
        var value=identities.findByTenantIdAndExternalId(tenant,subject);
        if(value.isEmpty())return Map.of("resolved",false,"subject",subject,"reason","Identity not registered");
        var identity=value.get();
        return Map.of("resolved","ACTIVE".equals(identity.getStatus()),"subject",subject,
            "id",identity.getId(),"identityType",identity.getIdentityType(),"status",identity.getStatus());
    }
    public Identity registerAgent(UUID tenant,UUID workspace,Map<String,Object> body) {
        records.scope(tenant,workspace);governance.checkTenant(tenant,body);
        String external=String.valueOf(body.getOrDefault("externalId",body.getOrDefault("subject",body.getOrDefault("name",""))));
        if(external.isBlank())throw new IllegalArgumentException("externalId, subject or name required");
        if(identities.findByTenantIdAndExternalId(tenant,external).isPresent())throw new IllegalStateException("Identity already registered");
        Identity identity=new Identity();identity.setId(UUID.randomUUID());identity.setTenantId(tenant);
        identity.setExternalId(external);identity.setName(String.valueOf(body.getOrDefault("name",external)));
        identity.setIdentityType("AI_AGENT");identity.setAttributes(records.json(body.getOrDefault("attributes",Map.of())));
        identity=identities.save(identity);
        governance.event(tenant,workspace,"AGENT_REGISTERED",Map.of("id",identity.getId(),"subject",external));
        return identity;
    }
    public Map<String,Object> mission(UUID tenant,UUID workspace,String agentId,Map<String,Object> body) {
        records.scope(tenant,workspace);governance.checkTenant(tenant,body);
        var identity=identities.findByTenantId(tenant).stream().filter(value->
            value.getId().toString().equals(agentId)||value.getExternalId().equals(agentId)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Agent not found"));
        if(!"ACTIVE".equals(identity.getStatus())||!"AI_AGENT".equals(identity.getIdentityType()))throw new AccessDeniedException("Active AI agent required");
        AgentTask task=new AgentTask();task.setTenantId(tenant);task.setAgentIdentityId(identity.getId());
        task.setExternalTaskId(String.valueOf(body.getOrDefault("externalTaskId","mission-"+UUID.randomUUID())));
        task.setPurpose(String.valueOf(body.getOrDefault("objective",body.getOrDefault("purpose","SDK mission"))));
        task.setExpiresAt(Instant.now().plusSeconds(3600));
        task=tasks.saveAndFlush(task);
        List<?> delegated=body.get("toolIds") instanceof List<?> ids?ids:tools.findByTenantId(tenant).stream()
            .filter(tool->tool.isEnabled()&&tool.getName().equals(body.get("objective"))).map(AgentTool::getId).toList();
        if(delegated.isEmpty())throw new IllegalArgumentException("Mission requires enabled toolIds or an objective matching an enabled tool name");
        for(Object id:delegated){
            var tool=tools.findById(GovernanceStore.uuid(id)).orElseThrow(()->new IllegalArgumentException("Tool not found"));
            if(!tenant.equals(tool.getTenantId())||!tool.isEnabled())throw new AccessDeniedException("Tool unavailable for tenant");
            taskTools.attach(task.getId(),tool.getId());
        }
        Map<String,Object> result=Map.of("id",task.getId(),"externalTaskId",task.getExternalTaskId(),"agentIdentityId",task.getAgentIdentityId(),
            "status",task.getStatus(),"expiresAt",task.getExpiresAt(),"toolIds",taskTools.toolIds(task.getId()),"subject",identity.getExternalId());
        governance.event(tenant,workspace,"AGENT_MISSION_CREATED",result);
        return result;
    }
    public Map<String,Object> register(UUID tenant,UUID workspace,String kind,Map<String,Object> body,String status) {
        governance.checkTenant(tenant,body);
        Map<String,Object> value=new LinkedHashMap<>();value.put("spec",body);value.put("status",status);
        var result=records.create(tenant,workspace,kind,value);
        governance.event(tenant,workspace,kind+"_REGISTERED",result);
        return result;
    }
    public Map<String,Object> trust(UUID tenant,UUID workspace,Map<String,Object> body) {
        String organization=SdkActionAdapter.required(body,"organizationId");
        records.get(tenant,workspace,"ORGANIZATION",organization,false);
        var result=register(tenant,workspace,"TRUST",body,"PENDING_VERIFICATION");
        result.put("trusted",false);result.put("reason","Registration does not establish cryptographic trust");
        records.update(tenant,workspace,"TRUST",result);
        return result;
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> simulate(UUID tenant,UUID workspace,String id,Map<String,Object> body) {
        var simulation=records.get(tenant,workspace,"SIMULATION",id,false);
        Map<String,Object> spec=(Map<String,Object>)simulation.get("spec");
        Object raw=body.getOrDefault("request",spec.get("request"));
        if(!(raw instanceof Map<?,?>))throw new IllegalArgumentException("Simulation requires a request");
        records.scope(tenant,workspace);
        var request=adapter.request(tenant,(Map<String,Object>)raw);
        var result=evaluator.evaluate(policies.forEvaluation(tenant,UUID.randomUUID()),request);
        return records.create(tenant,workspace,"SIMULATION_RESULT",Map.of("simulationId",id,
            "simulationType","POLICY_ONLY","decision",result.decision(),"reason",result.reason(),"matchedPolicies",result.matched()));
    }
}
