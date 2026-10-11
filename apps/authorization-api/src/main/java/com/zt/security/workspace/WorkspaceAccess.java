package com.zt.security.workspace;

import com.zt.security.common.TenantSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.Instant;
import java.util.*;

/** A browser-selected header is checked against server grants, never trusted on its own. */
@Service @Transactional(readOnly=true)
public class WorkspaceAccess {
    public static final String VERIFIED_SCOPE="zt.verified-workspace";
    public record Verified(UUID tenant,UUID workspace,String subject,String token){}
    private final NamedParameterJdbcTemplate jdbc;private final TenantSession tenants;
    @Value("${zt.security.mcp.audience:zt-mcp-gateway}") private String audience;
    public WorkspaceAccess(NamedParameterJdbcTemplate jdbc,TenantSession tenants){this.jdbc=jdbc;this.tenants=tenants;}
    public static boolean selectingHuman(Authentication auth){
        return auth instanceof JwtAuthenticationToken jwt&&auth.isAuthenticated()&&"HUMAN".equals(jwt.getToken().getClaimAsString("actor_type"))&&Boolean.TRUE.equals(jwt.getToken().getClaim("workspace_selection"))&&auth.getAuthorities().stream().noneMatch(a->Set.of("ROLE_AI_AGENT","ROLE_SERVICE_CLIENT","ROLE_RETRIEVAL_SYNC","ROLE_STITCH_SYNC").contains(a.getAuthority()));
    }
    public static boolean verifiedRequest(Authentication auth,UUID t,UUID w){
        if(!(auth instanceof JwtAuthenticationToken jwt)||!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))return false;
        var value=attributes.getRequest().getAttribute(VERIFIED_SCOPE);
        return value instanceof Verified scope&&t.equals(scope.tenant())&&Objects.equals(w,scope.workspace())&&jwt.getToken().getSubject().equals(scope.subject())&&Objects.equals(jwt.getToken().getTokenValue(),scope.token());
    }
    private void token(Authentication auth,UUID t){
        if(!(auth instanceof JwtAuthenticationToken jwt)||!auth.isAuthenticated()||jwt.getToken().getSubject()==null||jwt.getToken().getSubject().isBlank()||jwt.getToken().getExpiresAt()==null||!jwt.getToken().getExpiresAt().isAfter(Instant.now())||!t.toString().equalsIgnoreCase(jwt.getToken().getClaimAsString("tenant_id")))throw new AccessDeniedException("Verified tenant-scoped JWT required");
        if(selectingHuman(auth)&&!jwt.getToken().getAudience().contains(audience))throw new AccessDeniedException("Dashboard audience required for workspace selection");
        tenants.set(t);
    }
    private boolean admin(Authentication a){return a.getAuthorities().stream().anyMatch(r->Set.of("ROLE_ADMIN","ROLE_PLATFORM").contains(r.getAuthority()));}
    private boolean allowed(Authentication auth,UUID t,UUID w){
        if(w==null)return false;
        var p=Map.of("t",t,"w",w,"subject",auth.getName());
        if(jdbc.queryForObject("SELECT count(*) FROM workspaces WHERE tenant_id=:t AND id=:w AND status='ACTIVE'",p,Integer.class)!=1)return false;
        var jwt=(JwtAuthenticationToken)auth;
        var grants="HUMAN".equals(jwt.getToken().getClaimAsString("actor_type"))?jdbc.queryForList("SELECT enabled FROM workspace_access_grants WHERE tenant_id=:t AND workspace_id=:w AND principal_type='HUMAN' AND principal_id=:subject",p):List.<Map<String,Object>>of();
        if(!selectingHuman(auth))return w.toString().equalsIgnoreCase(jwt.getToken().getClaimAsString("workspace_id"))&&(grants.isEmpty()||Boolean.TRUE.equals(grants.get(0).get("enabled")));
        if(admin(auth))return true;
        if(!grants.isEmpty())return Boolean.TRUE.equals(grants.get(0).get("enabled"));
        // Signed default scope remains compatible. Explicit disabled access overrides it;
        // additional scopes always require enabled grants in ZT.
        return w.toString().equalsIgnoreCase(jwt.getToken().getClaimAsString("workspace_id"));
    }
    public void require(Authentication auth,UUID t,UUID w){token(auth,t);if(!allowed(auth,t,w))throw new AccessDeniedException("Caller has no active access to the selected workspace");}
    public List<Map<String,Object>> accessible(UUID t,Authentication auth){
        if(!(auth instanceof JwtAuthenticationToken)){
            if(auth==null||!auth.isAuthenticated()||!admin(auth))throw new AccessDeniedException("Workspace discovery requires authentication");
            tenants.set(t);return jdbc.queryForList("SELECT id,name,slug,status FROM workspaces WHERE tenant_id=:t AND status='ACTIVE' ORDER BY name,id",Map.of("t",t));
        }
        token(auth,t);var rows=jdbc.queryForList("SELECT id,name,slug,status FROM workspaces WHERE tenant_id=:t AND status='ACTIVE' ORDER BY name,id",Map.of("t",t));
        return rows.stream().filter(row->allowed(auth,t,(UUID)row.get("id"))).toList();
    }
    @Transactional public void grant(UUID t,UUID w,String kind,String id,boolean enabled){
        tenants.set(t);
        if(!Set.of("HUMAN","AI_AGENT").contains(kind)||id==null||id.isBlank()||id.length()>512)throw new IllegalArgumentException("Valid principal type and subject required");
        jdbc.update("INSERT INTO workspace_access_grants(tenant_id,workspace_id,principal_type,principal_id,enabled) VALUES(:t,:w,:kind,:subject,:enabled) ON CONFLICT(tenant_id,workspace_id,principal_type,principal_id) DO UPDATE SET enabled=EXCLUDED.enabled,updated_at=now()",Map.of("t",t,"w",w,"kind",kind,"subject",id,"enabled",enabled));
    }
    public Object grants(UUID t,UUID w){tenants.set(t);return jdbc.queryForList("SELECT principal_type,principal_id,enabled,updated_at FROM workspace_access_grants WHERE tenant_id=:t AND workspace_id=:w ORDER BY principal_type,principal_id",Map.of("t",t,"w",w));}
    public void workflowAgent(UUID t,UUID w,String id){
        tenants.set(t);if(w==null||!("workflow-chat-"+w).equals(id))throw new AccessDeniedException("Workflow AI belongs to another workspace");
        if(jdbc.queryForObject("SELECT count(*) FROM identities i JOIN workspaces w ON w.tenant_id=i.tenant_id AND w.id=:w WHERE i.tenant_id=:t AND i.external_id=:subject AND i.identity_type='AI_AGENT' AND i.status='ACTIVE' AND w.status='ACTIVE'",Map.of("t",t,"w",w,"subject",id),Integer.class)!=1)throw new AccessDeniedException("No active Workflow AI in this workspace");
        var rows=jdbc.queryForList("SELECT enabled FROM workspace_access_grants WHERE tenant_id=:t AND workspace_id=:w AND principal_type='AI_AGENT' AND principal_id=:subject",Map.of("t",t,"w",w,"subject",id));
        if(!rows.isEmpty()&&!Boolean.TRUE.equals(rows.get(0).get("enabled")))throw new AccessDeniedException("Workflow AI access to this workspace was revoked");
    }
}
