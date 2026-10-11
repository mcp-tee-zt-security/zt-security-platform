CREATE TABLE workflow_simulated_transfers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  workspace_id UUID NOT NULL REFERENCES workspaces(id),
  contract_id UUID NOT NULL UNIQUE REFERENCES governance_records(id),
  recipient VARCHAR(120) NOT NULL,
  amount NUMERIC(14,2) NOT NULL CHECK (amount > 0 AND amount <= 1000000),
  currency VARCHAR(3) NOT NULL CHECK (currency='EUR'),
  status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY(tenant_id,workspace_id) REFERENCES workspaces(tenant_id,id)
);
ALTER TABLE workflow_simulated_transfers ENABLE ROW LEVEL SECURITY;
ALTER TABLE workflow_simulated_transfers FORCE ROW LEVEL SECURITY;
CREATE POLICY workflow_simulated_transfers_isolation ON workflow_simulated_transfers
USING (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid
    AND workspace_id = NULLIF(current_setting('app.workspace_id',true),'')::uuid)
WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid
    AND workspace_id = NULLIF(current_setting('app.workspace_id',true),'')::uuid);
