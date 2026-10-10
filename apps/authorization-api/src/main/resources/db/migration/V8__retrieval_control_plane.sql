-- Central ZT connection metadata. Source data tables retain their historical names for compatibility.
CREATE TABLE retrieval_connections (
 tenant_id uuid NOT NULL, workspace_id uuid NOT NULL,
 connection_id varchar(64) NOT NULL, display_name varchar(128) NOT NULL,
 connector_subject varchar(512) NOT NULL, enabled boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant_id,workspace_id), UNIQUE(tenant_id,connection_id),
 FOREIGN KEY(workspace_id) REFERENCES workspaces(id)
);
ALTER TABLE retrieval_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE retrieval_connections FORCE ROW LEVEL SECURITY;
CREATE POLICY retrieval_connection_tenant ON retrieval_connections
 USING (tenant_id=nullif(current_setting('app.tenant_id',true),'')::uuid)
 WITH CHECK (tenant_id=nullif(current_setting('app.tenant_id',true),'')::uuid);
ALTER TABLE stitch_integration_scopes ADD COLUMN policy_fingerprint varchar(64) NOT NULL DEFAULT '';
ALTER TABLE stitch_integration_audit ADD COLUMN policy_reason text;
ALTER TABLE stitch_integration_audit ADD COLUMN matched_policies jsonb NOT NULL DEFAULT '[]';
-- ai_access is now a deprecated source field, ignored by the authorization path.
-- No connection or ALLOW policy is installed automatically by this migration.
