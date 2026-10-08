package com.zerotrust.security.sre.secops;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
@Service
public class SecOpsIncidentService {
 private final List<SecOpsIncident> incidents=new CopyOnWriteArrayList<>();
 private final List<SecOpsResponseAction> actions=new CopyOnWriteArrayList<>();
 public SecOpsIncident open(UUID tenantId,String incidentKey,String title,
 String severity,String source,String owner,String summary) {
  var i=new SecOpsIncident(UUID.randomUUID(),tenantId,incidentKey,title,
  severity,"OPEN",source,OffsetDateTime.now(),owner,summary);
  incidents.add(i);
  return i;
 }
 public List<SecOpsIncident> list(UUID tenantId) {
     return incidents.stream().filter(i->
     i.tenantId().equals(tenantId)).toList();
 }
 public SecOpsResponseAction requestResponse(UUID tenantId,UUID incidentId,
 String actionType,String requestedBy,String rationale) {
  var a=new SecOpsResponseAction(UUID.randomUUID(),tenantId,incidentId,
  actionType,requestedBy,"PENDING","NOT_EXECUTED",OffsetDateTime.now(),rationale);
  actions.add(a);
  return a;
 }
 public SecOpsResponseAction approve(UUID tenantId,UUID actionId) {
  return actions.stream().filter(a->a.id().equals(actionId)&&a.tenantId().equals(tenantId)).findFirst()
   .map(a->new SecOpsResponseAction(a.id(),a.tenantId(),a.incidentId(),
   a.actionType(),a.requestedBy(),"APPROVED","NOT_EXECUTED",a.requestedAt(),
   a.rationale()))
   .orElseThrow();
 }
 public List<SecOpsResponseAction> actions(UUID tenantId,UUID incidentId) {
  return actions.stream().filter(a->a.tenantId().equals(tenantId)&&a.incidentId().equals(incidentId)).toList();
 }
}
