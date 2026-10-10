package com.zt.security.stitch;

import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.time.Instant;
import java.util.*;

@Component @ConditionalOnProperty(name="zt.stitch.enabled",havingValue="true")
public class StitchIdentity {
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;
    private final com.zt.security.common.TenantSession tenants;
    private final com.zt.security.common.WorkspaceSession workspaces;
    public StitchIdentity(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc,com.zt.security.common.TenantSession tenants,com.zt.security.common.WorkspaceSession workspaces){this.jdbc=jdbc;this.tenants=tenants;this.workspaces=workspaces;}
    @Value("${zt.stitch.audience:zt-stitch-integration}") private String audience;
    @Value("${zt.stitch.allow-development-sync-key:false}") private boolean developmentSyncKey;
    public record Actor(String subject,String type,String jti,Set<String> groups,Instant expiresAt,boolean sync){}
    public Actor resolve(Authentication auth,UUID tenant,UUID workspace){
        if(auth==null||!auth.isAuthenticated())throw new AccessDeniedException("Authentication required");
        var roles=auth.getAuthorities().stream().map(x->x.getAuthority()).toList();
        if(auth instanceof JwtAuthenticationToken jwt){
            var token=jwt.getToken();String kind=token.getClaimAsString("actor_type");
            if(!token.getAudience().contains(audience)||!tenant.toString().equalsIgnoreCase(token.getClaimAsString("tenant_id"))||!workspace.toString().equalsIgnoreCase(token.getClaimAsString("workspace_id"))||token.getId()==null||token.getId().isBlank()||token.getId().length()>512||token.getExpiresAt()==null||!token.getExpiresAt().isAfter(Instant.now()))throw new AccessDeniedException("JWT requires matching audience/scope, jti and expiration");
            if(!List.of("HUMAN","AI_AGENT","CONNECTOR").contains(kind==null?"":kind))throw new AccessDeniedException("Trusted actor_type required");
            if(token.getSubject()==null||token.getSubject().isBlank()||token.getSubject().length()>512)throw new AccessDeniedException("Valid subject required");
            tenants.set(tenant);workspaces.set(workspace);
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))",Map.of("key","stitch-integration:"+tenant+":"+workspace),Object.class);
            if(!token.getExpiresAt().isAfter(Instant.now()))throw new AccessDeniedException("JWT expired while waiting for resource scope");
            if(jdbc.queryForObject("SELECT count(*) FROM stitch_revoked_tokens WHERE tenant_id=:tenant AND workspace_id=:workspace AND jti=:jti AND expires_at>now()",Map.of("tenant",tenant,"workspace",workspace,"jti",token.getId()),Integer.class)>0)throw new AccessDeniedException("JWT has been revoked by the source identity provider");
            if(roles.contains("ROLE_SERVICE_CLIENT")||roles.contains("ROLE_AI_AGENT"))kind="AI_AGENT";
            var groups=token.getClaimAsStringList("groups");
            if(groups!=null&&(groups.size()>100||groups.stream().anyMatch(x->x==null||x.isBlank()||x.length()>256)))throw new AccessDeniedException("Invalid trusted group claims");
            boolean sync=(roles.contains("ROLE_RETRIEVAL_SYNC")||roles.contains("ROLE_STITCH_SYNC"))&&"CONNECTOR".equals(kind);
            return new Actor(token.getSubject(),kind,token.getId(),groups==null?Set.of():Set.copyOf(groups),token.getExpiresAt(),sync);
        }
        // ApiKeyFilter has already checked service credential tenant/workspace scope.
        if(roles.contains("ROLE_SERVICE_CLIENT")&&auth.getName().startsWith("client:")){
            tenants.set(tenant);workspaces.set(workspace);
            var rows=jdbc.queryForList("SELECT client_id FROM api_clients WHERE tenant_id=:tenant AND client_id=:client AND status='ACTIVE' AND (expires_at IS NULL OR expires_at>now()) AND (workspace_id IS NULL OR workspace_id=:workspace) FOR SHARE",Map.of("tenant",tenant,"workspace",workspace,"client",auth.getName().substring("client:".length())));
            if(rows.size()!=1)throw new AccessDeniedException("Service client has been revoked or has mismatched scope");
            return new Actor(auth.getName(),"AI_AGENT","",Set.of(),Instant.now().plusSeconds(300),false);
        }
        if(developmentSyncKey&&"api-key".equals(auth.getName())&&roles.contains("ROLE_PLATFORM"))return new Actor(auth.getName(),"CONNECTOR","development-sync-only",Set.of(),Instant.now().plusSeconds(300),true);
        throw new AccessDeniedException("Human access requires OIDC JWT; administrator keys cannot impersonate a user");
    }
    public Actor connector(Authentication auth,UUID tenant,UUID workspace){return boundConnector(auth,tenant,workspace,true);}
    public Actor cleanupConnector(Authentication auth,UUID tenant,UUID workspace){return boundConnector(auth,tenant,workspace,false);}
    private Actor boundConnector(Authentication auth,UUID tenant,UUID workspace,boolean requireEnabled){var a=resolve(auth,tenant,workspace);if(!a.sync())throw new AccessDeniedException("RETRIEVAL_SYNC connector credential required");tenants.set(tenant);workspaces.set(workspace);if(jdbc.queryForObject("SELECT count(*) FROM retrieval_connections WHERE tenant_id=:tenant AND workspace_id=:workspace AND connector_subject=:subject AND (:required=false OR enabled=true)",Map.of("tenant",tenant,"workspace",workspace,"subject",a.subject(),"required",requireEnabled),Integer.class)!=1)throw new AccessDeniedException("Connector subject must be registered by a ZT administrator in this workspace");return a;}
}
