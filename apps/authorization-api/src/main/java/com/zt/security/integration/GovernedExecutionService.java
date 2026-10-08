package com.zt.security.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zt.security.action.ActionEvaluationService;
import com.zt.security.action.EvaluateModels;
import com.zt.security.approval.ApprovalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class GovernedExecutionService {
    private final GovernanceStore records;
    private final ApprovalRepository approvals;
    private final ActionEvaluationService evaluations;
    private final ObjectMapper mapper;
    private final List<GovernedActionExecutor> executors;
    public GovernedExecutionService(GovernanceStore records,ApprovalRepository approvals,
            ActionEvaluationService evaluations,ObjectMapper mapper,org.springframework.beans.factory.ObjectProvider<GovernedActionExecutor> executors) {
        this.records=records;this.approvals=approvals;this.evaluations=evaluations;this.mapper=mapper;this.executors=executors.orderedStream().toList();
    }

    public Map<String,Object> evidence(UUID tenant,UUID workspace,Map<String,Object> body) {
        checkTenant(tenant,body);
        Map<String,Object> record=new LinkedHashMap<>();
        record.put("type",body.getOrDefault("type","SECURITY_EVIDENCE"));
        record.put("subject",body.getOrDefault("subject","unknown"));
        record.put("traceId",body.getOrDefault("traceId",UUID.randomUUID().toString()));
        record.put("payload",body);record.put("digest",digest(body));
        var value=records.create(tenant,workspace,"EVIDENCE",record);
        event(tenant,workspace,"EVIDENCE_CREATED",value);
        return value;
    }

    public Map<String,Object> verifyEvidence(UUID tenant,UUID workspace,String id) {
        var record=records.get(tenant,workspace,"EVIDENCE",id,false);
        boolean verified=Objects.equals(record.get("digest"),digest(record.get("payload")));
        return Map.of("id",id,"verified",verified,"verificationType","STORED_PAYLOAD_SHA256");
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> contract(UUID tenant,UUID workspace,Map<String,Object> body) {
        checkTenant(tenant,body);
        if(!(body.get("policyDecision") instanceof Map<?,?> supplied))throw new IllegalArgumentException("policyDecision is required");
        var decision=records.get(tenant,workspace,"DECISION",String.valueOf(supplied.get("id")),false);
        var request=mapper.convertValue(decision.get("request"),EvaluateModels.EvaluateRequest.class);
        if("DENY".equals(decision.get("decision")))throw new AccessDeniedException("Cannot create a contract from a denied decision");
        if(Instant.parse(String.valueOf(decision.get("decidedAt"))).plusSeconds(900).isBefore(Instant.now()))
            throw new IllegalStateException("Decision expired; evaluate again");
        if(!Objects.equals(body.get("action"),request.action().name())||
           !Objects.equals(body.get("resource"),request.resource().type()+"/"+request.resource().id()))
            throw new IllegalArgumentException("Contract action/resource must match the stored decision");
        List<String> evidenceIds=new ArrayList<>();
        if(body.get("evidenceIds") instanceof List<?> ids)for(Object id:ids){
            var verified=verifyEvidence(tenant,workspace,String.valueOf(id));
            if(!Boolean.TRUE.equals(verified.get("verified")))throw new AccessDeniedException("Evidence integrity check failed");
            evidenceIds.add(String.valueOf(id));
        }
        String approvalId=null;
        if(body.get("approval") instanceof Map<?,?> approval&&approval.get("id")!=null){
            approvalId=String.valueOf(approval.get("id"));
            approval(tenant,decision,approvalId,false);
        }
        Map<String,Object> value=new LinkedHashMap<>();
        value.put("action",request.action().name());value.put("resource",body.get("resource"));
        value.put("subject",request.principal().id());
        value.put("decisionId",decision.get("id"));value.put("evidenceIds",evidenceIds);
        value.put("approvalId",approvalId);value.put("request",request);value.put("traceId",decision.get("traceId"));
        value.put("status","STEP_UP".equals(decision.get("decision"))?"PENDING_APPROVAL":"READY");
        value.put("expiresAt",Instant.now().plusSeconds(900).toString());
        var result=records.create(tenant,workspace,"CONTRACT",value);
        event(tenant,workspace,"EXECUTION_CONTRACT_CREATED",result);
        return result;
    }

    public Map<String,Object> execute(UUID tenant,UUID workspace,String id) {
        var contract=records.get(tenant,workspace,"CONTRACT",id,true);
        if(contract.get("executionId")!=null)return records.get(tenant,workspace,"EXECUTION",String.valueOf(contract.get("executionId")),false);
        if(Instant.parse(String.valueOf(contract.get("expiresAt"))).isBefore(Instant.now()))throw new IllegalStateException("Execution contract expired");
        var decision=records.get(tenant,workspace,"DECISION",String.valueOf(contract.get("decisionId")),false);
        if("STEP_UP".equals(decision.get("decision")))approval(tenant,decision,String.valueOf(contract.get("approvalId")),true);
        var request=mapper.convertValue(contract.get("request"),EvaluateModels.EvaluateRequest.class);
        // Recheck the authoritative pipeline immediately before external execution.
        var current=evaluations.evaluate(tenant,request);
        records.decision(tenant,workspace,request,current);
        if("DENY".equals(current.decision()))throw new AccessDeniedException(current.reason());
        if("STEP_UP".equals(current.decision())){
            if(!"STEP_UP".equals(decision.get("decision")))throw new AccessDeniedException("Risk now requires approval; evaluate again and obtain a new approval");
            approval(tenant,decision,String.valueOf(contract.get("approvalId")),true);
        }
        for(Object evidenceId:(List<?>)contract.get("evidenceIds")){
            if(!Boolean.TRUE.equals(verifyEvidence(tenant,workspace,String.valueOf(evidenceId)).get("verified")))
                throw new AccessDeniedException("Evidence integrity check failed");
        }
        var candidates=executors.stream().filter(executor->executor.action().equals(request.action().name())).toList();
        if(candidates.size()>1)throw new IllegalStateException("Multiple execution connectors configured for action");
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("contractId",id);result.put("traceId",contract.get("traceId"));
        result.put("subject",request.principal().id());result.put("evidenceIds",contract.get("evidenceIds"));
        result.put("startedAt",Instant.now().toString());
        if(candidates.isEmpty()){
            result.put("status","UNSUPPORTED");result.put("result",Map.of("executed",false,"reason","No external action connector installed"));
        }else{
            // Connector implementations must use contractId for external idempotency.
            try{
                var output=candidates.get(0).execute(id,request);
                result.put("result",output);result.put("status","SUCCEEDED");
            }catch(Exception failure){
                result.put("status","FAILED");result.put("result",Map.of("executed",false,"reason","Execution connector failed"));
            }
        }
        result.put("completedAt",Instant.now().toString());
        var execution=records.create(tenant,workspace,"EXECUTION",result);
        contract.put("executionId",execution.get("id"));contract.put("status",execution.get("status"));
        records.update(tenant,workspace,"CONTRACT",contract);
        event(tenant,workspace,"EXECUTION_"+execution.get("status"),execution);
        return execution;
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> verify(UUID tenant,UUID workspace,String id) {
        var execution=records.get(tenant,workspace,"EXECUTION",id,false);
        var contract=records.get(tenant,workspace,"CONTRACT",String.valueOf(execution.get("contractId")),false);
        boolean verified=false;
        if("SUCCEEDED".equals(execution.get("status"))){
            var connector=executors.stream().filter(e->e.action().equals(contract.get("action"))).findFirst();
            if(connector.isPresent()){
                try{verified=connector.get().verify((Map<String,Object>)execution.get("result"));}catch(Exception ignored){verified=false;}
            }
        }
        var result=records.create(tenant,workspace,"VERIFICATION",Map.of("executionId",id,"verified",verified,
            "traceId",execution.get("traceId"),"reason",verified?"Execution verified by connector":"No verified external execution"));
        event(tenant,workspace,"EXECUTION_VERIFIED",result);
        return result;
    }

    private void approval(UUID tenant,Map<String,Object> decision,String id,boolean requireApproved) {
        if(id==null||id.equals("null"))throw new IllegalStateException("Approval is required before execution");
        var value=approvals.findByIdAndTenantId(GovernanceStore.uuid(id),tenant)
            .orElseThrow(()->new AccessDeniedException("Approval not found"));
        if(!Objects.equals(value.getRequestId(),GovernanceStore.uuid(decision.get("requestId"))))throw new AccessDeniedException("Approval belongs to another decision");
        if(value.getExpiresAt()!=null&&value.getExpiresAt().isBefore(Instant.now()))throw new AccessDeniedException("Approval expired");
        if("REJECTED".equals(value.getStatus())||"EXPIRED".equals(value.getStatus())||
            (requireApproved&&!"APPROVED".equals(value.getStatus())))throw new AccessDeniedException("Approval is not approved");
    }
    public void event(UUID tenant,UUID workspace,String type,Map<String,Object> value) {
        records.create(tenant,workspace,"EVENT",Map.of("eventType",type,
            "traceId",String.valueOf(value.getOrDefault("traceId",value.get("id"))),"payload",value));
    }
    public void checkTenant(UUID tenant,Map<String,Object> body) {
        if(body.get("tenantId")!=null&&!tenant.toString().equalsIgnoreCase(String.valueOf(body.get("tenantId"))))throw new AccessDeniedException("tenant mismatch");
    }
    private String digest(Object value) {
        try{
            var canonical=mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        }catch(Exception e){throw new IllegalArgumentException("Cannot hash evidence",e);}
    }
}
