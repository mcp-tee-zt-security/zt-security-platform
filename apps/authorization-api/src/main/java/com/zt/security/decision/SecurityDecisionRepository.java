package com.zt.security.decision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SecurityDecisionRepository extends JpaRepository<SecurityDecision,
UUID>{
    @org.springframework.data.jpa.repository.Query(value="SELECT d.* FROM security_decisions d WHERE d.tenant_id=:tenant AND EXISTS(SELECT 1 FROM audit_logs a WHERE a.tenant_id=d.tenant_id AND a.request_id=d.request_id AND a.workspace_id=:workspace) ORDER BY d.created_at DESC LIMIT 100",nativeQuery=true)
    List<SecurityDecision> recentInWorkspace(@org.springframework.data.repository.query.Param("tenant") UUID tenant,@org.springframework.data.repository.query.Param("workspace") UUID workspace);
    @org.springframework.data.jpa.repository.Query(value="SELECT count(*) FROM security_decisions d WHERE d.id=:id AND d.tenant_id=:tenant AND EXISTS(SELECT 1 FROM audit_logs a WHERE a.tenant_id=d.tenant_id AND a.request_id=d.request_id AND a.workspace_id=:workspace)",nativeQuery=true)
    long countInWorkspace(@org.springframework.data.repository.query.Param("tenant") UUID tenant,@org.springframework.data.repository.query.Param("workspace") UUID workspace,@org.springframework.data.repository.query.Param("id") UUID id);
    List<SecurityDecision> findTop100ByTenantIdOrderByCreatedAtDesc(UUID t);
    Optional<SecurityDecision> findTopByTenantIdAndPrincipalIdOrderByCreatedAtDesc(UUID t,
    String p);
    }
