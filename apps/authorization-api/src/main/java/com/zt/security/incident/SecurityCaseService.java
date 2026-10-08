package com.zt.security.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.blast.BlastRadiusAssessment;
import com.zt.security.blast.BlastRadiusAssessmentRepository;
import com.zt.security.common.TenantSession;
import com.zt.security.event.SecurityEventService;
import com.zt.security.response.SecurityResponseAction;
import com.zt.security.response.SecurityResponseActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class SecurityCaseService {
 private final SecurityCaseRepository cases;
 private final CaseEvidenceRepository evidence;
 private final SecurityResponseActionRepository responses;
 private final BlastRadiusAssessmentRepository assessments;
 private final SecurityEventService events;
 private final TenantSession tenant;
 private final ObjectMapper mapper;
 SecurityCaseService(SecurityCaseRepository c,CaseEvidenceRepository e,
 SecurityResponseActionRepository r,BlastRadiusAssessmentRepository a,SecurityEventService ev,
 TenantSession ts,ObjectMapper m){
     cases=c;
     evidence=e;
     responses=r;
     assessments=a;
     events=ev;
     tenant=ts;
     mapper=m;
     }

 @Transactional public Map<String,Object> createFromResponse(UUID t,UUID responseId,String requestedBy){
  tenant.set(t);
  SecurityResponseAction r=responses.findByIdAndTenantId(responseId,
  t).orElseThrow(()->new SecurityException("response action not found"));
  SecurityCase c=new SecurityCase();
  c.setTenantId(t);
  c.setTitle("AI Agent Security Incident — "+r.getActionType());
  c.setSeverity(r.getPriority());
  c.setStatus("OPEN");
  c.setSourceType("SECURITY_RESPONSE");
  c.setSourceId(r.getId());
  c.setAssignedTo(requestedBy);
  c.setSummary(r.getReason());
  cases.save(c);
  addEvidenceInternal(t,c,"RESPONSE_ACTION",r.getId().toString(),Map.of("actionType",
  r.getActionType(),"target",r.getTarget(),"status",r.getStatus(),"reason",
  r.getReason(),"priority",r.getPriority()),requestedBy);
  if(r.getAssessmentId()!=null){
      assessments.findById(r.getAssessmentId()).filter(a->
      t.equals(a.getTenantId())).ifPresent(a->addEvidenceInternal(t,
      c,"BLAST_RADIUS_ASSESSMENT",a.getId().toString(),Map.of("assessmentId",
      a.getId(),"result",read(a.getResult())),requestedBy));
      }
  audit(t,c,"SECURITY_CASE_CREATED",Map.of("responseId",r.getId(),"requestedBy",requestedBy));
  return view(c);
 }
 @Transactional public CaseEvidence addEvidence(UUID t,UUID caseId,String type,
 String sourceRef,Object payload,String createdBy){
  tenant.set(t);
  SecurityCase c=get(t,caseId);
  CaseEvidence e=addEvidenceInternal(t,
  c,type,sourceRef,payload,createdBy);
  audit(t,c,"SECURITY_CASE_EVIDENCE_ADDED",
  Map.of("evidenceId",e.getId(),"type",type));
  return e;
 }
 @Transactional public SecurityCase updateStatus(UUID t,UUID id,String status,String actor){
  tenant.set(t);
  SecurityCase c=get(t,id);
  String s=status.toUpperCase(Locale.ROOT);
  if(!Set.of("OPEN","INVESTIGATING","CONTAINED","RESOLVED","CLOSED").contains(s)) throw new
IllegalArgumentException("unsupported case status");
  c.setStatus(s);
  c.touch();
  if(Set.of("RESOLVED","CLOSED").contains(s)) c.setClosedAt(Instant.now());
  cases.save(c);
  audit(t,c,"SECURITY_CASE_STATUS_CHANGED",Map.of("status",
  s,"actor",actor));
  return c;
 }
 @Transactional public Map<String,Object> handoff(UUID t,UUID id,String connector,String actor){
  tenant.set(t);
  SecurityCase c=get(t,id);
  String ref="SOAR-"+c.getId().toString().substring(0,
  8).toUpperCase(Locale.ROOT);
  c.setExternalRef(ref);
  c.touch();
  cases.save(c);
  Map<String,Object> p=Map.of("caseId",c.getId(),"connector",connector,
  "externalRef",ref,"status","HANDED_OFF","actor",actor);
  audit(t,c,"SECURITY_CASE_SOAR_HANDOFF",
  p);
  addEvidenceInternal(t,c,"SOAR_HANDOFF",ref,p,actor);
  return Map.of("case",
  view(c),"handoff",p);
 }
 @Transactional(readOnly=true) public List<Map<String,Object>> list(UUID t){
     tenant.set(t);
     return cases.findTop100ByTenantIdOrderByUpdatedAtDesc(t).stream().map(this::view).toList();
 }
 @Transactional(readOnly=true) public Map<String,Object> detail(UUID t,
 UUID id){
     tenant.set(t);
     SecurityCase c=get(t,id);
     return Map.of("case",view(c),
     "evidence",evidence.findTop200ByTenantIdAndCaseIdOrderByCreatedAtAsc(t,
     id).stream().map(this::evidenceView).toList());
     }
 private SecurityCase get(UUID t,UUID id){
     return cases.findByIdAndTenantId(id,
     t).orElseThrow(()->new SecurityException("security case not found"));
     }
 private CaseEvidence addEvidenceInternal(UUID t,SecurityCase c,String type,String sourceRef,Object payload,String by){
  CaseEvidence e=new CaseEvidence();
  e.setTenantId(t);
  e.setCaseId(c.getId());
  e.setEvidenceType(type);
  e.setSourceRef(sourceRef);
  e.setCreatedBy(by);
  e.setPayload(json(payload));
  e.setContentHash(sha256(e.getPayload()));
  evidence.save(e);
  c.setEvidenceCount(c.getEvidenceCount()+1);
  c.touch();
  cases.save(c);
  return e;
 }
 private void audit(UUID t,SecurityCase c,String type,Object payload){
     events.enqueue(t,null,type,"SECURITY_CASE",c.getId().toString(),json(payload));
 }
 private Map<String,Object> view(SecurityCase c){
     Map<String,Object> m=new LinkedHashMap<>();
     m.put("id",c.getId());
     m.put("title",c.getTitle());
     m.put("severity",c.getSeverity());
     m.put("status",c.getStatus());
     m.put("sourceType",c.getSourceType());
     m.put("sourceId",
     String.valueOf(c.getSourceId()));
     m.put("assignedTo",String.valueOf(c.getAssignedTo()));
     m.put("summary",c.getSummary());
     m.put("externalRef",String.valueOf(c.getExternalRef()));
     m.put("evidenceCount",c.getEvidenceCount());
     m.put("createdAt",c.getCreatedAt());
     m.put("updatedAt",c.getUpdatedAt());
     m.put("closedAt",String.valueOf(c.getClosedAt()));
     return m;
     }
 private Map<String,Object> evidenceView(CaseEvidence e){
     return new LinkedHashMap<>(Map.of("id",
     e.getId(),"type",e.getEvidenceType(),"sourceRef",e.getSourceRef(),"payload",
     read(e.getPayload()),"hash",e.getContentHash(),"createdBy",e.getCreatedBy(),
     "createdAt",e.getCreatedAt()));
     }
 private Map<String,Object> read(String s){
     try{
         Object o=mapper.readValue(s,
         Object.class);
         return o instanceof Map<?,?> m?(Map<String,Object>)m:Map.of("value",
         o);
         }
         catch(Exception e){
             return Map.of();
         }
         }
 private String json(Object x){
     try{
         return mapper.writeValueAsString(x);
     }
 catch(Exception e){
     return "{}";
 }
 }
 private String sha256(String s){
     try{
         byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.
         UTF_8));
         StringBuilder h=new StringBuilder();
         for(byte x:b)h.append(String.format("%02x",
         x));
         return h.toString();
         }
         catch(Exception e){
             throw new IllegalStateException(e);
     }
     }
}
