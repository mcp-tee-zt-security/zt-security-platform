CREATE TABLE workspace_access_grants (
 tenant_id uuid NOT NULL,
 workspace_id uuid NOT NULL,
 principal_type varchar(16) NOT NULL CHECK(principal_type IN ('HUMAN','AI_AGENT')),
 principal_id varchar(512) NOT NULL,
 enabled boolean NOT NULL DEFAULT true,
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant_id,workspace_id,principal_type,principal_id),
 FOREIGN KEY(tenant_id,workspace_id) REFERENCES workspaces(tenant_id,id)
);
ALTER TABLE workspace_access_grants ENABLE ROW LEVEL SECURITY;
ALTER TABLE workspace_access_grants FORCE ROW LEVEL SECURITY;
CREATE POLICY workspace_grant_tenant ON workspace_access_grants
 USING(tenant_id=nullif(current_setting('app.tenant_id',true),'')::uuid)
 WITH CHECK(tenant_id=nullif(current_setting('app.tenant_id',true),'')::uuid);
ALTER TABLE audit_logs ADD COLUMN workspace_id uuid;
ALTER TABLE audit_logs ADD CONSTRAINT audit_workspace_tenant FOREIGN KEY(tenant_id,workspace_id) REFERENCES workspaces(tenant_id,id);
CREATE INDEX audit_workspace_recent ON audit_logs(tenant_id,workspace_id,created_at DESC);
-- Audit events are append-only. Preserve historical rows without updating them
-- or disabling their mutation guard. New events receive their workspace on insert.
-- Historical NULL scopes are excluded from workspace-specific record views.
