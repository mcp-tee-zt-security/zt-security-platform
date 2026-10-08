package com.zt.security.risk;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class RiskEngine {
    public record Evidence(String category,String signal,double weight,double score,String explanation) {
    }
    public record Result(double score,String level,List<Evidence> evidence){
    }

    @Cacheable(cacheNames="risk-score", key="#tenantId.toString() + ':' + #fingerprint")
    public Result score(UUID tenantId, String fingerprint, Map<String,Object> c){
        double s=0;
        List<Evidence> e=new ArrayList<>();
        Object a=c==null?null:c.get("amount");
        if(a instanceof Number n){
            double v=n.doubleValue();
            if(v>=10000000){
s+=35;
e.add(new Evidence("TRANSACTION","HIGH_AMOUNT",35,35,"Transaction amount is at least 10,000,000"));
            }
            else if(v>=1000000){
                s+=15;
                e.add(new Evidence("TRANSACTION","ELEVATED_AMOUNT",
            15,15,"Transaction amount is at least 1,000,000"));
            }
            }
        if(Boolean.TRUE.equals(c==null?null:c.get("new_beneficiary"))){
            s+=25;
            e.add(new Evidence("IDENTITY","NEW_BENEFICIARY",25,25,"Beneficiary is new"));
        }
        if(!Boolean.TRUE.equals(c==null?null:c.get("device_trusted"))){
            s+=20;
            e.add(new Evidence("DEVICE","UNTRUSTED_DEVICE",20,20,"Device is not trusted"));
        }
        if(c!=null&&String.valueOf(c.getOrDefault("ip","" )).startsWith("10.")){
            s-=5;
            e.add(new Evidence("NETWORK","TRUSTED_PRIVATE_RANGE",-5,0,"Private network range reduces risk"));
        }
        s=Math.max(0,Math.min(100,s));
        return new Result(round(s),s>=70?"HIGH":s>=40?"MEDIUM":"LOW",List.copyOf(e));
    }
    public Result score(Map<String,Object> c){
        return score(UUID.nameUUIDFromBytes("local-risk".getBytes()),
        fingerprint(c),c);
        }
    public String fingerprint(Map<String,Object> c){
        return Integer.toHexString(Objects.hashCode(c==
        null?Map.of():new TreeMap<>(c)));
    }
    private double round(double x){
        return Math.round(x*100.0)/100.0;
    }
}
