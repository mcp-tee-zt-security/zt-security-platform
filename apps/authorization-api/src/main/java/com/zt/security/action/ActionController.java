package com.zt.security.action;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.idempotency.IdempotencyService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import com.zt.security.integration.GovernanceStore;
import com.zt.security.integration.SdkActionAdapter;
import org.springframework.transaction.annotation.Transactional;
@RestController @RequestMapping("/v1/actions") public class ActionController {
    final ActionEvaluationService service;
    final IdempotencyService idem;
    final ObjectMapper mapper;
    final GovernanceStore records;
    final SdkActionAdapter adapter;
    ActionController(ActionEvaluationService s,IdempotencyService i,ObjectMapper m,GovernanceStore records,SdkActionAdapter adapter){
        service=s;
        idem=i;
        mapper=m;
        this.records=records;
        this.adapter=adapter;
        }
 @PostMapping("/evaluate") @Transactional Object evaluate(@RequestHeader("X-Tenant-Id") UUID tenant,
 @RequestHeader(value="X-Workspace-Id",required=false) UUID workspace,
 @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body){
   records.scope(tenant,workspace);
   var req=adapter.request(tenant,body);
   String scopedKey=key==null||key.isBlank()?null:(workspace==null?"tenant":workspace.toString())+":"+key;
   var old=idem.existing(tenant,scopedKey,req);
   EvaluateModels.EvaluateResponse out;
   if(old.isPresent()) {
     try{out=mapper.readValue(old.get(),EvaluateModels.EvaluateResponse.class);}
     catch(Exception e){throw new IllegalStateException(e);}
   }else{
     out=service.evaluate(tenant,req);
     idem.complete(tenant,scopedKey,out);
   }
   var record=records.decision(tenant,workspace,req,out);
   if(body.containsKey("principal"))return out;
   Map<String,Object> result=new LinkedHashMap<>(record);
   result.remove("request");
   return result;
 }
}
