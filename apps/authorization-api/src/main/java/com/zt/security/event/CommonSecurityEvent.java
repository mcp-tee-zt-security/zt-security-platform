package com.zt.security.event;
import java.time.Instant;
import java.util.*;
/** Internal producer contract. Observation is evidence, never an authorization grant. */
public record CommonSecurityEvent(UUID tenantId,UUID workspaceId,String source,String eventType,String stage,
 String originId,Instant occurredAt,String correlationId,UUID requestId,String caller,String humanSubject,String aiSubject,
 String action,String resourceType,String resourceId,String decision,String status,Double riskScore,boolean analysisEligible,Map<String,Object> metadata){}
