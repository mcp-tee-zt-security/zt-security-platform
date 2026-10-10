package com.zt.security.stitch;

import com.zt.security.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

/** Vendor-independent control-plane registration. One source connector per workspace. */
@RestController @RequestMapping("/v1/retrieval/connections")
@ConditionalOnProperty(name="zt.stitch.enabled",havingValue="true")
@PreAuthorize("hasAnyRole('PLATFORM','ADMIN','POLICY_MANAGER')") @Transactional
public class RetrievalConnectionController {
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;
    private final TenantSession tenants;
    private final WorkspaceSession workspaces;
    private final RetrievalPolicyInvalidator invalidator;
    public RetrievalConnectionController(NamedParameterJdbcTemplate jdbc,TenantSession tenants,WorkspaceSession workspaces,RetrievalPolicyInvalidator invalidator){this.jdbc=jdbc;this.tenants=tenants;this.workspaces=workspaces;this.invalidator=invalidator;}
    public record Registration(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String connectionId,
        @NotBlank @Size(max=128) String displayName,@NotBlank @Size(max=512) String connectorSubject,boolean enabled){}
    private MapSqlParameterSource scope(UUID t,UUID w){tenants.set(t);workspaces.set(w);if(!Integer.valueOf(1).equals(jdbc.queryForObject("SELECT count(*) FROM workspaces WHERE tenant_id=:t AND id=:w",Map.of("t",t,"w",w),Integer.class)))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Workspace unavailable");return new MapSqlParameterSource().addValue("tenant",t).addValue("workspace",w);}
    @GetMapping public Object current(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w){return jdbc.queryForList("SELECT connection_id,display_name,connector_subject,enabled,updated_at FROM retrieval_connections WHERE tenant_id=:tenant AND workspace_id=:workspace",scope(t,w));}
    @PostMapping public Object register(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,@Valid @RequestBody Registration r){
        var p=scope(t,w);jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))",Map.of("key","stitch-integration:"+t+":"+w),Object.class);
        var old=jdbc.queryForList("SELECT connection_id,connector_subject FROM retrieval_connections WHERE tenant_id=:tenant AND workspace_id=:workspace",p);
        if(!old.isEmpty()&&(!r.connectionId().equals(old.get(0).get("connection_id"))||!r.connectorSubject().equals(old.get(0).get("connector_subject"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"Use a new workspace for another source/connector; existing content cannot change ownership");
        if(jdbc.queryForObject("SELECT count(*) FROM retrieval_connections WHERE tenant_id=:tenant AND connection_id=:id AND workspace_id<>:workspace",new MapSqlParameterSource(p.getValues()).addValue("id",r.connectionId()),Integer.class)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Connection ID already registered in this tenant");
        p.addValue("id",r.connectionId()).addValue("name",r.displayName()).addValue("subject",r.connectorSubject()).addValue("enabled",r.enabled());
        jdbc.update("INSERT INTO retrieval_connections(tenant_id,workspace_id,connection_id,display_name,connector_subject,enabled) VALUES(:tenant,:workspace,:id,:name,:subject,:enabled) ON CONFLICT(tenant_id,workspace_id) DO UPDATE SET display_name=EXCLUDED.display_name,enabled=EXCLUDED.enabled,updated_at=now()",p);
        invalidator.invalidate(t,w,"CONNECTION_CHANGED");return Map.of("connectionId",r.connectionId(),"workspaceId",w,"enabled",r.enabled());
    }
    @GetMapping("/resources") public Object resources(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w){return jdbc.queryForList("SELECT resource_id,parent_id,kind,title,source_version FROM stitch_resources WHERE tenant_id=:tenant AND workspace_id=:workspace AND deleted=false ORDER BY resource_id LIMIT 5000",scope(t,w));}
}
