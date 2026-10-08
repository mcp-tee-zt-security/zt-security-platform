package com.zt.security.audit;
import com.zt.security.policy.PolicyEvaluator;
import com.zt.security.action.EvaluateModels.*;
import com.zt.security.common.Jsons;
import com.zt.security.risk.RiskEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
@Service public class AuditService {
    final AuditRepository repo;
    final ObjectMapper mapper;
    final AuditArchiveService archive;
    AuditService(AuditRepository r,ObjectMapper m,
    AuditArchiveService a){
        repo=r;
        mapper=m;
        archive=a;
    }
    public AuditLog record(UUID tenant,
    UUID request,EvaluateRequest r,PolicyEvaluator.Result d,RiskEngine.Result risk){
        AuditLog a=new AuditLog();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenant);
        a.setRequestId(request);
        a.setIdentityType(r.principal().type());
        a.setAction(r.action().name());
        a.setResourceType(r.resource().type());
        a.setResourceId(r.resource().id());
        a.setDecision(d.decision());
        a.setReason(d.reason());
        a.setRiskScore(risk.score());
        if(!d.matched().isEmpty())a.setPolicyId(d.matched().get(0).id());
        a.setMetadata(Jsons.string(mapper,
        Map.of("principal",r.principal().id(),"context",r.context()==null?Map.of():r.context(),
        "matchedPolicies",d.matched())));
        String prev=repo.findTopByTenantIdOrderByCreatedAtDesc(tenant).
        map(AuditLog::getEventHash).orElse("");
        a.setPreviousHash(prev);
        a.setEventHash(hash(prev+a.getId()+tenant+request+d.decision()+a.getCreatedAt()));
        AuditLog saved=repo.save(a);
        archive.append(saved);
        return saved;
        }
        String hash(String s){
        try{
            byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(b);
            }
            catch(Exception e){
                throw new IllegalStateException(e);
        }
        }
        }
