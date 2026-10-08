package com.zt.security.compliance;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
@RestController @RequestMapping("/v1/compliance") public class ComplianceAuditBundleController {
    private final ComplianceAuditBundleService s;
    public ComplianceAuditBundleController(ComplianceAuditBundleService
s){
        this.s=s;
        }
 @PostMapping("/assessments/{id}/audit-bundle") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')")
public ResponseEntity<byte[]> create(@RequestHeader("X-Tenant-Id") UUID t,
 @PathVariable UUID id,@RequestParam(defaultValue="dashboard-admin") String createdBy){
     var b=s.create(t,id,createdBy);
     return ResponseEntity.ok().header("Content-Disposition",
     "attachment; filename=\""+b.filename()+"\"").header("X-ZT-Bundle-Hash",
     b.bundleHash()).header("X-ZT-Signature-Key-Id",b.keyId()).contentType(MediaType.APPLICATION_OCTET_STREAM).
     body(b.bytes());
 }
 @PostMapping(value="/audit-bundles/verify",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')") public Map<String,
 Object> verify(@RequestPart("bundle") MultipartFile f)throws Exception{
     var v=s.verify(f.getBytes());
     return Map.of("valid",v.valid(),"reason",
     v.reason(),"bundleHash",v.bundleHash());
     }
}
