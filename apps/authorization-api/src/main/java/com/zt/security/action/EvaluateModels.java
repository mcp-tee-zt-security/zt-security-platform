package com.zt.security.action;
import jakarta.validation.constraints.*;
import java.util.*;
public final class EvaluateModels {
    public record Principal(@NotBlank String id,
    @NotBlank String type,Map<String,Object> attributes){
    }
    public record Action(@NotBlank String name){
    }
    public record Resource(@NotBlank String type,@NotBlank String id,Map<String,
Object> attributes){
}
public record EvaluateRequest(@NotNull Principal principal,
@NotNull Action action,@NotNull Resource resource,Map<String,Object> context){
}
public record MatchedPolicy(UUID id,String name,int version,String effect){
}
public record EvaluateResponse(UUID requestId,String decision,String reason,
List<MatchedPolicy> matchedPolicies,Risk risk,Audit audit,long latencyMs){
}
public record Risk(double score,String level){
}
public record Audit(UUID eventId){
}
}
