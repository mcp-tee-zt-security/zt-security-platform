package com.zt.security.stitch;

import com.zt.security.common.*;
import com.zt.security.policy.PolicyChanged;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** Central policy changes invalidate caches/disclosures independently of source ACL synchronization. */
@Component @ConditionalOnProperty(name="zt.stitch.enabled",havingValue="true")
public class RetrievalPolicyInvalidator {
    private final NamedParameterJdbcTemplate jdbc;private final TenantSession tenants;private final WorkspaceSession workspaces;
    public RetrievalPolicyInvalidator(NamedParameterJdbcTemplate jdbc,TenantSession tenants,WorkspaceSession workspaces){this.jdbc=jdbc;this.tenants=tenants;this.workspaces=workspaces;}
    @EventListener public void changed(PolicyChanged event){
        tenants.set(event.tenant());String previous=jdbc.queryForObject("SELECT current_setting('app.workspace_id',true)",Map.of(),String.class);
        try{
            if(event.workspace()!=null)invalidate(event.tenant(),event.workspace(),"ZT_POLICY_CHANGED");
            else for(var row:jdbc.queryForList("SELECT id FROM workspaces WHERE tenant_id=:tenant ORDER BY id",Map.of("tenant",event.tenant())))invalidate(event.tenant(),(UUID)row.get("id"),"ZT_POLICY_CHANGED");
        }finally{workspaces.set(previous==null||previous.isBlank()?null:UUID.fromString(previous));}
    }
    public void invalidate(UUID t,UUID w,String reason){
        tenants.set(t);workspaces.set(w);var p=new MapSqlParameterSource().addValue("tenant",t).addValue("workspace",w).addValue("reason",reason);
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))",Map.of("key","stitch-integration:"+t+":"+w),Object.class);
        jdbc.update("UPDATE stitch_integration_scopes SET acl_version=acl_version+1,policy_fingerprint='' WHERE tenant_id=:tenant AND workspace_id=:workspace",p);
        jdbc.update("DELETE FROM stitch_retrieval_cache WHERE tenant_id=:tenant AND workspace_id=:workspace",p);
        jdbc.update("INSERT INTO stitch_deletion_events(id,tenant_id,workspace_id,resource_id,target_subject,reason) SELECT gen_random_uuid(),tenant_id,workspace_id,resource_id,ai_subject,:reason FROM stitch_disclosures WHERE tenant_id=:tenant AND workspace_id=:workspace AND revoked=false GROUP BY tenant_id,workspace_id,resource_id,ai_subject",p);
        jdbc.update("UPDATE stitch_disclosures SET revoked=true WHERE tenant_id=:tenant AND workspace_id=:workspace",p);
    }
}
