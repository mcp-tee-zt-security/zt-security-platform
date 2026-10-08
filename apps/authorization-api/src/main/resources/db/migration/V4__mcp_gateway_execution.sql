CREATE TABLE mcp_tool_bindings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    workspace_id UUID REFERENCES workspaces(id),
    tool_id UUID NOT NULL REFERENCES agent_tools(id),
    config JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX mcp_tool_bindings_scope ON mcp_tool_bindings
    (tenant_id, COALESCE(workspace_id, '00000000-0000-0000-0000-000000000000'::uuid), tool_id);

CREATE TABLE mcp_invocations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    workspace_id UUID REFERENCES workspaces(id),
    actor_key TEXT NOT NULL,
    actor_subject TEXT NOT NULL,
    request_key VARCHAR(64),
    tool_id UUID NOT NULL REFERENCES agent_tools(id),
    binding_hash VARCHAR(64) NOT NULL,
    arguments JSONB NOT NULL,
    arguments_hash VARCHAR(64) NOT NULL,
    decision_request_id UUID NOT NULL,
    policy_hash VARCHAR(64) NOT NULL,
    approval_id UUID REFERENCES approvals(id),
    status VARCHAR(32) NOT NULL,
    result_hash VARCHAR(64),
    error_code VARCHAR(64),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status IN ('DENIED','PENDING_APPROVAL','EXECUTING','SUCCEEDED','TOOL_ERROR','NOT_EXECUTED','UNKNOWN'))
);
CREATE INDEX mcp_invocations_scope ON mcp_invocations(tenant_id, workspace_id, created_at DESC);
CREATE UNIQUE INDEX mcp_invocations_request ON mcp_invocations
    (tenant_id, COALESCE(workspace_id, '00000000-0000-0000-0000-000000000000'::uuid), actor_key, request_key)
    WHERE request_key IS NOT NULL;

ALTER TABLE mcp_tool_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE mcp_tool_bindings FORCE ROW LEVEL SECURITY;
CREATE POLICY mcp_tool_bindings_isolation ON mcp_tool_bindings
USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    AND workspace_id IS NOT DISTINCT FROM NULLIF(current_setting('app.workspace_id', true), '')::uuid)
WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    AND workspace_id IS NOT DISTINCT FROM NULLIF(current_setting('app.workspace_id', true), '')::uuid);

ALTER TABLE mcp_invocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE mcp_invocations FORCE ROW LEVEL SECURITY;
CREATE POLICY mcp_invocations_isolation ON mcp_invocations
USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    AND workspace_id IS NOT DISTINCT FROM NULLIF(current_setting('app.workspace_id', true), '')::uuid)
WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    AND workspace_id IS NOT DISTINCT FROM NULLIF(current_setting('app.workspace_id', true), '')::uuid);
