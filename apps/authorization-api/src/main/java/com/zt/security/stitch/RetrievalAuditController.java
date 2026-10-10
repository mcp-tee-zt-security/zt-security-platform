package com.zt.security.stitch;
import com.zt.security.common.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/retrieval/audit") @ConditionalOnProperty(name="zt.stitch.enabled",havingValue="true")
@PreAuthorize("hasAnyRole('PLATFORM','ADMIN','POLICY_MANAGER')")
public class RetrievalAuditController {
 private final NamedParameterJdbcTemplate jdbc;private final TenantSession tenants;private final WorkspaceSession workspaces;
 public RetrievalAuditController(NamedParameterJdbcTemplate jdbc,TenantSession tenants,WorkspaceSession workspaces){this.jdbc=jdbc;this.tenants=tenants;this.workspaces=workspaces;}
 @GetMapping @Transactional(readOnly=true) public Object list(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w){tenants.set(t);workspaces.set(w);return jdbc.queryForList("SELECT id,subject,operation,resource_id,decision,acl_version,returned_count,policy_reason,matched_policies,created_at FROM stitch_integration_audit WHERE tenant_id=:tenant AND workspace_id=:workspace ORDER BY created_at DESC LIMIT 100",Map.of("tenant",t,"workspace",w));}
}
