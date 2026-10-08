package com.zt.security.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.action.EvaluateModels;
import com.zt.security.identity.IdentityRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import java.util.*;

/** Both SDK and dashboard requests enter the existing authorization pipeline. */
@Component
public class SdkActionAdapter {
    private final ObjectMapper mapper;
    private final IdentityRepository identities;
    private final Validator validator;
    public SdkActionAdapter(ObjectMapper mapper, IdentityRepository identities, Validator validator) {
        this.mapper=mapper; this.identities=identities; this.validator=validator;
    }
    @SuppressWarnings("unchecked")
    public EvaluateModels.EvaluateRequest request(UUID tenant, Map<String,Object> body) {
        if(body.get("tenantId")!=null&&!tenant.toString().equalsIgnoreCase(String.valueOf(body.get("tenantId"))))
            throw new org.springframework.security.access.AccessDeniedException("tenant mismatch");
        EvaluateModels.EvaluateRequest request;
        if(body.containsKey("principal"))request=mapper.convertValue(body,EvaluateModels.EvaluateRequest.class);
        else {
            String subject=required(body,"subject");
            var identity=identities.findByTenantIdAndExternalId(tenant,subject)
                .orElseThrow(()->new IllegalArgumentException("Register the SDK subject as an identity before evaluation"));
            if(!"ACTIVE".equals(identity.getStatus()))throw new org.springframework.security.access.AccessDeniedException("identity is not active");
            String resource=required(body,"resource");
            int slash=resource.indexOf('/');
            if(slash<=0||slash==resource.length()-1)throw new IllegalArgumentException("SDK resource must use type/id, e.g. bank_account/ACC-1001");
            Map<String,Object> attributes=body.get("attributes") instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
            if("AI_AGENT".equals(identity.getIdentityType())&&(!attributes.containsKey("task_id")||!attributes.containsKey("tool_id")))
                throw new IllegalArgumentException("AI-agent SDK requests require attributes.task_id and attributes.tool_id");
            Map<String,Object> principalAttributes;
            try{principalAttributes=mapper.readValue(identity.getAttributes(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}
            catch(Exception e){throw new IllegalStateException("Identity attributes are invalid");}
            request=new EvaluateModels.EvaluateRequest(
                new EvaluateModels.Principal(subject,identity.getIdentityType(),principalAttributes),
                new EvaluateModels.Action(required(body,"action")),
                new EvaluateModels.Resource(resource.substring(0,slash),resource.substring(slash+1),Map.of()),attributes);
        }
        if(request.principal()==null||request.action()==null||request.resource()==null
            ||!validator.validate(request).isEmpty()||!validator.validate(request.principal()).isEmpty()
            ||!validator.validate(request.action()).isEmpty()||!validator.validate(request.resource()).isEmpty())
            throw new IllegalArgumentException("principal, action and resource require valid identifiers and types");
        return request;
    }
    public static String required(Map<String,Object> body,String key) {
        Object value=body.get(key);
        if(!(value instanceof String text)||text.isBlank())throw new IllegalArgumentException(key+" is required");
        return text;
    }
}
