-- A binding chooses the policy principal; transport authentication stays with the client.
CREATE UNIQUE INDEX api_clients_tenant_identity ON api_clients(tenant_id,id);
CREATE UNIQUE INDEX identities_tenant_identity ON identities(tenant_id,id);
CREATE UNIQUE INDEX workspaces_tenant_identity ON workspaces(tenant_id,id);
CREATE TABLE service_agent_bindings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    workspace_id UUID REFERENCES workspaces(id),
    client_id UUID NOT NULL,
    agent_id UUID NOT NULL,
    revision UUID NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    is_default BOOLEAN NOT NULL DEFAULT false,
    CHECK (NOT is_default OR enabled),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY(tenant_id,client_id) REFERENCES api_clients(tenant_id,id),
    FOREIGN KEY(tenant_id,agent_id) REFERENCES identities(tenant_id,id),
    FOREIGN KEY(tenant_id,workspace_id) REFERENCES workspaces(tenant_id,id)
);
CREATE UNIQUE INDEX service_agent_bindings_scope ON service_agent_bindings
    (tenant_id, COALESCE(workspace_id, '00000000-0000-0000-0000-000000000000'::uuid), client_id,agent_id);
CREATE UNIQUE INDEX service_agent_bindings_default ON service_agent_bindings
    (tenant_id, COALESCE(workspace_id, '00000000-0000-0000-0000-000000000000'::uuid), client_id) WHERE is_default;
ALTER TABLE service_agent_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_agent_bindings FORCE ROW LEVEL SECURITY;
CREATE POLICY service_agent_bindings_isolation ON service_agent_bindings
USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    AND workspace_id IS NOT DISTINCT FROM NULLIF(current_setting('app.workspace_id', true), '')::uuid)
WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    AND workspace_id IS NOT DISTINCT FROM NULLIF(current_setting('app.workspace_id', true), '')::uuid);

ALTER TABLE mcp_invocations ADD COLUMN policy_subject TEXT;
ALTER TABLE mcp_invocations ADD COLUMN agent_binding_revision TEXT;
CREATE INDEX mcp_invocations_agent ON mcp_invocations(tenant_id,workspace_id,policy_subject,created_at DESC);
