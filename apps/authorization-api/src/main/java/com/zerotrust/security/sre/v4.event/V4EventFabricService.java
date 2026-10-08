package com.zerotrust.security.sre.v4.event;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class V4EventFabricService {
 public Map<String,String> envelope(String eventId,String tenant,String traceId) {
  return Map.of("eventId",eventId,"tenantId",tenant,"traceId",traceId,"schemaVersion","4.0");
 }
 public boolean idempotent(String eventId) {
     return eventId!=null && !eventId.isBlank();
 }
}
