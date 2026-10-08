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
    @GetMapping @Transactional(readOnly=true) List<AuditLog>
list(@RequestHeader("X-Tenant-Id") UUID t){
        session.set(t);
        return repo.findTop100ByTenantIdOrderByCreatedAtDesc(t);
    }
    }
