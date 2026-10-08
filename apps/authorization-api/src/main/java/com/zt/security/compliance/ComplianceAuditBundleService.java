package com.zt.security.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.common.TenantSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;

@Service
public class ComplianceAuditBundleService {
 private final ComplianceAssessmentRepository assessments;
 private final ComplianceAuditBundleRepository bundles;
 private final TenantSession tenant;
 private final ComplianceExportService exports;
 private final ObjectMapper mapper;
 public ComplianceAuditBundleService(ComplianceAssessmentRepository a,
 ComplianceAuditBundleRepository b,TenantSession t,ComplianceExportService e,
 ObjectMapper m){
     assessments=a;
     bundles=b;
     tenant=t;
     exports=e;
     mapper=m;
 }
 @Transactional public BundleResult create(UUID t,UUID assessmentId,String createdBy){
     tenant.set(t);
     ComplianceAssessment a=assessments.findByIdAndTenantId(assessmentId,
     t).orElseThrow(()->new SecurityException("compliance assessment not found"));
  try {
      Map<String,Object> manifest=new TreeMap<>();
      manifest.put("schema",
      "zt.security.compliance.audit-bundle/v1");
      manifest.put("tenantId",t.toString());
      manifest.put("assessmentId",assessmentId.toString());
      manifest.put("framework",
      a.getFramework());
      manifest.put("generatedAt",Instant.now().toString());
   Map<String,byte[]> files=new TreeMap<>();
   Map<String,Object> assessmentJson=new LinkedHashMap<>();
   assessmentJson.put("id",a.getId());
   assessmentJson.put("tenantId",t);
   assessmentJson.put("framework",
   a.getFramework());
   assessmentJson.put("periodStart",a.getPeriodStart());
   assessmentJson.put("periodEnd",a.getPeriodEnd());
   assessmentJson.put("status",
   a.getStatus());
   assessmentJson.put("score",a.getScore());
   assessmentJson.put("evidenceCount",
   a.getEvidenceCount());
   assessmentJson.put("reportHash",a.getReportHash());
   assessmentJson.put("controls",a.getControls());
   files.put("assessment.json",
   mapper.writeValueAsBytes(assessmentJson));
   files.put("evidence.csv",exports.export(t,assessmentId,"csv").bytes());
   files.put("evidence.pdf",exports.export(t,assessmentId,"pdf").bytes());
   Map<String,String> hashes=new TreeMap<>();
   for(var e:files.entrySet()) hashes.put(e.getKey(),
   sha(e.getValue()));
   manifest.put("files",hashes);
   byte[] canonical=mapper.writeValueAsBytes(manifest);
   byte[] manifestHash=MessageDigest.getInstance("SHA-256").digest(canonical);
   String signature=sign(canonical);
   manifest.put("manifestSha256",hex(manifestHash));
   manifest.put("signature",signature);
   manifest.put("keyId",env("ZT_EVIDENCE_SIGNING_KEY_ID",
   "enterprise-evidence-ed25519"));
   files.put("manifest.json",mapper.writeValueAsBytes(manifest));
   byte[] zip=zip(files);
   String bundleHash=sha(zip);
   ComplianceAuditBundle b=new ComplianceAuditBundle();
   b.setTenantId(t);
   b.setAssessmentId(assessmentId);
   b.setBundleHash(bundleHash);
   b.setSignature(signature);
   b.setKeyId(String.valueOf(manifest.get("keyId")));
   b.setCreatedBy(createdBy);
   bundles.save(b);
   return new BundleResult(zip,
   "compliance-audit-bundle-"+assessmentId+".zip",bundleHash,signature,String.valueOf(manifest.get("keyId")),
   b.getId());
  }
  catch(Exception e){
      throw new IllegalStateException("audit bundle generation failed",e);
  }
 }
 public VerifyResult verify(byte[] zipBytes){
     try{
         Map<String,byte[]> files=unzip(zipBytes);
         if(!files.containsKey("manifest.json"))return new VerifyResult(false,"manifest.json missing",
         sha(zipBytes));
         Map<String,Object> m=mapper.readValue(files.get("manifest.json"),
         Map.class);
         Object sigObj=m.get("signature");
         if(sigObj==null)return new VerifyResult(false,
         "signature missing",sha(zipBytes));
         String sig=String.valueOf(sigObj);
         Map<String,Object> copy=new TreeMap<>(m);
         copy.remove("signature");
         copy.remove("manifestSha256");
         copy.remove("keyId");
         byte[] canonical=mapper.writeValueAsBytes(copy);
         boolean signatureValid=verifySignature(canonical,sig);
         Object fileObj=m.get("files");
         boolean hashesValid=true;
         if(fileObj instanceof Map<?,?> fm){
             for(var e:fm.entrySet()){
                 byte[] actual=files.get(String.valueOf(e.getKey()));
                 if(actual==
                 null||!sha(actual).equals(String.valueOf(e.getValue())))hashesValid=false;
             }
             }
             else hashesValid=false;
             return new VerifyResult(signatureValid&&hashesValid,
     signatureValid?(hashesValid?"signature and file hashes valid":"file hash mismatch"):"signature invalid",
     sha(zipBytes));
     }
     catch(Exception e){
         return new VerifyResult(false,"verification error: "+
     e.getClass().getSimpleName(),
     sha(zipBytes));
     }
     }
 private String sign(byte[] data)throws Exception{
     String b=System.getenv("ZT_EVIDENCE_SIGNING_PRIVATE_KEY_B64");
     if(b==null||b.isBlank())return null;
     PrivateKey k=KeyFactory.getInstance("Ed25519").generatePrivate(new
PKCS8EncodedKeySpec(Base64.
     getDecoder().decode(b)));
     Signature s=Signature.getInstance("Ed25519");
     s.initSign(k);
     s.update(data);
     return Base64.getEncoder().encodeToString(s.sign());
     }
 private boolean verifySignature(byte[] data,String signature)throws Exception{
     String b=System.getenv("ZT_EVIDENCE_SIGNING_PUBLIC_KEY_B64");
     if(b==null||
     b.isBlank()||signature==null||signature.isBlank())return false;
     PublicKey k=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().
     decode(b)));
     Signature s=Signature.getInstance("Ed25519");
     s.initVerify(k);
     s.update(data);
     return s.verify(Base64.getDecoder().decode(signature));
     }
 private byte[] zip(Map<String,byte[]> files)throws IOException{
     ByteArrayOutputStream o=new ByteArrayOutputStream();
     try(ZipOutputStream z=new ZipOutputStream(o)){
         for(var e:files.entrySet()){
             z.putNextEntry(new ZipEntry(e.getKey()));
             z.write(e.getValue());
             z.closeEntry();
         }
         }
         return o.toByteArray();
         }
 private Map<String,byte[]> unzip(byte[] bytes)throws IOException{
     Map<String,
     byte[]> out=new HashMap<>();
     try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(bytes))){
         ZipEntry e;
         while((e=z.getNextEntry())!=null){
             if(e.isDirectory())continue;
             ByteArrayOutputStream o=new ByteArrayOutputStream();
             z.transferTo(o);
             out.put(e.getName(),
             o.toByteArray());
             }
             }
             return out;
             }
 private String sha(byte[] x){
     try {
         return hex(MessageDigest.getInstance("SHA-256").digest(x));
     } catch (java.security.NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 unavailable", e);
     }
 }
 private String hex(byte[] x){
     return HexFormat.of().formatHex(x);
 }
 private String env(String n,
 String d){
     String v=System.getenv(n);
     return v==null||v.isBlank()?d:v;
 }
 public record BundleResult(byte[] bytes,String filename,String bundleHash,
 String signature,String keyId,UUID id){
 }
 public record VerifyResult(boolean valid,
 String reason,String bundleHash){
 }
}
