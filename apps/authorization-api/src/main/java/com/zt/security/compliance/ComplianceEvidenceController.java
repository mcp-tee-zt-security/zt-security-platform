package com.zt.security.compliance;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/v1/compliance") public class ComplianceEvidenceController {
    private final ComplianceEvidenceService s;
    private final ComplianceExportService export;
    ComplianceEvidenceController(ComplianceEvidenceService s,ComplianceExportService export){
        this.s=s;
        this.export=export;
        }
 @PostMapping("/assessments/generate") Map<String,Object> generate(@RequestHeader("X-Tenant-Id") UUID t,
 @RequestBody(required=false) Map<String,Object> b){
     Map<String,Object>x=b==null?Map.of():b;
     Instant to=Instant.now();
     Instant from=to.minus(Duration.ofDays(Long.parseLong(String.valueOf(x.getOrDefault("days",
     30)))));
     return s.generate(t,String.valueOf(x.getOrDefault("framework",
     "SOC2")),String.valueOf(x.getOrDefault("generatedBy","dashboard-admin")),
     from,to);
     }
 @GetMapping("/assessments") List<Map<String,Object>> history(@RequestHeader("X-Tenant-Id") UUID t){
     return s.history(t);
     }
 @GetMapping("/assessments/{id}") Map<String,Object> get(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id){
     return s.get(t,id);
 }
 @GetMapping("/assessments/{id}/export/{format}") org.springframework.http.ResponseEntity<byte[]>
export(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@PathVariable String format){
     var x=export.export(t,
     id,format);
     return org.springframework.http.ResponseEntity.ok().header("Content-Disposition",
     "attachment; filename=\""+x.filename()+"\"").header("X-Content-Type-Options",
     "nosniff").contentType(org.springframework.http.MediaType.parseMediaType(x.contentType())).body(x.bytes());
 }
}
