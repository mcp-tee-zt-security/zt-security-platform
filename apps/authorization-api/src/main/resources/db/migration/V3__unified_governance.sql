CREATE TABLE governance_records (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    workspace_id UUID REFERENCES workspaces(id),
    kind VARCHAR(40) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX governance_records_scope ON governance_records(tenant_id, workspace_id, kind, created_at DESC);
ALTER TABLE governance_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE governance_records FORCE ROW LEVEL SECURITY;
CREATE POLICY governance_records_isolation ON governance_records
USING (
    tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    AND (workspace_id IS NULL OR workspace_id = NULLIF(current_setting('app.workspace_id', true), '')::uuid)
)
WITH CHECK (
    tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    AND (workspace_id IS NULL OR workspace_id = NULLIF(current_setting('app.workspace_id', true), '')::uuid)
);
