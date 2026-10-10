package com.zt.security.audit;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
@Service public class AuditArchiveService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuditArchiveService.class);
    @Value("${zt.security.audit.local-archive:./data/audit-archive}")
String dir;
    @Value("${zt.security.audit.s3-enabled:false}") boolean s3Enabled;
@Value("${zt.security.audit.bucket:}") String bucket;
    final S3Client s3;
    AuditArchiveService(S3Client s3){
        this.s3=s3;
    }
    public void append(AuditLog a){
        String stage="LOCAL";
        String line="{\"id\":\""+a.getId()+"\",\"tenantId\":\""+a.getTenantId()+"\",\"requestId\":\""+
            a.getRequestId()+"\",\"decision\":\""+a.getDecision()+"\",\"eventHash\":\""+a.getEventHash()+
            "\",\"previousHash\":\""+a.getPreviousHash()+"\",\"createdAt\":\""+a.getCreatedAt()+"\"}\n";
        try{
            Path p=Paths.get(dir);
            Files.createDirectories(p);
            Path f=p.resolve(a.getTenantId()+
            "-"+a.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate()+".ndjson");
            Files.writeString(f,line,StandardCharsets.UTF_8,StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
            if(s3Enabled&&s3!=null&&!bucket.isBlank()){
                stage="S3";
                String key="tenant="+a.getTenantId()+"/date="+a.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate()+
                "/event="+a.getId()+".json";
                s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/json").build(),
                RequestBody.fromString(line));
                }
                }
                catch(Exception e){
                    log.error("Audit archive failed: stage={}, configuredPath={}, auditId={}", stage, dir, a.getId(), e);
                    // Keep fail-closed behavior; expose a stage/type without returning SDK credential details.
                    throw new IllegalStateException("Audit archive failed: " + stage + " (" + e.getClass().getSimpleName()
                        + "); check archive permissions or configuration", e);
            }
            }
            }
