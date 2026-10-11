package com.zt.security.audit;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.zt.security.common.TenantSession;
@RestController @RequestMapping("/v1/audit") public class AuditController {
    final AuditRepository repo;
    final TenantSession session;
    AuditController(AuditRepository r,
    TenantSession s){
        repo=r;
        session=s;
    }
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR','POLICY_MANAGER','SOC_ANALYST')")
    @GetMapping @Transactional(readOnly=true) List<AuditLog>
list(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader(value="X-Workspace-Id",required=false) UUID workspace){
        session.set(t);
        return workspace==null?repo.findTop100ByTenantIdOrderByCreatedAtDesc(t):repo.findTop100ByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(t,workspace);
    }
    }
