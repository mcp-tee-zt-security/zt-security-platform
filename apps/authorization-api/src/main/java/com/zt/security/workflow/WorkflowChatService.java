package com.zt.security.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.action.*;
import com.zt.security.approval.*;
import com.zt.security.integration.*;
import com.zt.security.policy.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@ConditionalOnProperty(name="zt.workflow.chat.enabled",havingValue="true")
public class WorkflowChatService {
    private final GovernanceStore records;private final GovernedExecutionService execution;
    private final ActionEvaluationService evaluations;private final ApprovalRepository approvals;
    private final PolicyRepository policies;private final PolicyService policyService;
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final WorkflowAccess access;
    private final List<WorkflowPlanner> planners;
    private final ObjectProvider<com.zt.security.stitch.StitchIntegrationService> retrieval;
    @Value("${zt.workflow.chat.provider:ollama}") private String provider;
    public WorkflowChatService(GovernanceStore records,GovernedExecutionService execution,ActionEvaluationService evaluations,
        ApprovalRepository approvals,PolicyRepository policies,PolicyService policyService,JdbcTemplate jdbc,ObjectMapper mapper,WorkflowAccess access,ObjectProvider<WorkflowPlanner> planners,ObjectProvider<com.zt.security.stitch.StitchIntegrationService> retrieval){
        this.records=records;this.execution=execution;this.evaluations=evaluations;this.approvals=approvals;this.policies=policies;this.policyService=policyService;
        this.jdbc=jdbc;this.mapper=mapper;this.access=access;this.planners=planners.orderedStream().toList();this.retrieval=retrieval;
    }
    private String agent(UUID workspace){return "workflow-chat-"+workspace;}
    public WorkflowReply respond(String message,List<Map<String,String>> history){
        var matches=planners.stream().filter(p->p.provider().split("/")[0].equals(provider)).toList();
        if(matches.size()!=1)throw new IllegalStateException("Exactly one configured workflow model provider is required");
        return matches.get(0).respond(message,history);
    }
    @Transactional(readOnly=true) public List<Map<String,String>> history(UUID t,UUID w,Authentication auth){
        access.request(auth,t,w);records.scope(t,w);
        var rows=jdbc.queryForList("SELECT payload::text AS payload FROM governance_records WHERE tenant_id=? AND workspace_id=? AND kind='WORKFLOW_PROPOSAL' AND payload->>'requestedBy'=? ORDER BY created_at DESC LIMIT 8",t,w,auth.getName());
        Collections.reverse(rows);List<Map<String,String>> messages=new ArrayList<>();
        for(var row:rows)try{var p=mapper.readTree(String.valueOf(row.get("payload")));messages.add(Map.of("role","user","content",p.path("message").asText()));if(p.path("answer").isTextual())messages.add(Map.of("role","assistant","content",p.path("answer").asText()));}catch(java.io.IOException ex){throw new IllegalStateException("Stored chat is invalid",ex);}
        return messages;
    }
    @Transactional public Object saveReply(UUID t,UUID w,Authentication auth,String message,WorkflowReply reply){
        access.request(auth,t,w);
        var body=new LinkedHashMap<String,Object>();body.put("requestedBy",auth.getName());body.put("message",message);body.put("type",reply.type());body.put("answer",reply.answer());body.put("aiSubject",agent(w));body.put("provider",provider);body.put("expiresAt",Instant.now().plusSeconds(900).toString());
        if(reply.transfer()!=null){body.put("proposal",reply.transfer());body.put("simulation",true);}
        if("RETRIEVAL".equals(reply.type()))body.put("target",reply.target());
        return records.create(t,w,"WORKFLOW_PROPOSAL",body);
    }
    @Transactional public Map<String,Object> saveProposal(UUID tenant,UUID workspace,Authentication auth,String message,TransferProposal proposal){
        access.request(auth,tenant,workspace);
        return records.create(tenant,workspace,"WORKFLOW_PROPOSAL",Map.of("requestedBy",auth.getName(),"message",message,
            "proposal",proposal,"aiSubject",agent(workspace),"provider",provider,"simulation",true,"expiresAt",Instant.now().plusSeconds(900).toString()));
    }
    public List<String> demoPolicies(UUID workspace){
        String prefix="policy \"workflow_chat_",suffix="_"+workspace+"\" {\n";
        String common="  principal.type == \"AI_AGENT\"\n  action == \"simulation.transfer\"\n  resource.type == \"simulation_account\"\n  condition {\n    principal.id == \""+agent(workspace)+"\" and\n    context._workflowWorkspace == \""+workspace+"\" and\n    context.currency == \"EUR\"";
        return List.of(prefix+"allow"+suffix+"  effect allow\n"+common+"\n  }\n}",
            prefix+"step_up"+suffix+"  priority 10\n  effect step_up\n"+common+" and\n    context.amount >= 1000\n  }\n}");
    }
    @Transactional public Object config(UUID tenant,UUID workspace){
        records.scope(tenant,workspace);
        var rows=jdbc.queryForList("SELECT name,status,version FROM policies WHERE tenant_id=? AND workspace_id=? AND name IN (?,?) ORDER BY name,version DESC",tenant,workspace,"workflow_chat_allow_"+workspace,"workflow_chat_step_up_"+workspace);
        return Map.of("simulation",true,"provider",provider,"aiSubject",agent(workspace),"currency","EUR","approvalThreshold",1000,"policies",rows,"policyTemplates",demoPolicies(workspace));
    }
    @Transactional public Object setup(UUID tenant,UUID workspace,Authentication auth){
        access.human(auth,tenant,workspace);
        if(!access.has(auth,"ROLE_ADMIN","ROLE_PLATFORM"))throw new AccessDeniedException("Administrator role required to install demo policies");
        records.scope(tenant,workspace);
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",tenant+":"+workspace+":workflow-setup");
        jdbc.update("INSERT INTO identities(tenant_id,external_id,identity_type,name,attributes) VALUES(?,?,'AI_AGENT','Workflow Chat Agent','{}'::jsonb) ON CONFLICT(tenant_id,external_id) DO NOTHING",tenant,agent(workspace));
        jdbc.update("INSERT INTO agent_tools(tenant_id,name,description,risk_level,metadata) VALUES(?,'workflow.simulatedTransfer','Local simulated transfer','MEDIUM','{}'::jsonb) ON CONFLICT(tenant_id,name) DO NOTHING",tenant);
        // Never overwrite or reactivate existing policies: administrators retain control in Policy Studio.
        for(String dsl:demoPolicies(workspace)){
            PolicyDsl parsed=PolicyDsl.parse(dsl);
            if(!policies.findByTenantIdAndNameOrderByVersionDesc(tenant,parsed.name()).isEmpty())continue;
            Policy policy=new Policy();policy.setId(UUID.randomUUID());policy.setTenantId(tenant);policy.setWorkspaceId(workspace);
            policy.setName(parsed.name());policy.setVersion(1);policy.setPriority(parsed.priority());policy.setEffect(parsed.effect());policy.setPolicyText(dsl);policy.setStatus("DRAFT");
            var saved=policyService.save(tenant,policy);policyService.publish(tenant,saved.getId());
        }
        return config(tenant,workspace);
    }
    @Transactional public Object submit(UUID tenant,UUID workspace,UUID proposalId,Authentication auth){
        access.request(auth,tenant,workspace);
        var saved=records.get(tenant,workspace,"WORKFLOW_PROPOSAL",proposalId.toString(),true);
        if(!auth.getName().equals(saved.get("requestedBy")))throw new AccessDeniedException("Only the original requester can submit this proposal");
        if("CHAT".equals(saved.get("type")))throw new IllegalArgumentException("Conversation messages do not execute operations");
        if("RETRIEVAL".equals(saved.get("type")))throw new IllegalArgumentException("Use the retrieval endpoint for file proposals");
        if(saved.get("decisionId")!=null)return snapshot(tenant,workspace,saved);
        if(Instant.parse(String.valueOf(saved.get("expiresAt"))).isBefore(Instant.now()))throw new IllegalStateException("Proposal expired; send a new message");
        var proposal=mapper.convertValue(saved.get("proposal"),TransferProposal.class);
        var identities=jdbc.queryForList("SELECT id FROM identities WHERE tenant_id=? AND external_id=? AND identity_type='AI_AGENT' AND status='ACTIVE'",tenant,agent(workspace));
        if(identities.size()!=1)throw new IllegalStateException("An administrator must prepare the simulation first");
        UUID tool=jdbc.queryForObject("SELECT id FROM agent_tools WHERE tenant_id=? AND name='workflow.simulatedTransfer'",UUID.class,tenant);
        String task="workflow-chat-"+proposalId;
        UUID taskId=UUID.randomUUID();
        jdbc.update("INSERT INTO agent_tasks(id,tenant_id,agent_identity_id,external_task_id,purpose,expires_at,metadata) VALUES(?,?,?,?,?, ?,CAST(? AS jsonb))",taskId,tenant,identities.get(0).get("id"),task,"Simulated transfer requested by authenticated user",java.sql.Timestamp.from(Instant.now().plusSeconds(900)),json(Map.of("requestedBy",auth.getName(),"workspaceId",workspace.toString())));
        jdbc.update("INSERT INTO task_tools(task_id,tool_id) VALUES(?,?)",taskId,tool);
        var context=new LinkedHashMap<String,Object>();context.put("recipient",proposal.recipient());context.put("amount",proposal.amount());context.put("currency",proposal.currency());
        context.put("task_id",task);context.put("tool_id",tool.toString());context.put("requestedBy",auth.getName());
        context.put("_workflowTenant",tenant.toString());context.put("_workflowWorkspace",workspace.toString());context.put("simulation",true);
        context.put("_workflowProposal",proposalId.toString());
        var request=new EvaluateModels.EvaluateRequest(new EvaluateModels.Principal(agent(workspace),"AI_AGENT",Map.of()),new EvaluateModels.Action("simulation.transfer"),new EvaluateModels.Resource("simulation_account",proposal.recipient(),Map.of()),context);
        var result=evaluations.evaluate(tenant,workspace,request,false);
        var decision=records.decision(tenant,workspace,request,result);saved.put("decisionId",decision.get("id"));
        records.update(tenant,workspace,"WORKFLOW_PROPOSAL",saved);
        if(!"DENY".equals(result.decision())){
            Map<String,Object> body=new LinkedHashMap<>();body.put("policyDecision",Map.of("id",decision.get("id")));body.put("action","simulation.transfer");body.put("resource","simulation_account/"+proposal.recipient());body.put("evidenceIds",List.of());
            if("STEP_UP".equals(result.decision())){
                Approval approval=new Approval();approval.setId(UUID.randomUUID());approval.setTenantId(tenant);approval.setRequestId(result.requestId());approval.setApprovalType("WORKFLOW_EXECUTION");approval.setReason(result.reason());approval.setExpiresAt(Instant.now().plusSeconds(900));
                approval.setPayload(json(Map.of("requestedBy",auth.getName(),"aiSubject",agent(workspace),"workspaceId",workspace.toString(),"proposalId",proposalId.toString())));
                var created=approvals.saveAndFlush(approval);saved.put("approvalId",created.getId().toString());body.put("approval",Map.of("id",created.getId()));
            }
            var contract=execution.contract(tenant,workspace,body);saved.put("contractId",contract.get("id"));
        }
        records.update(tenant,workspace,"WORKFLOW_PROPOSAL",saved);
        return snapshot(tenant,workspace,saved);
    }
    @Transactional public Object readFile(UUID t,UUID w,UUID id,Authentication auth){
        access.request(auth,t,w);
        var saved=records.get(t,w,"WORKFLOW_PROPOSAL",id.toString(),true);
        if(!auth.getName().equals(saved.get("requestedBy"))||!"RETRIEVAL".equals(saved.get("type")))throw new AccessDeniedException("Only the requester can confirm this file proposal");
        if(Instant.parse(String.valueOf(saved.get("expiresAt"))).isBefore(Instant.now()))throw new IllegalStateException("Proposal expired; send a new request");
        var adapter=retrieval.getIfAvailable();
        if(adapter==null)return Map.of("decision","UNAVAILABLE","results",List.of(),"reason","Enable the registered Retrieval connector before reading files. Nothing was accessed.");
        // Content is never stored in chat history or fed back to the model. Each read reauthorizes.
        return adapter.workflowFile(t,w,String.valueOf(saved.get("target")),auth);
    }
    @Transactional(readOnly=true) public Object status(UUID tenant,UUID workspace,UUID proposalId,Authentication auth){
        access.human(auth,tenant,workspace);
        var saved=records.get(tenant,workspace,"WORKFLOW_PROPOSAL",proposalId.toString(),false);
        if(!auth.getName().equals(saved.get("requestedBy"))&&("CHAT".equals(saved.get("type"))||!access.has(auth,"ROLE_APPROVER","ROLE_EXECUTOR","ROLE_ADMIN","ROLE_PLATFORM","ROLE_AUDITOR")))throw new AccessDeniedException("Proposal unavailable");
        return snapshot(tenant,workspace,saved);
    }
    @Transactional(readOnly=true) public Object recent(UUID tenant,UUID workspace,Authentication auth){
        access.human(auth,tenant,workspace);records.scope(tenant,workspace);
        boolean reviewer=access.has(auth,"ROLE_APPROVER","ROLE_EXECUTOR","ROLE_ADMIN","ROLE_PLATFORM","ROLE_AUDITOR");
        var rows=jdbc.queryForList("SELECT payload::text AS payload FROM governance_records WHERE tenant_id=? AND workspace_id=? AND kind='WORKFLOW_PROPOSAL'"+(reviewer?" AND (COALESCE(payload->>'type','TRANSFER')<>'CHAT' OR payload->>'requestedBy'=?)":" AND payload->>'requestedBy'=?")+" ORDER BY created_at DESC LIMIT 30",tenant,workspace,auth.getName());
        List<Object> result=new ArrayList<>();for(var row:rows){try{var saved=mapper.readValue(String.valueOf(row.get("payload")),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});result.add(snapshot(tenant,workspace,saved));}catch(java.io.IOException e){throw new IllegalStateException("Stored proposal is invalid",e);}}
        return result;
    }
    private Object snapshot(UUID tenant,UUID workspace,Map<String,Object> proposal){
        var output=new LinkedHashMap<String,Object>();output.put("proposal",proposal);
        if(proposal.get("decisionId")!=null)output.put("decision",records.get(tenant,workspace,"DECISION",String.valueOf(proposal.get("decisionId")),false));
        if(proposal.get("approvalId")!=null){var approval=approvals.findByIdAndTenantId(UUID.fromString(String.valueOf(proposal.get("approvalId"))),tenant).orElseThrow();output.put("approval",Map.of("id",approval.getId(),"status",approval.getExpiresAt().isBefore(Instant.now())&&"PENDING".equals(approval.getStatus())?"EXPIRED":approval.getStatus(),"decidedBy",Optional.ofNullable(approval.getDecidedBy()).orElse("")));}
        if(proposal.get("contractId")!=null){var contract=records.get(tenant,workspace,"CONTRACT",String.valueOf(proposal.get("contractId")),false);output.put("contract",contract);if(contract.get("executionId")!=null){output.put("execution",records.get(tenant,workspace,"EXECUTION",String.valueOf(contract.get("executionId")),false));output.put("verifications",records.workflowVerifications(tenant,workspace,String.valueOf(contract.get("executionId"))));}}
        return output;
    }
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Invalid workflow data",e);}}
}
