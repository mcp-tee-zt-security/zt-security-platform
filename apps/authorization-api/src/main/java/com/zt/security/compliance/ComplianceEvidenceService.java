package com.zt.security.compliance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.audit.*;
import com.zt.security.common.TenantSession;
import com.zt.security.incident.*;
import com.zt.security.response.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
@Service public class ComplianceEvidenceService {
 private final ComplianceAssessmentRepository reports;
 private final AuditRepository audits;
 private final SecurityCaseRepository cases;
 private final CaseEvidenceRepository evidence;
 private final SecurityResponseActionRepository responses;
 private final TenantSession tenant;
 private final ObjectMapper mapper;
 ComplianceEvidenceService(ComplianceAssessmentRepository r,AuditRepository a,
 SecurityCaseRepository c,CaseEvidenceRepository e,SecurityResponseActionRepository s,
 TenantSession t,ObjectMapper m){
     reports=r;
     audits=a;
     cases=c;
     evidence=e;
     responses=s;
     tenant=t;
     mapper=m;
     }
 @Transactional public Map<String,Object> generate(UUID t,String framework,
 String by,Instant from,Instant to){
     tenant.set(t);
     String fw=framework.toUpperCase(Locale.ROOT);
     if(!Set.of("SOC2","ISO27001","FINANCIAL").contains(fw)) throw new
IllegalArgumentException("supported frameworks: SOC2, ISO27001, FINANCIAL");
  long audit=audits.findTop100ByTenantIdOrderByCreatedAtDesc(t).stream().filter(x->between(x.getCreatedAt(),
  from,to)).count();
  long cs=cases.findTop100ByTenantIdOrderByUpdatedAtDesc(t).stream().filter(x->
  between(x.getUpdatedAt(),
  from,to)).count();
  long ev=evidence.findTop200ByTenantIdAndCaseIdOrderByCreatedAtAsc(t,
  cases.findTop100ByTenantIdOrderByUpdatedAtDesc(t).stream().findFirst().map(SecurityCase::getId).orElse(UUID.randomUUID())).size();
  long resp=responses.findTop100ByTenantIdOrderByCreatedAtDesc(t).stream().filter(x->between(x.getCreatedAt(),
  from,to)).count();
  List<Map<String,Object>> controls=controls(fw,audit,cs,resp);
  double score=controls.stream().mapToDouble(x->
  Boolean.TRUE.equals(x.get("met"))?100:0).average().orElse(0);
  String status=score>=90?"READY":score>=70?"PARTIAL":"GAPS";
  Map<String,Object> report=new LinkedHashMap<>();
  report.put("framework",
  fw);
  report.put("periodStart",from);
  report.put("periodEnd",to);
  report.put("generatedBy",
  by);
  report.put("score",score);
  report.put("status",status);
  report.put("controls",
  controls);
  report.put("evidenceSources",Map.of("auditEvents",audit,"securityCases",
  cs,"caseEvidence",ev,"responseActions",resp));
  report.put("disclaimer",
  "Assessment evidence is product-generated and does not constitute SOC 2, " +
"ISO 27001 certification or an independent audit opinion.");
  ComplianceAssessment a=new ComplianceAssessment();
  a.setTenantId(t);
  a.setFramework(fw);
  a.setPeriodStart(from);
  a.setPeriodEnd(to);
  a.setGeneratedBy(by);
  a.setScore(score);
  a.setStatus(status);
  a.setEvidenceCount((int)(audit+cs+ev+resp));
  a.setControls(json(controls));
  a.setReportHash(sha256(json(report)));
  reports.save(a);
  report.put("id",
  a.getId());
  report.put("reportHash",a.getReportHash());
  report.put("createdAt",
  a.getCreatedAt());
  report.put("markdown",markdown(report));
  return report;
 }
 @Transactional(readOnly=true) public List<Map<String,Object>> history(UUID t){
     tenant.set(t);
     return reports.findTop50ByTenantIdOrderByCreatedAtDesc(t).stream().map(this::view).toList();
 }
 @Transactional(readOnly=true) public Map<String,Object> get(UUID t,UUID id){
     tenant.set(t);
     return reports.findByIdAndTenantId(id,t).map(this::view).orElseThrow(()->
     new SecurityException("compliance assessment not found"));
 }
 private List<Map<String,Object>> controls(String fw,long audit,long cases,
 long responses){
     List<Map<String,Object>> c=new ArrayList<>();
     add(c,"CC6.1 / A.5.15",
     "Logical access and tenant-aware authorization",audit>0,"Audit decision evidence");
     add(c,"CC7.2 / A.8.16","Security monitoring and anomaly detection",audit>0,
     "Security decision/audit events");
     add(c,"CC8.1 / A.8.32","Controlled security changes",
     responses>0,"Response and policy lifecycle evidence");
     add(c,"A.5.28",
     "Collection of evidence",cases>0,"Security cases and evidence chain");
     if("FINANCIAL".equals(fw)){
         add(c,"FIN-01","Immutable decision evidence",
         audit>0,"Audit verification boundary");
         add(c,"FIN-02","Incident containment and approval",
         responses>0&&cases>0,"Response approval + case evidence");
         }
         return c;
         }
 private void add(List<Map<String,Object>> c,String id,String name,boolean met,
 String source){
     c.add(Map.of("id",id,"name",name,"met",met,"evidence",source));
 }
 private boolean between(Instant x,Instant a,Instant b){
     return x!=null&&!x.isBefore(a)&&x.isBefore(b);
 }
 private Map<String,Object> view(ComplianceAssessment a){
     return Map.of("id",
     a.getId(),"framework",a.getFramework(),"periodStart",a.getPeriodStart(),
     "periodEnd",a.getPeriodEnd(),"status",a.getStatus(),"score",a.getScore(),
     "evidenceCount",a.getEvidenceCount(),"reportHash",a.getReportHash(),"generatedBy",
     a.getGeneratedBy(),"createdAt",a.getCreatedAt());
     }
private String markdown(Map<String,Object> r){
    StringBuilder s=new StringBuilder("# Zero Trust Compliance Evidence Report\n\n");
     s.append("Framework: **").append(r.get("framework")).append("**\n\nScore: **").append(String.format(Locale.ROOT,
     "%.1f",(Double)r.get("score"))).append("** · Status: **").append(r.get("status")).append("**\n\n");
     s.append("## Controls\n\n| Control | Description | Met | Evidence |\n|---|---|---:|---|\n");
     for(Object o:(List<?>)r.get("controls")){
         Map<?,?> c=(Map<?,?>)o;
         s.append("| ").append(c.get("id")).
         append(" | ").append(c.get("name")).append(" | ").append(c.get("met")).append(" | ").append(c.get("evidence")).
         append(" |\n");
     }
     s.append("\n## Evidence Sources\n\n").append(r.get("evidenceSources")).append("\n\n> This is an internal evidence report, not a"
+
" certification or " +
"independent audit opinion.\n");
 return s.toString();
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
