CREATE TABLE mcp_upstream_servers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    workspace_id UUID REFERENCES workspaces(id),
    server_id VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    endpoint TEXT NOT NULL,
    bearer_token_env VARCHAR(128),
    enabled BOOLEAN NOT NULL DEFAULT true,
    development_http BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX mcp_upstream_servers_scope ON mcp_upstream_servers
    (tenant_id, COALESCE(workspace_id, '00000000-0000-0000-0000-000000000000'::uuid), server_id);
ALTER TABLE mcp_upstream_servers ENABLE ROW LEVEL SECURITY;
ALTER TABLE mcp_upstream_servers FORCE ROW LEVEL SECURITY;
CREATE POLICY mcp_upstream_servers_isolation ON mcp_upstream_servers
USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    AND workspace_id IS NOT DISTINCT FROM NULLIF(current_setting('app.workspace_id', true), '')::uuid)
WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    AND workspace_id IS NOT DISTINCT FROM NULLIF(current_setting('app.workspace_id', true), '')::uuid);
