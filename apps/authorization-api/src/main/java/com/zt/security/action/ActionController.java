package com.zt.security.action;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/actions") public class ActionController {
    final ActionEvaluationService service;
    final IdempotencyService idem;
    final ObjectMapper mapper;
    ActionController(ActionEvaluationService s,IdempotencyService i,ObjectMapper m){
        service=s;
        idem=i;
        mapper=m;
        }
 @PostMapping("/evaluate") EvaluateModels.EvaluateResponse evaluate(@RequestHeader("X-Tenant-Id") UUID tenant,
 @RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody EvaluateModels.
 EvaluateRequest req){
   var old=idem.existing(tenant,key,req);
   if(old.isPresent()) try{
       return mapper.readValue(old.get(),
       EvaluateModels.EvaluateResponse.class);
       }
       catch(Exception e){
           throw new IllegalStateException(e);
   }
   var out=service.evaluate(tenant,req);
   idem.complete(tenant,key,out);
   return out;
 }
}
