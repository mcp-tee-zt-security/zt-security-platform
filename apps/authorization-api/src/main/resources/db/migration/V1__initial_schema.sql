-- ============================================================================
-- Zero Trust Security Platform 4.75 - Squashed Initial Schema
-- Generated from the original V1..V150 migration chain.
-- Canonical fresh-install baseline. Existing development databases should be reset.
-- ============================================================================

-- ============================================================================
-- BEGIN V1__init.sql
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE tenants (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), slug VARCHAR(100) NOT NULL UNIQUE,
name VARCHAR(255) NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE identities (id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
tenant_id UUID NOT NULL REFERENCES tenants(id), external_id VARCHAR(255) NOT NULL,
identity_type VARCHAR(30) NOT NULL, name VARCHAR(255), attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
UNIQUE(tenant_id,external_id));
CREATE TABLE resources (id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
tenant_id UUID NOT NULL REFERENCES tenants(id), resource_type VARCHAR(100) NOT NULL,
resource_id VARCHAR(255) NOT NULL, attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,resource_type,
resource_id));
CREATE TABLE policies (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
name VARCHAR(255) NOT NULL, version INTEGER NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
priority INTEGER NOT NULL DEFAULT 100, effect VARCHAR(20) NOT NULL, policy_text TEXT NOT NULL,
policy_json JSONB, created_by VARCHAR(255), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,name,version));
CREATE TABLE audit_logs (id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
tenant_id UUID NOT NULL REFERENCES tenants(id), request_id UUID NOT NULL,
identity_id UUID, identity_type VARCHAR(30), action VARCHAR(255) NOT NULL,
resource_type VARCHAR(100), resource_id VARCHAR(255), decision VARCHAR(30) NOT NULL,
policy_id UUID, reason TEXT, risk_score NUMERIC(5,2), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
previous_hash VARCHAR(128), event_hash VARCHAR(128), created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE api_keys (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
key_prefix VARCHAR(20) NOT NULL, key_hash VARCHAR(128) NOT NULL UNIQUE,
name VARCHAR(255) NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
created_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_used_at TIMESTAMPTZ);
CREATE INDEX idx_identity_tenant ON identities(tenant_id);
CREATE INDEX idx_resource_tenant ON resources(tenant_id);
CREATE INDEX idx_policy_tenant_status ON policies(tenant_id,status,priority);
CREATE INDEX idx_audit_tenant_created ON audit_logs(tenant_id,created_at DESC);
CREATE INDEX idx_audit_request ON audit_logs(request_id);
CREATE INDEX idx_audit_identity ON audit_logs(identity_id);
INSERT INTO tenants(id,slug,name) VALUES ('11111111-1111-1111-1111-111111111111',
'demo','Demo Tenant') ON CONFLICT (slug) DO NOTHING;

-- END V1__init.sql

-- ============================================================================
-- BEGIN V2__enterprise_security.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS approvals (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id), request_id UUID NOT NULL,
 approver_id VARCHAR(255), status VARCHAR(30) NOT NULL DEFAULT 'PENDING', reason TEXT,
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), decided_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_approvals_tenant_created ON approvals(tenant_id,created_at DESC);
ALTER TABLE policies ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE policies ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE policies ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Defense-in-depth tenant isolation. Application transactions should set app.tenant_id.
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE identities ENABLE ROW LEVEL SECURITY;
ALTER TABLE resources ENABLE ROW LEVEL SECURITY;
ALTER TABLE policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE approvals ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_policies ON policies;
CREATE POLICY tenant_isolation_policies ON policies USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
DROP POLICY IF EXISTS tenant_isolation_identities ON identities;
CREATE POLICY tenant_isolation_identities ON identities USING (tenant_id = current_setting('app.tenant_id',
true)::uuid);
DROP POLICY IF EXISTS tenant_isolation_resources ON resources;
CREATE POLICY tenant_isolation_resources ON resources USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
DROP POLICY IF EXISTS tenant_isolation_audit ON audit_logs;
CREATE POLICY tenant_isolation_audit ON audit_logs USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
DROP POLICY IF EXISTS tenant_isolation_approvals ON approvals;
CREATE POLICY tenant_isolation_approvals ON approvals USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

CREATE OR REPLACE FUNCTION deny_audit_mutation() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION
'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS audit_no_update ON audit_logs;
DROP TRIGGER IF EXISTS audit_no_delete ON audit_logs;
CREATE TRIGGER audit_no_update BEFORE UPDATE ON audit_logs FOR EACH ROW EXECUTE FUNCTION deny_audit_mutation();
CREATE TRIGGER audit_no_delete BEFORE DELETE ON audit_logs FOR EACH ROW EXECUTE FUNCTION deny_audit_mutation();

CREATE OR REPLACE FUNCTION audit_hash_guard() RETURNS trigger AS $$ BEGIN
 IF NEW.event_hash IS NULL OR NEW.event_hash !~ '^[0-9a-f]{64}$' THEN RAISE EXCEPTION 'invalid audit event hash';
 END IF;
 RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS audit_hash_guard ON audit_logs;
CREATE TRIGGER audit_hash_guard BEFORE INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION audit_hash_guard();

ALTER TABLE tenants FORCE ROW LEVEL SECURITY;
ALTER TABLE identities FORCE ROW LEVEL SECURITY;
ALTER TABLE resources FORCE ROW LEVEL SECURITY;
ALTER TABLE policies FORCE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;
ALTER TABLE approvals FORCE ROW LEVEL SECURITY;

-- END V2__enterprise_security.sql

-- ============================================================================
-- BEGIN V3__production_security.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS roles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  name VARCHAR(100) NOT NULL,
  description TEXT,
  UNIQUE(tenant_id,name)
);
CREATE TABLE IF NOT EXISTS role_bindings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  identity_id UUID NOT NULL REFERENCES identities(id),
  role_id UUID NOT NULL REFERENCES roles(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(tenant_id,identity_id,role_id)
);
CREATE TABLE IF NOT EXISTS agent_tasks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  agent_identity_id UUID NOT NULL REFERENCES identities(id),
  external_task_id VARCHAR(255) NOT NULL,
  purpose TEXT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  expires_at TIMESTAMPTZ,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(tenant_id,external_task_id)
);
CREATE TABLE IF NOT EXISTS agent_tools (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  name VARCHAR(255) NOT NULL,
  description TEXT,
  risk_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  UNIQUE(tenant_id,name)
);
CREATE TABLE IF NOT EXISTS task_tools (
  task_id UUID NOT NULL REFERENCES agent_tasks(id) ON DELETE CASCADE,
  tool_id UUID NOT NULL REFERENCES agent_tools(id) ON DELETE CASCADE,
  PRIMARY KEY(task_id,tool_id)
);
CREATE TABLE IF NOT EXISTS action_replays (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  source_audit_id UUID NOT NULL REFERENCES audit_logs(id),
  policy_set VARCHAR(255) NOT NULL,
  decision VARCHAR(30) NOT NULL,
  reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS api_keys (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  key_hash VARCHAR(128) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
  expires_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_role_binding_identity ON role_bindings(tenant_id,identity_id);
CREATE INDEX IF NOT EXISTS idx_agent_tasks_agent ON agent_tasks(tenant_id,agent_identity_id);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_time ON audit_logs(tenant_id,created_at DESC);

ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;
ALTER TABLE role_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_bindings FORCE ROW LEVEL SECURITY;
ALTER TABLE agent_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_tasks FORCE ROW LEVEL SECURITY;
ALTER TABLE agent_tools ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_tools FORCE ROW LEVEL SECURITY;
ALTER TABLE task_tools ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_tools FORCE ROW LEVEL SECURITY;
ALTER TABLE action_replays ENABLE ROW LEVEL SECURITY;
ALTER TABLE action_replays FORCE ROW LEVEL SECURITY;
ALTER TABLE api_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_keys FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_roles ON roles;
CREATE POLICY tenant_roles ON roles USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
DROP POLICY IF EXISTS tenant_role_bindings ON role_bindings;
CREATE POLICY tenant_role_bindings ON role_bindings USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
DROP POLICY IF EXISTS tenant_agent_tasks ON agent_tasks;
CREATE POLICY tenant_agent_tasks ON agent_tasks USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
DROP POLICY IF EXISTS tenant_agent_tools ON agent_tools;
CREATE POLICY tenant_agent_tools ON agent_tools USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
DROP POLICY IF EXISTS tenant_task_tools ON task_tools;
CREATE POLICY tenant_task_tools ON task_tools USING (EXISTS (SELECT 1 FROM agent_tasks t WHERE
t.id=task_id AND t.tenant_id=NULLIF(current_setting('app.tenant_id',
true),'')::uuid));
DROP POLICY IF EXISTS tenant_action_replays ON action_replays;
CREATE POLICY tenant_action_replays ON action_replays USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
DROP POLICY IF EXISTS tenant_api_keys ON api_keys;
CREATE POLICY tenant_api_keys ON api_keys USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

CREATE OR REPLACE FUNCTION prevent_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'audit_logs is append-only';
END;
$$;
DROP TRIGGER IF EXISTS audit_immutable_update ON audit_logs;
CREATE TRIGGER audit_immutable_update BEFORE UPDATE OR DELETE ON audit_logs FOR EACH ROW EXECUTE FUNCTION
prevent_audit_mutation();

-- END V3__production_security.sql

-- ============================================================================
-- BEGIN V4__governance_and_integrations.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS idempotency_keys (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  idempotency_key VARCHAR(255) NOT NULL,
  request_hash VARCHAR(128) NOT NULL,
  response_json JSONB,
  status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL DEFAULT now() + interval '24 hours',
  UNIQUE(tenant_id,idempotency_key)
);
CREATE TABLE IF NOT EXISTS siem_sinks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  name VARCHAR(255) NOT NULL,
  endpoint TEXT NOT NULL,
  secret_ref VARCHAR(255),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  event_types JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(tenant_id,name)
);
CREATE TABLE IF NOT EXISTS policy_blast_radius (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  policy_id UUID NOT NULL REFERENCES policies(id),
  affected_actions JSONB NOT NULL DEFAULT '[]'::jsonb,
  affected_resource_types JSONB NOT NULL DEFAULT '[]'::jsonb,
  affected_identities JSONB NOT NULL DEFAULT '[]'::jsonb,
  risk_level VARCHAR(20) NOT NULL,
  calculated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_idempotency_tenant_key ON idempotency_keys(tenant_id,idempotency_key);
CREATE INDEX IF NOT EXISTS idx_siem_tenant ON siem_sinks(tenant_id);
CREATE INDEX IF NOT EXISTS idx_blast_policy ON policy_blast_radius(tenant_id,policy_id);
ALTER TABLE idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys FORCE ROW LEVEL SECURITY;
ALTER TABLE siem_sinks ENABLE ROW LEVEL SECURITY;
ALTER TABLE siem_sinks FORCE ROW LEVEL SECURITY;
ALTER TABLE policy_blast_radius ENABLE ROW LEVEL SECURITY;
ALTER TABLE policy_blast_radius FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_idempotency ON idempotency_keys USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
CREATE POLICY tenant_siem_sinks ON siem_sinks USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
CREATE POLICY tenant_blast_radius ON policy_blast_radius USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

-- END V4__governance_and_integrations.sql

-- ============================================================================
-- BEGIN V5__policy_lifecycle_and_agent_behavior.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS policy_change_requests (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 policy_id UUID REFERENCES policies(id), policy_name VARCHAR(255) NOT NULL, version INTEGER NOT NULL,
 operation VARCHAR(30) NOT NULL, source VARCHAR(30) NOT NULL DEFAULT 'API', source_ref VARCHAR(500),
 policy_text TEXT NOT NULL, requested_by VARCHAR(255), status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
 required_approvals INTEGER NOT NULL DEFAULT 1, approvals INTEGER NOT NULL DEFAULT 0,
 simulation_json JSONB NOT NULL DEFAULT '{}'::jsonb, blast_radius_json JSONB NOT NULL DEFAULT '{}'::jsonb,
 canary_percent INTEGER NOT NULL DEFAULT 0, commit_sha VARCHAR(100), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 approved_at TIMESTAMPTZ, published_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS policy_deployments (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 policy_id UUID NOT NULL REFERENCES policies(id), policy_name VARCHAR(255) NOT NULL, version INTEGER NOT NULL,
 stage VARCHAR(30) NOT NULL, canary_percent INTEGER NOT NULL DEFAULT 0, status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
 commit_sha VARCHAR(100), started_at TIMESTAMPTZ NOT NULL DEFAULT now(), completed_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS gitops_sources (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 name VARCHAR(255) NOT NULL, repository VARCHAR(500) NOT NULL, branch VARCHAR(255) NOT NULL DEFAULT 'main',
 path VARCHAR(500) NOT NULL DEFAULT 'policy', webhook_secret VARCHAR(255), enabled BOOLEAN NOT NULL DEFAULT TRUE,
 last_commit_sha VARCHAR(100), last_synced_at TIMESTAMPTZ, UNIQUE(tenant_id,name)
);
CREATE TABLE IF NOT EXISTS agent_behavior_profiles (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 agent_external_id VARCHAR(255) NOT NULL, max_actions_per_minute INTEGER NOT NULL DEFAULT 60,
 max_risk_score NUMERIC(5,2) NOT NULL DEFAULT 70, max_amount NUMERIC(20,2),
 allowed_actions JSONB NOT NULL DEFAULT '[]'::jsonb, allowed_resource_types JSONB NOT NULL DEFAULT '[]'::jsonb,
 deny_burst_threshold INTEGER NOT NULL DEFAULT 5, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,agent_external_id)
);
CREATE TABLE IF NOT EXISTS agent_behavior_events (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 agent_external_id VARCHAR(255) NOT NULL, request_id UUID NOT NULL, action VARCHAR(255) NOT NULL,
 resource_type VARCHAR(100), decision VARCHAR(30) NOT NULL, risk_score NUMERIC(5,2), amount NUMERIC(20,2),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS agent_anomalies (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 agent_external_id VARCHAR(255) NOT NULL, request_id UUID NOT NULL, anomaly_type VARCHAR(80) NOT NULL,
 severity VARCHAR(20) NOT NULL, score NUMERIC(5,2) NOT NULL, reason TEXT NOT NULL,
 evidence JSONB NOT NULL DEFAULT '{}'::jsonb, status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_pcr_tenant_status ON policy_change_requests(tenant_id,status,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pd_tenant_policy ON policy_deployments(tenant_id,policy_name,status);
CREATE INDEX IF NOT EXISTS idx_behavior_event_agent_time ON agent_behavior_events(tenant_id,
agent_external_id,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_anomaly_agent_time ON agent_anomalies(tenant_id,agent_external_id,created_at DESC);
ALTER TABLE policy_change_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE policy_change_requests FORCE
ROW LEVEL SECURITY;
ALTER TABLE policy_deployments ENABLE ROW LEVEL SECURITY;
ALTER TABLE policy_deployments FORCE ROW LEVEL SECURITY;
ALTER TABLE gitops_sources ENABLE ROW LEVEL SECURITY;
ALTER TABLE gitops_sources FORCE ROW LEVEL SECURITY;
ALTER TABLE agent_behavior_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_behavior_profiles FORCE
ROW LEVEL SECURITY;
ALTER TABLE agent_behavior_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_behavior_events FORCE ROW LEVEL SECURITY;
ALTER TABLE agent_anomalies ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_anomalies FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_pcr ON policy_change_requests USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
CREATE POLICY tenant_pd ON policy_deployments USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
CREATE POLICY tenant_gitops ON gitops_sources USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
CREATE POLICY tenant_behavior_profiles ON agent_behavior_profiles USING (tenant_id =
NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
CREATE POLICY tenant_behavior_events ON agent_behavior_events USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
CREATE POLICY tenant_anomalies ON agent_anomalies USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

-- END V5__policy_lifecycle_and_agent_behavior.sql

-- ============================================================================
-- BEGIN V6__adaptive_agent_behavior.sql
-- ============================================================================
ALTER TABLE agent_behavior_profiles ADD COLUMN IF NOT EXISTS peer_group VARCHAR(120) NOT NULL DEFAULT 'default';
ALTER TABLE agent_behavior_profiles ADD COLUMN IF NOT EXISTS baseline_window_days INTEGER NOT NULL DEFAULT 7;
ALTER TABLE agent_behavior_profiles ADD COLUMN IF NOT EXISTS adaptive_step_up_score NUMERIC(5,2) NOT NULL DEFAULT 65;
ALTER TABLE agent_behavior_profiles ADD COLUMN IF NOT EXISTS adaptive_deny_score NUMERIC(5,2) NOT NULL DEFAULT 85;

CREATE TABLE IF NOT EXISTS agent_adaptive_decisions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 agent_external_id VARCHAR(255) NOT NULL, request_id UUID NOT NULL, peer_group VARCHAR(120),
 behavior_score NUMERIC(5,2) NOT NULL, baseline_score NUMERIC(5,2) NOT NULL DEFAULT 0,
 sequence_score NUMERIC(5,2) NOT NULL DEFAULT 0, peer_score NUMERIC(5,2) NOT NULL DEFAULT 0,
 decision VARCHAR(30) NOT NULL, signals JSONB NOT NULL DEFAULT '[]'::jsonb,
 evidence JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_adaptive_agent_time ON agent_adaptive_decisions(tenant_id,
agent_external_id,created_at DESC);
ALTER TABLE agent_adaptive_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_adaptive_decisions
FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_adaptive_decisions ON agent_adaptive_decisions USING (tenant_id =
NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

-- END V6__adaptive_agent_behavior.sql

-- ============================================================================
-- BEGIN V7__security_graph.sql
-- ============================================================================
ALTER TABLE agent_behavior_events ADD COLUMN IF NOT EXISTS tool_id UUID;
CREATE INDEX IF NOT EXISTS idx_agent_behavior_events_tenant_tool_time ON agent_behavior_events(tenant_id,
tool_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_behavior_events_tenant_action_time ON agent_behavior_events(tenant_id,
action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_behavior_events_tenant_resource_time ON agent_behavior_events(tenant_id,
resource_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_anomalies_tenant_time ON agent_anomalies(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_adaptive_decisions_tenant_time ON agent_adaptive_decisions(tenant_id,
created_at DESC);

CREATE TABLE IF NOT EXISTS security_graph_snapshots (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 node_count INTEGER NOT NULL DEFAULT 0,
 edge_count INTEGER NOT NULL DEFAULT 0,
 max_risk NUMERIC(5,2) NOT NULL DEFAULT 0,
 graph JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_security_graph_snapshots_tenant_time ON security_graph_snapshots(tenant_id,
generated_at DESC);
ALTER TABLE security_graph_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_graph_snapshots FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_security_graph_snapshots ON security_graph_snapshots
 USING (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid)
 WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid);

-- END V7__security_graph.sql

-- ============================================================================
-- BEGIN V8__attack_path_and_blast_radius.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS attack_path_assessments (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 source_node VARCHAR(255) NOT NULL,
 source_type VARCHAR(64) NOT NULL,
 scenario VARCHAR(64) NOT NULL DEFAULT 'COMPROMISED_AGENT',
 risk_score NUMERIC(5,2) NOT NULL DEFAULT 0,
 severity VARCHAR(32) NOT NULL DEFAULT 'LOW',
 reachable_resources INTEGER NOT NULL DEFAULT 0,
 blocked_paths INTEGER NOT NULL DEFAULT 0,
 critical_paths INTEGER NOT NULL DEFAULT 0,
 max_depth INTEGER NOT NULL DEFAULT 0,
 generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 result JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_attack_path_assessments_tenant_time ON attack_path_assessments(tenant_id,
generated_at DESC);
CREATE INDEX IF NOT EXISTS idx_attack_path_assessments_source ON attack_path_assessments(tenant_id,
source_node, generated_at DESC);
ALTER TABLE attack_path_assessments ENABLE ROW LEVEL SECURITY;
ALTER TABLE attack_path_assessments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_attack_path_assessments ON attack_path_assessments
 USING (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid)
 WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid);

-- END V8__attack_path_and_blast_radius.sql

-- ============================================================================
-- BEGIN V9__continuous_security_decision_engine.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS security_assets (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 resource_type VARCHAR(128) NOT NULL,
 resource_id VARCHAR(255) NOT NULL,
 criticality INTEGER NOT NULL DEFAULT 50 CHECK (criticality BETWEEN 0 AND 100),
 data_classification VARCHAR(64) NOT NULL DEFAULT 'INTERNAL',
 owner VARCHAR(255),
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id, resource_type, resource_id)
);
CREATE INDEX IF NOT EXISTS idx_security_assets_tenant ON security_assets(tenant_id, resource_type, resource_id);
ALTER TABLE security_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_assets FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_security_assets ON security_assets USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

CREATE TABLE IF NOT EXISTS security_decisions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 request_id UUID NOT NULL,
 principal_id VARCHAR(255) NOT NULL,
 principal_type VARCHAR(64) NOT NULL,
 action VARCHAR(255) NOT NULL,
 resource_type VARCHAR(128) NOT NULL,
 resource_id VARCHAR(255) NOT NULL,
 base_risk NUMERIC(5,2) NOT NULL DEFAULT 0,
 behavior_risk NUMERIC(5,2) NOT NULL DEFAULT 0,
 asset_criticality NUMERIC(5,2) NOT NULL DEFAULT 0,
 attack_path_risk NUMERIC(5,2) NOT NULL DEFAULT 0,
 policy_risk NUMERIC(5,2) NOT NULL DEFAULT 0,
 composite_score NUMERIC(5,2) NOT NULL DEFAULT 0,
 decision VARCHAR(32) NOT NULL,
 reason TEXT,
 signals JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_security_decisions_tenant_time ON security_decisions(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_security_decisions_request ON security_decisions(tenant_id, request_id);
ALTER TABLE security_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_decisions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_security_decisions ON security_decisions USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

-- END V9__continuous_security_decision_engine.sql

-- ============================================================================
-- BEGIN V10__product_ga_explainability_incidents.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS security_incidents (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 title VARCHAR(255) NOT NULL,
 severity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
 status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
 principal_id VARCHAR(255),
 source_decision_id UUID,
 summary TEXT,
 evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_security_incidents_tenant_time ON security_incidents(tenant_id, created_at DESC);
ALTER TABLE security_incidents ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_incidents FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_security_incidents ON security_incidents USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

CREATE TABLE IF NOT EXISTS security_decision_evidence (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 decision_id UUID NOT NULL REFERENCES security_decisions(id),
 category VARCHAR(64) NOT NULL,
 signal VARCHAR(128) NOT NULL,
 weight NUMERIC(6,3) NOT NULL DEFAULT 0,
 score NUMERIC(6,2) NOT NULL DEFAULT 0,
 explanation TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_decision_evidence_decision ON security_decision_evidence(tenant_id, decision_id);
ALTER TABLE security_decision_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_decision_evidence FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_security_decision_evidence ON security_decision_evidence USING (tenant_id =
NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

-- END V10__product_ga_explainability_incidents.sql

-- ============================================================================
-- BEGIN V11__demo_environment.sql
-- ============================================================================
-- 1.1 Demo Environment
-- Safe, synthetic data only. All values belong to the built-in demo tenant.
DO $$
DECLARE
  t UUID := '11111111-1111-1111-1111-111111111111';
  pay UUID := '22222222-2222-2222-2222-222222222201';
  refund UUID := '22222222-2222-2222-2222-222222222202';
  tool_transfer UUID := '33333333-3333-3333-3333-333333333301';
  tool_refund UUID := '33333333-3333-3333-3333-333333333302';
  task_pay UUID := '44444444-4444-4444-4444-444444444401';
  task_refund UUID := '44444444-4444-4444-4444-444444444402';
  p1 UUID := '55555555-5555-5555-5555-555555555501';
  p2 UUID := '55555555-5555-5555-5555-555555555502';
  d1 UUID := '66666666-6666-6666-6666-666666666601';
  d2 UUID := '66666666-6666-6666-6666-666666666602';
BEGIN
  INSERT INTO identities(id,tenant_id,external_id,identity_type,name,attributes) VALUES
    (pay,t,'payment-agent','AI_AGENT','Payment Agent','{"team":"payments","model":"demo-agent"}'::jsonb),
    (refund,t,'refund-agent','AI_AGENT','Customer Refund Agent','{"team":"customer-ops","model":"demo-agent"}'::jsonb)
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO agent_tools(id,tenant_id,name,description,risk_level,metadata) VALUES
    (tool_transfer,t,'payment.transfer','Transfer funds to a bank account','CRITICAL','{"domain":"payments"}'::jsonb),
    (tool_refund,t,'refund.create','Create a customer refund','HIGH','{"domain":"customer-ops"}'::jsonb)
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO agent_tasks(id,tenant_id,agent_identity_id,external_task_id,purpose,status,expires_at) VALUES
    (task_pay,t,pay,'payment-demo-task','Process approved supplier payments','ACTIVE',now()+interval '7 days'),
    (task_refund,t,refund,'refund-demo-task','Process customer refunds under policy','ACTIVE',now()+interval '7 days')
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO task_tools(task_id,tool_id) VALUES (task_pay,tool_transfer),
  (task_refund,tool_refund) ON CONFLICT DO NOTHING;

  INSERT INTO agent_behavior_profiles(id,tenant_id,agent_external_id,max_actions_per_minute,
  max_risk_score,max_amount,allowed_actions,allowed_resource_types,deny_burst_threshold,
  enabled,peer_group,baseline_window_days,adaptive_step_up_score,adaptive_deny_score)
  VALUES
    (gen_random_uuid(),t,'payment-agent',60,70,5000000,'["payment.transfer","payment.lookup"]',
    '["bank_account","payment_account"]',5,true,'payments',7,65,85),
    (gen_random_uuid(),t,'refund-agent',45,70,1000000,'["refund.create","order.lookup"]',
    '["order","customer_record"]',5,true,'customer-ops',7,65,85)
  ON CONFLICT (tenant_id,agent_external_id) DO UPDATE SET max_amount=EXCLUDED.max_amount,peer_group=EXCLUDED.peer_group;

  INSERT INTO policies(id,tenant_id,name,version,status,priority,effect,policy_text,created_by)
  VALUES
    (p1,t,'payment_guard',1,'ACTIVE',10,'DENY',$policy$policy "payment_guard" {
 effect deny
 principal.type == "AI_AGENT"
 action == "payment.transfer"
 condition {
  context.amount > 10000000
 }
}$policy$,'demo'),
    (p2,t,'refund_step_up',1,'ACTIVE',20,'STEP_UP',$policy$policy "refund_step_up" {
 effect step_up
 principal.type == "AI_AGENT"
 action == "refund.create"
 condition {
  context.amount > 500000
 }
}$policy$,'demo')
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO security_assets(tenant_id,resource_type,resource_id,criticality,data_classification,owner,metadata) VALUES
    (t,'bank_account','ACC-1001',95,'FINANCIAL','Treasury','{"currency":"KRW"}'::jsonb),
    (t,'production_db','prod-orders',95,'CONFIDENTIAL','Platform','{"environment":"production"}'::jsonb),
    (t,'customer_record','CUST-1001',85,'PII','Customer Ops','{}'::jsonb),
    (t,'order','ORDER-1001',60,'INTERNAL','Commerce','{}'::jsonb)
  ON CONFLICT (tenant_id,resource_type,resource_id) DO UPDATE SET criticality=EXCLUDED.criticality;

  INSERT INTO agent_behavior_events(tenant_id,agent_external_id,request_id,
  action,resource_type,decision,risk_score,amount,created_at,tool_id) VALUES
    (t,'payment-agent',gen_random_uuid(),'payment.lookup','bank_account',
    'ALLOW',18,250000,now()-interval '55 minutes',tool_transfer),
    (t,'payment-agent',gen_random_uuid(),'payment.transfer','bank_account',
    'ALLOW',25,300000,now()-interval '48 minutes',tool_transfer),
    (t,'payment-agent',gen_random_uuid(),'payment.transfer','bank_account',
    'ALLOW',31,420000,now()-interval '41 minutes',tool_transfer),
    (t,'payment-agent',gen_random_uuid(),'payment.transfer','bank_account',
    'DENY',88,15000000,now()-interval '8 minutes',tool_transfer),
    (t,'payment-agent',gen_random_uuid(),'payment.transfer','bank_account',
    'DENY',92,18000000,now()-interval '6 minutes',tool_transfer),
    (t,'refund-agent',gen_random_uuid(),'order.lookup','order','ALLOW',
    12,120000,now()-interval '35 minutes',tool_refund),
    (t,'refund-agent',gen_random_uuid(),'refund.create','customer_record',
    'STEP_UP',68,700000,now()-interval '5 minutes',tool_refund);

  INSERT INTO agent_anomalies(tenant_id,agent_external_id,request_id,anomaly_type,
  severity,score,reason,evidence,created_at)
  VALUES
    (t,'payment-agent',gen_random_uuid(),'BASELINE_AMOUNT_DEVIATION','CRITICAL',
88,'Transfer amount is far above the recent agent baseline',
'{"baselineAmount":350000,"observedAmount":15000000,"zScore":8.2}'::jsonb,
    now()-interval '8 minutes'),
    (t,'payment-agent',gen_random_uuid(),'SEQUENCE_ANOMALY','HIGH',78,
    'Repeated denied transfers followed by a high-value transfer attempt',
    '{"recentSequence":["payment.transfer","payment.transfer","payment.transfer"]}'::jsonb,
    now()-interval '6 minutes'),
    (t,'payment-agent',gen_random_uuid(),'PEER_DEVIATION','HIGH',74,'Agent behavior is materially above its peer group',
    '{"peerMedianAmount":300000,"observedAmount":18000000}'::jsonb,now()-interval '6 minutes');

  INSERT INTO agent_adaptive_decisions(tenant_id,agent_external_id,request_id,
  peer_group,behavior_score,baseline_score,sequence_score,peer_score,decision,
  signals,evidence,created_at)
  VALUES
    (t,'payment-agent',gen_random_uuid(),'payments',86,88,78,74,'DENY',
'["BASELINE_AMOUNT_DEVIATION","SEQUENCE_ANOMALY","PEER_DEVIATION"]',
'{"message":"Synthetic demo high-risk behavior"}'::jsonb,
    now()-interval '6 minutes'),
    (t,'refund-agent',gen_random_uuid(),'customer-ops',68,55,40,45,'STEP_UP',
    '["AMOUNT_DEVIATION"]','{"message":"Synthetic demo step-up"}'::jsonb,now()-interval '5 minutes');

  INSERT INTO security_decisions(id,tenant_id,request_id,principal_id,
  principal_type,action,resource_type,resource_id,base_risk,behavior_risk,
  asset_criticality,attack_path_risk,policy_risk,composite_score,decision,
  reason,signals,created_at)
  VALUES
    (d1,t,gen_random_uuid(),'payment-agent','AI_AGENT','payment.transfer',
'bank_account','ACC-1001',60,86,95,91,100,85.7,'DENY','Continuous security decision: composite risk 85.7 exceeded deny threshold',
    '{"demo":true,"signals":["BASELINE_AMOUNT_DEVIATION","SEQUENCE_ANOMALY","PEER_DEVIATION"]}'::jsonb,
    now()-interval '6 minutes'),
    (d2,t,gen_random_uuid(),'refund-agent','AI_AGENT','refund.create',
'customer_record','CUST-1001',45,68,85,60,70,65.2,'STEP_UP',
'Continuous security decision: composite risk 65.2 requires human step-up',
    '{"demo":true,"signals":["AMOUNT_DEVIATION"]}'::jsonb,now()-interval '5 minutes');

  INSERT INTO security_decision_evidence(tenant_id,decision_id,category,signal,weight,score,explanation) VALUES
    (t,d1,'CONTINUOUS_DECISION','BASE_RISK',.25,60,'Base transaction risk contributed 15 points.'),
    (t,d1,'CONTINUOUS_DECISION','BEHAVIOR',.30,86,'Agent behavior anomaly contributed 25.8 points.'),
    (t,d1,'CONTINUOUS_DECISION','ASSET_CRITICALITY',.20,95,'Bank account criticality contributed 19 points.'),
    (t,d1,'CONTINUOUS_DECISION','ATTACK_PATH',.15,91,'Reachable critical path contributed 13.65 points.'),
    (t,d1,'CONTINUOUS_DECISION','POLICY',.10,100,'Policy DENY contributed 10 points.'),
    (t,d2,'CONTINUOUS_DECISION','BASE_RISK',.25,45,'Base transaction risk contributed 11.25 points.'),
    (t,d2,'CONTINUOUS_DECISION','BEHAVIOR',.30,68,'Behavior deviation contributed 20.4 points.'),
    (t,d2,'CONTINUOUS_DECISION','ASSET_CRITICALITY',.20,85,'Customer record criticality contributed 17 points.'),
    (t,d2,'CONTINUOUS_DECISION','ATTACK_PATH',.15,60,'Attack-path exposure contributed 9 points.'),
    (t,d2,'CONTINUOUS_DECISION','POLICY',.10,70,'Policy STEP_UP contributed 7 points.');

  INSERT INTO security_incidents(id,tenant_id,title,severity,status,principal_id,
  source_decision_id,summary,evidence,created_at,updated_at)
  VALUES
    ('77777777-7777-7777-7777-777777777701',t,'Payment Agent high-risk transfer',
'CRITICAL','OPEN','payment-agent',d1,'Synthetic demo incident: compromised-agent simulation indicates a critical payment path.',
    '[{"type":"BASELINE_AMOUNT_DEVIATION","score":88},{"type":"PEER_DEVIATION","score":74}]'::jsonb,
    now()-interval '6 minutes',now()-interval '6 minutes'),
    ('77777777-7777-7777-7777-777777777702',t,'Refund Agent step-up required',
    'HIGH','OPEN','refund-agent',d2,'Synthetic demo incident: refund amount exceeded adaptive step-up threshold.',
    '[{"type":"AMOUNT_DEVIATION","score":68}]'::jsonb,now()-interval '5 minutes',
    now()-interval '5 minutes')
  ON CONFLICT (id) DO NOTHING;
END $$;

-- END V11__demo_environment.sql

-- ============================================================================
-- BEGIN V12__security_core_multi_tenant_workspace.sql
-- ============================================================================
-- 1.2 Security Core + Multi-Tenant workspace boundary
CREATE TABLE IF NOT EXISTS workspaces (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 slug VARCHAR(100) NOT NULL,
 name VARCHAR(255) NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id, slug)
);
CREATE INDEX IF NOT EXISTS idx_workspaces_tenant ON workspaces(tenant_id);
ALTER TABLE workspaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE workspaces FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_workspaces ON workspaces;
CREATE POLICY tenant_workspaces ON workspaces USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

ALTER TABLE role_bindings ADD COLUMN IF NOT EXISTS workspace_id UUID REFERENCES workspaces(id);
CREATE INDEX IF NOT EXISTS idx_role_bindings_workspace ON role_bindings(tenant_id,workspace_id,identity_id);

-- Workspace-aware policy scope. NULL keeps legacy tenant-wide policies working.
ALTER TABLE policies ADD COLUMN IF NOT EXISTS workspace_id UUID REFERENCES workspaces(id);
CREATE INDEX IF NOT EXISTS idx_policies_workspace ON policies(tenant_id,workspace_id,status);
DROP POLICY IF EXISTS tenant_isolation_policies ON policies;
CREATE POLICY tenant_isolation_policies ON policies USING (
 tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid
 AND (workspace_id IS NULL OR current_setting('app.workspace_id',true) = '' OR workspace_id =
NULLIF(current_setting('app.workspace_id',
 true),'')::uuid)
) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid);

-- Demo workspace for the built-in tenant.
INSERT INTO workspaces(id,tenant_id,slug,name,status)
VALUES ('88888888-8888-8888-8888-888888888801','11111111-1111-1111-1111-111111111111',
'security-demo','Security Demo','ACTIVE')
ON CONFLICT DO NOTHING;
UPDATE policies SET workspace_id='88888888-8888-8888-8888-888888888801'
WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND workspace_id IS NULL;
UPDATE role_bindings SET workspace_id='88888888-8888-8888-8888-888888888801'
WHERE tenant_id='11111111-1111-1111-1111-111111111111' AND workspace_id IS NULL;

-- Cache/incremental metadata. This is deliberately small: graph snapshots remain source-of-truth in PostgreSQL.
CREATE TABLE IF NOT EXISTS security_graph_versions (
 tenant_id UUID PRIMARY KEY REFERENCES tenants(id),
 version BIGINT NOT NULL DEFAULT 0,
 last_fingerprint VARCHAR(128),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE security_graph_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_graph_versions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_security_graph_versions ON security_graph_versions;
CREATE POLICY tenant_security_graph_versions ON security_graph_versions USING (tenant_id =
NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

-- END V12__security_core_multi_tenant_workspace.sql

-- ============================================================================
-- BEGIN V13__runtime_security_gateway.sql
-- ============================================================================
-- 1.3 AI Agent Runtime Security Gateway / Session model
CREATE TABLE IF NOT EXISTS agent_runtime_sessions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 workspace_id UUID REFERENCES workspaces(id),
 agent_external_id VARCHAR(255) NOT NULL,
 task_external_id VARCHAR(255),
 status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
 source VARCHAR(80) NOT NULL DEFAULT 'SDK',
 started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 ended_at TIMESTAMPTZ,
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_runtime_sessions_tenant_agent ON agent_runtime_sessions(tenant_id,
agent_external_id,status,last_seen_at DESC);
ALTER TABLE agent_runtime_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_runtime_sessions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_runtime_sessions ON agent_runtime_sessions;
CREATE POLICY tenant_runtime_sessions ON agent_runtime_sessions USING (tenant_id =
NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

CREATE TABLE IF NOT EXISTS agent_runtime_events (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 workspace_id UUID REFERENCES workspaces(id),
 session_id UUID NOT NULL REFERENCES agent_runtime_sessions(id),
 request_id UUID,
 agent_external_id VARCHAR(255) NOT NULL,
 task_external_id VARCHAR(255),
 tool_id VARCHAR(255),
 action VARCHAR(255) NOT NULL,
 resource_type VARCHAR(255) NOT NULL,
 resource_id VARCHAR(255) NOT NULL,
 decision VARCHAR(30) NOT NULL,
 risk_score NUMERIC(6,2),
 latency_ms BIGINT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 context JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_runtime_events_session ON agent_runtime_events(tenant_id,session_id,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_runtime_events_agent ON agent_runtime_events(tenant_id,
agent_external_id,created_at DESC);
ALTER TABLE agent_runtime_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_runtime_events FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_runtime_events ON agent_runtime_events;
CREATE POLICY tenant_runtime_events ON agent_runtime_events USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

INSERT INTO agent_runtime_sessions(id,tenant_id,workspace_id,agent_external_id,task_external_id,status,source,metadata)
VALUES ('99999999-9999-9999-9999-999999999901','11111111-1111-1111-1111-111111111111',
'88888888-8888-8888-8888-888888888801','payment-agent','payment-demo-task',
'ACTIVE','DEMO','{"scenario":"bank-payment"}'::jsonb)
ON CONFLICT DO NOTHING;

-- END V13__runtime_security_gateway.sql

-- ============================================================================
-- BEGIN V14__runtime_security_hardening.sql
-- ============================================================================
-- 1.4.0 Runtime Security Hardening
-- Workspace isolation is enforced when a runtime session is workspace-bound.
DROP POLICY IF EXISTS tenant_runtime_sessions ON agent_runtime_sessions;
CREATE POLICY tenant_runtime_sessions ON agent_runtime_sessions
USING (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid
  AND (workspace_id IS NULL OR workspace_id = NULLIF(current_setting('app.workspace_id',true),'')::uuid))
WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid
  AND (workspace_id IS NULL OR workspace_id = NULLIF(current_setting('app.workspace_id',true),'')::uuid));
DROP POLICY IF EXISTS tenant_runtime_events ON agent_runtime_events;
CREATE POLICY tenant_runtime_events ON agent_runtime_events
USING (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid
  AND (workspace_id IS NULL OR workspace_id = NULLIF(current_setting('app.workspace_id',true),'')::uuid))
WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid
  AND (workspace_id IS NULL OR workspace_id = NULLIF(current_setting('app.workspace_id',true),'')::uuid));
CREATE INDEX IF NOT EXISTS idx_runtime_sessions_workspace ON agent_runtime_sessions(tenant_id,
workspace_id,last_seen_at DESC);
CREATE INDEX IF NOT EXISTS idx_runtime_events_workspace ON agent_runtime_events(tenant_id,workspace_id,created_at DESC);

-- END V14__runtime_security_hardening.sql

-- ============================================================================
-- BEGIN V15__enterprise_sso_rbac_scim.sql
-- ============================================================================
-- 1.4 Enterprise Security: SSO metadata, directory, permissions and approval controls
ALTER TABLE roles ADD COLUMN IF NOT EXISTS permissions JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE approvals ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
ALTER TABLE approvals ADD COLUMN IF NOT EXISTS decided_by VARCHAR(255);
ALTER TABLE approvals ADD COLUMN IF NOT EXISTS approval_type VARCHAR(40) NOT NULL DEFAULT 'HUMAN';
ALTER TABLE approvals ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
CREATE INDEX IF NOT EXISTS idx_approvals_pending_expiry ON approvals(tenant_id,status,expires_at);

CREATE TABLE IF NOT EXISTS enterprise_users (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 external_id VARCHAR(255) NOT NULL,
 user_name VARCHAR(255) NOT NULL, display_name VARCHAR(255), email VARCHAR(320), active BOOLEAN NOT NULL DEFAULT true,
 source VARCHAR(30) NOT NULL DEFAULT 'LOCAL', attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,user_name), UNIQUE(tenant_id,external_id)
);
CREATE INDEX IF NOT EXISTS idx_enterprise_users_tenant ON enterprise_users(tenant_id,user_name);

CREATE TABLE IF NOT EXISTS enterprise_groups (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 external_id VARCHAR(255) NOT NULL,
 display_name VARCHAR(255) NOT NULL, active BOOLEAN NOT NULL DEFAULT true,
 attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,display_name), UNIQUE(tenant_id,external_id)
);
CREATE TABLE IF NOT EXISTS enterprise_group_members (
 tenant_id UUID NOT NULL REFERENCES tenants(id), group_id UUID NOT NULL REFERENCES enterprise_groups(id)
ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES enterprise_users(id) ON DELETE CASCADE, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(group_id,user_id)
);

CREATE TABLE IF NOT EXISTS sso_connections (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 provider VARCHAR(30) NOT NULL,
 issuer_uri TEXT NOT NULL, client_id VARCHAR(255), client_secret_ref VARCHAR(512),
 enabled BOOLEAN NOT NULL DEFAULT false,
 allowed_domains JSONB NOT NULL DEFAULT '[]'::jsonb, claims_mapping JSONB NOT NULL DEFAULT
'{"subject":"sub","email":"email","groups":"groups"}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,provider)
);

ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_roles ON roles;
CREATE POLICY tenant_roles ON roles USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
ALTER TABLE role_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_bindings FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_role_bindings ON role_bindings;
CREATE POLICY tenant_role_bindings ON role_bindings USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
ALTER TABLE enterprise_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE enterprise_users FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_enterprise_users ON enterprise_users USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
ALTER TABLE enterprise_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE enterprise_groups FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_enterprise_groups ON enterprise_groups USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
ALTER TABLE enterprise_group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE enterprise_group_members
FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_group_members ON enterprise_group_members USING (tenant_id =
NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);
ALTER TABLE sso_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE sso_connections FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_sso_connections ON sso_connections USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

-- Built-in enterprise roles. Permissions are explicit capabilities, not UI-only labels.
INSERT INTO roles(id,tenant_id,name,description,permissions)
VALUES
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1','11111111-1111-1111-1111-111111111111',
'SECURITY_ADMIN','Full security administration','["tenant.read","workspace.read","policy.read","policy.write","policy.publish","policy.approve","runtime.read","runtime.respond","audit.read","rbac.read","rbac.write","sso.read","sso.write","directory.read","directory.write"]'),
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2','11111111-1111-1111-1111-111111111111',
'POLICY_ADMIN','Policy authoring and lifecycle','["policy.read","policy.write","policy.publish","policy.approve","runtime.read","audit.read"]'),
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3','11111111-1111-1111-1111-111111111111',
'AUDITOR','Read-only security audit access','["tenant.read","workspace.read","policy.read","runtime.read","audit.read","rbac.read","sso.read","directory.read"]'),
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4','11111111-1111-1111-1111-111111111111',
 'APPROVER','Human approval access','["policy.read","policy.approve","runtime.read","audit.read"]')
ON CONFLICT (tenant_id,name) DO UPDATE SET permissions=EXCLUDED.permissions,description=EXCLUDED.description;

-- END V15__enterprise_sso_rbac_scim.sql

-- ============================================================================
-- BEGIN V16__commercial_product_foundation.sql
-- ============================================================================
-- 2.0 commercial product foundation
CREATE TABLE api_clients (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, workspace_id uuid, client_id varchar(120) NOT NULL,
 name varchar(200) NOT NULL, secret_hash varchar(128) NOT NULL, status varchar(32) NOT NULL DEFAULT 'ACTIVE',
 scopes jsonb NOT NULL DEFAULT '[]', last_used_at timestamptz, expires_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(tenant_id, client_id)
);
CREATE INDEX idx_api_clients_lookup ON api_clients(tenant_id, client_id, status);

CREATE TABLE webhooks (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, workspace_id uuid, name varchar(200) NOT NULL,
 url varchar(1000) NOT NULL, secret varchar(256) NOT NULL, event_types jsonb NOT NULL DEFAULT '[]',
 status varchar(32) NOT NULL DEFAULT 'ACTIVE', created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_webhooks_tenant ON webhooks(tenant_id, status);

CREATE TABLE webhook_deliveries (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, webhook_id uuid NOT NULL,
 event_id uuid NOT NULL, event_type varchar(120) NOT NULL, payload jsonb NOT NULL,
 status varchar(32) NOT NULL DEFAULT 'PENDING', attempts int NOT NULL DEFAULT 0,
 next_attempt_at timestamptz NOT NULL DEFAULT now(), last_error varchar(1000),
 delivered_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_webhook_deliveries_due ON webhook_deliveries(status, next_attempt_at);

CREATE TABLE security_event_outbox (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, workspace_id uuid, event_type varchar(120) NOT NULL,
 aggregate_type varchar(120), aggregate_id varchar(200), payload jsonb NOT NULL,
 status varchar(32) NOT NULL DEFAULT 'PENDING', attempts int NOT NULL DEFAULT 0,
 available_at timestamptz NOT NULL DEFAULT now(), published_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_event_outbox_due ON security_event_outbox(status, available_at);

CREATE TABLE tenant_settings (
 tenant_id uuid PRIMARY KEY, security_mode varchar(32) NOT NULL DEFAULT 'ENFORCED',
 default_rate_limit int NOT NULL DEFAULT 120, runtime_session_ttl_seconds int NOT NULL DEFAULT 3600,
 audit_retention_days int NOT NULL DEFAULT 365, webhook_enabled boolean NOT NULL DEFAULT true,
 updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE api_clients ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_clients FORCE ROW LEVEL SECURITY;
ALTER TABLE webhooks ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhooks FORCE ROW LEVEL SECURITY;
ALTER TABLE webhook_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhook_deliveries FORCE ROW LEVEL SECURITY;
ALTER TABLE security_event_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_event_outbox FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_settings FORCE ROW LEVEL SECURITY;
CREATE POLICY api_clients_tenant ON api_clients USING (tenant_id = current_setting('app.tenant_id',
true)::uuid) WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY webhooks_tenant ON webhooks USING (tenant_id = current_setting('app.tenant_id',
true)::uuid) WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY webhook_deliveries_tenant ON webhook_deliveries USING (tenant_id = current_setting('app.tenant_id',
true)::uuid) WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY event_outbox_tenant ON security_event_outbox USING (tenant_id = current_setting('app.tenant_id',
true)::uuid) WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_settings_tenant ON tenant_settings USING (tenant_id = current_setting('app.tenant_id',
true)::uuid) WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- END V16__commercial_product_foundation.sql

-- ============================================================================
-- BEGIN V17__runtime_security_intelligence.sql
-- ============================================================================
-- 2.3 Runtime Security Intelligence indexes and event taxonomy support
CREATE INDEX IF NOT EXISTS idx_runtime_events_agent_time ON agent_runtime_events(tenant_id,
agent_external_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_runtime_events_decision_time ON agent_runtime_events(tenant_id,
decision, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_anomalies_severity_time ON agent_anomalies(tenant_id, severity, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_adaptive_decisions_agent_time ON agent_adaptive_decisions(tenant_id,
agent_external_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_event_outbox_type_time ON security_event_outbox(tenant_id, event_type, created_at DESC);

-- END V17__runtime_security_intelligence.sql

-- ============================================================================
-- BEGIN V18__mcp_security_gateway.sql
-- ============================================================================
-- 2.4 MCP Security Gateway support
CREATE INDEX IF NOT EXISTS idx_agent_tools_tenant_risk ON agent_tools(tenant_id, risk_level, name);
CREATE INDEX IF NOT EXISTS idx_security_event_outbox_mcp ON security_event_outbox(tenant_id,
event_type, created_at DESC);

-- END V18__mcp_security_gateway.sql

-- ============================================================================
-- BEGIN V19__blast_radius_impact_analysis.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS blast_radius_assessments (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 source_node VARCHAR(255) NOT NULL,
 source_type VARCHAR(64) NOT NULL,
 risk_score NUMERIC(5,2) NOT NULL DEFAULT 0,
 severity VARCHAR(32) NOT NULL DEFAULT 'LOW',
 impacted_assets INTEGER NOT NULL DEFAULT 0,
 critical_assets INTEGER NOT NULL DEFAULT 0,
 restricted_assets INTEGER NOT NULL DEFAULT 0,
 recommended_actions INTEGER NOT NULL DEFAULT 0,
 generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 result JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_blast_radius_tenant_time ON blast_radius_assessments(tenant_id, generated_at DESC);
CREATE INDEX IF NOT EXISTS idx_blast_radius_source ON blast_radius_assessments(tenant_id,
source_node, generated_at DESC);
ALTER TABLE blast_radius_assessments ENABLE ROW LEVEL SECURITY;
ALTER TABLE blast_radius_assessments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_blast_radius_assessments ON blast_radius_assessments
 USING (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid)
 WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid);

-- END V19__blast_radius_impact_analysis.sql

-- ============================================================================
-- BEGIN V20__automated_security_response.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS security_response_actions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 assessment_id UUID NULL REFERENCES blast_radius_assessments(id),
 action_type VARCHAR(64) NOT NULL,
 target VARCHAR(255) NOT NULL,
 priority VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
 reason TEXT,
 requested_by VARCHAR(255) NOT NULL,
 approved_by VARCHAR(255),
 status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 requires_approval BOOLEAN NOT NULL DEFAULT TRUE,
 execution_result JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 approved_at TIMESTAMPTZ,
 executed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_security_response_tenant_status ON security_response_actions(tenant_id,
status,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_security_response_assessment ON security_response_actions(tenant_id,
assessment_id,created_at DESC);
ALTER TABLE security_response_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_response_actions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_security_response_actions ON security_response_actions
 USING (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid)
 WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',true),'')::uuid);

ALTER TABLE agent_tools ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX IF NOT EXISTS idx_agent_tools_enabled ON agent_tools(tenant_id,enabled);

-- END V20__automated_security_response.sql

-- ============================================================================
-- BEGIN V21__incident_response_and_evidence_cases.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS security_cases (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 title VARCHAR(255) NOT NULL,
 severity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM', status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
 source_type VARCHAR(64), source_id UUID,
 assigned_to VARCHAR(255), summary TEXT, external_ref VARCHAR(255), evidence_count INT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), closed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_security_cases_tenant_status ON security_cases(tenant_id,status,updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_security_cases_source ON security_cases(tenant_id,source_type,source_id);
ALTER TABLE security_cases ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_cases FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_security_cases ON security_cases USING (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

CREATE TABLE IF NOT EXISTS security_case_evidence (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 case_id UUID NOT NULL REFERENCES security_cases(id) ON DELETE CASCADE,
 evidence_type VARCHAR(64) NOT NULL, source_ref VARCHAR(255), payload JSONB NOT NULL DEFAULT '{}'::jsonb,
 content_hash VARCHAR(128) NOT NULL,
 created_by VARCHAR(255), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_case_evidence_case ON security_case_evidence(tenant_id,case_id,created_at ASC);
ALTER TABLE security_case_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_case_evidence FORCE
ROW LEVEL SECURITY;
CREATE POLICY tenant_security_case_evidence ON security_case_evidence USING (tenant_id =
NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

-- END V21__incident_response_and_evidence_cases.sql

-- ============================================================================
-- BEGIN V22__compliance_evidence_plane.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS compliance_assessments (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 framework VARCHAR(64) NOT NULL,
 period_start TIMESTAMPTZ NOT NULL, period_end TIMESTAMPTZ NOT NULL, status VARCHAR(32) NOT NULL,
 score NUMERIC(5,2) NOT NULL DEFAULT 0, controls JSONB NOT NULL DEFAULT '[]'::jsonb,
 evidence_count INT NOT NULL DEFAULT 0,
 report_hash VARCHAR(128) NOT NULL, generated_by VARCHAR(255), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_compliance_assessments_tenant ON compliance_assessments(tenant_id,created_at DESC);
ALTER TABLE compliance_assessments ENABLE ROW LEVEL SECURITY;
ALTER TABLE compliance_assessments FORCE
ROW LEVEL SECURITY;
CREATE POLICY tenant_compliance_assessments ON compliance_assessments USING (tenant_id =
NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

-- END V22__compliance_evidence_plane.sql

-- ============================================================================
-- BEGIN V23__policy_what_if_simulation.sql
-- ============================================================================
-- 3.11 What-if simulation is intentionally stateless: simulation must never mutate enforcement state.
-- This migration reserves the version boundary for future persisted simulation history without
-- creating a table that could be mistaken for an enforcement source of truth.
SELECT 1;

-- END V23__policy_what_if_simulation.sql

-- ============================================================================
-- BEGIN V24__shadow_replay.sql
-- ============================================================================
create table if not exists security_shadow_replay_jobs (
 id uuid primary key,
 tenant_id uuid not null,
 workspace_id uuid,
 mode varchar(32) not null,
 policy_name varchar(200),
 policy_hash varchar(64) not null,
 from_time timestamptz,
 to_time timestamptz,
 event_count integer not null default 0,
 changed_decisions integer not null default 0,
 new_denies integer not null default 0,
 new_step_ups integer not null default 0,
 false_positive_candidates integer not null default 0,
 security_improvement_pct double precision not null default 0,
 business_impact_score double precision not null default 0,
 result_hash varchar(64),
 status varchar(32) not null,
 created_at timestamptz not null default now()
);
create index if not exists idx_shadow_replay_tenant_created on security_shadow_replay_jobs(tenant_id,created_at desc);
alter table security_shadow_replay_jobs enable row level security;
alter table security_shadow_replay_jobs force row level security;

-- END V24__shadow_replay.sql

-- ============================================================================
-- BEGIN V25__continuous_agent_risk.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS agent_risk_snapshots (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL,
 workspace_id UUID,
 agent_external_id VARCHAR(255) NOT NULL,
 risk_score DOUBLE PRECISION NOT NULL,
 risk_state VARCHAR(32) NOT NULL,
 risk_direction VARCHAR(32) NOT NULL,
 risk_drift DOUBLE PRECISION NOT NULL DEFAULT 0,
 driver_summary JSONB NOT NULL DEFAULT '[]'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_agent_risk_snapshots_tenant_agent_time ON agent_risk_snapshots(tenant_id,
agent_external_id,created_at DESC);
ALTER TABLE agent_risk_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_risk_snapshots FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS agent_risk_snapshots_tenant_policy ON agent_risk_snapshots;
CREATE POLICY agent_risk_snapshots_tenant_policy ON agent_risk_snapshots USING (tenant_id::text =
current_setting('app.tenant_id',
true));

-- END V25__continuous_agent_risk.sql

-- ============================================================================
-- BEGIN V26__agent_risk_forecast.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS agent_risk_forecasts (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL,
 workspace_id UUID,
 agent_external_id VARCHAR(255) NOT NULL,
 window_hours INT NOT NULL,
 horizon_hours INT NOT NULL,
 current_score DOUBLE PRECISION NOT NULL,
 forecast_score DOUBLE PRECISION NOT NULL,
 high_probability DOUBLE PRECISION NOT NULL,
 confidence DOUBLE PRECISION NOT NULL,
 forecast_band VARCHAR(32) NOT NULL,
 recommendation VARCHAR(128) NOT NULL,
 drivers JSONB NOT NULL DEFAULT '[]'::jsonb,
 exposure_paths JSONB NOT NULL DEFAULT '[]'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_agent_risk_forecasts_tenant_agent_time ON agent_risk_forecasts(tenant_id,
agent_external_id,created_at DESC);
ALTER TABLE agent_risk_forecasts ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_risk_forecasts FORCE ROW LEVEL SECURITY;
CREATE POLICY agent_risk_forecasts_tenant_policy ON agent_risk_forecasts USING (tenant_id =
current_setting('app.tenant_id',
true)::uuid);

-- END V26__agent_risk_forecast.sql

-- ============================================================================
-- BEGIN V27__policy_control_plane_repair.sql
-- ============================================================================
ALTER TABLE policy_change_requests ADD COLUMN IF NOT EXISTS approved_by VARCHAR(255);
ALTER TABLE policy_change_requests ADD COLUMN IF NOT EXISTS approval_comment TEXT;
ALTER TABLE policy_change_requests ADD COLUMN IF NOT EXISTS diff_json JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE policy_deployments ADD COLUMN IF NOT EXISTS canary_started_at TIMESTAMPTZ;
ALTER TABLE policy_deployments ADD COLUMN IF NOT EXISTS canary_stopped_at TIMESTAMPTZ;
ALTER TABLE policy_deployments ADD COLUMN IF NOT EXISTS canary_reason TEXT;
ALTER TABLE policy_deployments ADD COLUMN IF NOT EXISTS auto_stop_threshold NUMERIC(5,2) NOT NULL DEFAULT 50;
CREATE INDEX IF NOT EXISTS idx_pd_tenant_stage_status ON policy_deployments(tenant_id,stage,status,started_at DESC);

-- END V27__policy_control_plane_repair.sql

-- ============================================================================
-- BEGIN V28__control_loop_feedback_repair.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS security_control_feedback (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL,
 response_id UUID NOT NULL,
 action_type VARCHAR(64) NOT NULL,
 target VARCHAR(255) NOT NULL,
 verification VARCHAR(32) NOT NULL,
 before_risk DOUBLE PRECISION,
 after_risk DOUBLE PRECISION,
 risk_delta DOUBLE PRECISION,
 before_deny_rate DOUBLE PRECISION,
 after_deny_rate DOUBLE PRECISION,
 deny_rate_delta DOUBLE PRECISION,
 observation_hours INTEGER NOT NULL,
 learning_summary TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_security_control_feedback_tenant_time ON security_control_feedback(tenant_id,
created_at DESC);
ALTER TABLE security_control_feedback ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_control_feedback FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS security_control_feedback_tenant_policy ON security_control_feedback;
CREATE POLICY security_control_feedback_tenant_policy ON security_control_feedback USING (tenant_id::text
= current_setting('app.tenant_id',
true));

-- END V28__control_loop_feedback_repair.sql

-- ============================================================================
-- BEGIN V31__preventive_security_control_loop.sql
-- ============================================================================
-- 3.15 Preventive Security Control Loop
-- Control-loop proposals reuse security_response_actions so approval/execution remains centralized.
-- This table records immutable planning metadata without granting autonomous enforcement.
CREATE TABLE IF NOT EXISTS security_control_loop_runs (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL,
 workspace_id UUID,
 agent_external_id VARCHAR(255) NOT NULL,
 forecast_score DOUBLE PRECISION NOT NULL,
 high_probability DOUBLE PRECISION NOT NULL,
 recommendation VARCHAR(64) NOT NULL,
 proposed_action VARCHAR(64) NOT NULL,
 response_action_id UUID,
 status VARCHAR(32) NOT NULL DEFAULT 'PROPOSED',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_control_loop_tenant_agent_time ON security_control_loop_runs(tenant_id,
agent_external_id,created_at DESC);
ALTER TABLE security_control_loop_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_control_loop_runs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS security_control_loop_tenant_policy ON security_control_loop_runs;
CREATE POLICY security_control_loop_tenant_policy ON security_control_loop_runs USING (tenant_id::text =
current_setting('app.tenant_id',
true));

-- END V31__preventive_security_control_loop.sql

-- ============================================================================
-- BEGIN V32__closed_loop_verification.sql
-- ============================================================================
-- 3.17 Closed-Loop Verification
-- Persist verification observations so control outcomes are durable, queryable and auditable.
ALTER TABLE security_control_feedback
  ADD COLUMN IF NOT EXISTS before_events INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS after_events INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS before_risk_samples INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS after_risk_samples INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS observation_started_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS observation_completed_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS evaluated_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS confidence DOUBLE PRECISION NOT NULL DEFAULT 0;

-- 3.16 did not persist feedback from the application, so this is normally empty.
-- Keep one durable verification record per response action when upgrading environments
-- that may already contain manually inserted duplicate rows.
DELETE FROM security_control_feedback a
WHERE a.id IN (
  SELECT id FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY tenant_id, response_id ORDER BY created_at DESC, id DESC) rn
    FROM security_control_feedback
  ) d WHERE d.rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_security_control_feedback_response
  ON security_control_feedback(tenant_id, response_id);

CREATE INDEX IF NOT EXISTS idx_security_control_feedback_verification
  ON security_control_feedback(tenant_id, verification, evaluated_at DESC);

ALTER TABLE security_control_feedback
  ADD CONSTRAINT security_control_feedback_response_fk
  FOREIGN KEY (response_id) REFERENCES security_response_actions(id);

ALTER TABLE security_control_feedback ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_control_feedback FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS security_control_feedback_tenant_policy ON security_control_feedback;
CREATE POLICY security_control_feedback_tenant_policy ON security_control_feedback
  USING (tenant_id::text = current_setting('app.tenant_id', true))
  WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

-- END V32__closed_loop_verification.sql

-- ============================================================================
-- BEGIN V33__commercial_foundation_repair.sql
-- ============================================================================
-- 3.17 compatibility repair for installations whose historical V16 was the commercial foundation.
CREATE TABLE IF NOT EXISTS api_clients (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, workspace_id uuid, client_id varchar(120) NOT NULL,
 name varchar(200) NOT NULL,
 secret_hash varchar(128) NOT NULL, status varchar(32) NOT NULL DEFAULT 'ACTIVE', scopes jsonb NOT NULL DEFAULT '[]',
 last_used_at timestamptz, expires_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant_id, client_id)
);
CREATE INDEX IF NOT EXISTS idx_api_clients_lookup ON api_clients(tenant_id, client_id, status);
CREATE TABLE IF NOT EXISTS webhooks (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, workspace_id uuid, name varchar(200) NOT NULL,
 url varchar(1000) NOT NULL,
 secret varchar(256) NOT NULL, event_types jsonb NOT NULL DEFAULT '[]', status varchar(32) NOT NULL DEFAULT 'ACTIVE',
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_webhooks_tenant ON webhooks(tenant_id, status);
CREATE TABLE IF NOT EXISTS webhook_deliveries (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, webhook_id uuid NOT NULL,
 event_id uuid NOT NULL, event_type varchar(120) NOT NULL,
 payload jsonb NOT NULL, status varchar(32) NOT NULL DEFAULT 'PENDING', attempts int NOT NULL DEFAULT 0,
 next_attempt_at timestamptz NOT NULL DEFAULT now(), last_error varchar(1000),
 delivered_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_due ON webhook_deliveries(status, next_attempt_at);
CREATE TABLE IF NOT EXISTS security_event_outbox (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, workspace_id uuid, event_type varchar(120) NOT NULL,
 aggregate_type varchar(120),
 aggregate_id varchar(200), payload jsonb NOT NULL, status varchar(32) NOT NULL DEFAULT 'PENDING',
 attempts int NOT NULL DEFAULT 0,
 available_at timestamptz NOT NULL DEFAULT now(), published_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_event_outbox_due ON security_event_outbox(status, available_at);
CREATE TABLE IF NOT EXISTS tenant_settings (
 tenant_id uuid PRIMARY KEY, security_mode varchar(32) NOT NULL DEFAULT 'ENFORCED',
 default_rate_limit int NOT NULL DEFAULT 120,
 runtime_session_ttl_seconds int NOT NULL DEFAULT 3600, audit_retention_days int NOT NULL DEFAULT 365,
 webhook_enabled boolean NOT NULL DEFAULT true, updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE api_clients ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_clients FORCE ROW LEVEL SECURITY;
ALTER TABLE webhooks ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhooks FORCE ROW LEVEL SECURITY;
ALTER TABLE webhook_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhook_deliveries FORCE ROW LEVEL SECURITY;
ALTER TABLE security_event_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_event_outbox FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_settings FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS api_clients_tenant ON api_clients;
CREATE POLICY api_clients_tenant ON api_clients USING (tenant_id = current_setting('app.tenant_id',
true)::uuid) WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
DROP POLICY IF EXISTS webhooks_tenant ON webhooks;
CREATE POLICY webhooks_tenant ON webhooks USING (tenant_id = current_setting('app.tenant_id',
true)::uuid) WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
DROP POLICY IF EXISTS webhook_deliveries_tenant ON webhook_deliveries;
CREATE POLICY webhook_deliveries_tenant ON webhook_deliveries USING (tenant_id = current_setting('app.tenant_id',
true)::uuid) WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
DROP POLICY IF EXISTS event_outbox_tenant ON security_event_outbox;
CREATE POLICY event_outbox_tenant ON security_event_outbox USING (tenant_id = current_setting('app.tenant_id',
true)::uuid) WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
DROP POLICY IF EXISTS tenant_settings_tenant ON tenant_settings;
CREATE POLICY tenant_settings_tenant ON tenant_settings USING (tenant_id = current_setting('app.tenant_id',
true)::uuid) WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- END V33__commercial_foundation_repair.sql

-- ============================================================================
-- BEGIN V34__adaptive_control_effectiveness.sql
-- ============================================================================
-- 3.18 Adaptive Security Control: durable effectiveness profiles.
-- Profiles are advisory evidence only;
-- they never mutate enforcement policy automatically.
CREATE TABLE IF NOT EXISTS security_control_effectiveness_profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  action_type TEXT NOT NULL,
  target TEXT NOT NULL,
  evidence_count INTEGER NOT NULL DEFAULT 0,
  effective_count INTEGER NOT NULL DEFAULT 0,
  ineffective_count INTEGER NOT NULL DEFAULT 0,
  neutral_count INTEGER NOT NULL DEFAULT 0,
  effectiveness_score DOUBLE PRECISION NOT NULL DEFAULT 0,
  confidence DOUBLE PRECISION NOT NULL DEFAULT 0,
  last_verification TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_effectiveness_profile_scope
  ON security_control_effectiveness_profiles(tenant_id, action_type, target);
CREATE INDEX IF NOT EXISTS idx_effectiveness_profile_rank
  ON security_control_effectiveness_profiles(tenant_id, effectiveness_score DESC, updated_at DESC);
ALTER TABLE security_control_effectiveness_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_control_effectiveness_profiles FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS security_control_effectiveness_profiles_tenant_policy ON security_control_effectiveness_profiles;
CREATE POLICY security_control_effectiveness_profiles_tenant_policy
  ON security_control_effectiveness_profiles
  USING (tenant_id::text = current_setting('app.tenant_id', true))
  WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

-- Backfill profiles from durable 3.17 verification history during upgrade.
INSERT INTO security_control_effectiveness_profiles
  (tenant_id, action_type, target, evidence_count, effective_count, ineffective_count, neutral_count,
   effectiveness_score, confidence, last_verification, updated_at)
SELECT tenant_id, action_type, target,
       COUNT(*) FILTER (WHERE verification IN ('EFFECTIVE','INEFFECTIVE','NEUTRAL')),
       COUNT(*) FILTER (WHERE verification='EFFECTIVE'),
       COUNT(*) FILTER (WHERE verification='INEFFECTIVE'),
       COUNT(*) FILTER (WHERE verification='NEUTRAL'),
       ROUND((100.0 * (COUNT(*) FILTER (WHERE verification='EFFECTIVE') + 0.5*COUNT(*) FILTER (WHERE
verification='NEUTRAL'))) /
             NULLIF(COUNT(*) FILTER (WHERE verification IN ('EFFECTIVE','INEFFECTIVE','NEUTRAL')),0), 2),
       ROUND(AVG(confidence) FILTER (WHERE verification IN ('EFFECTIVE','INEFFECTIVE','NEUTRAL'))::numeric, 2),
       MAX(evaluated_at), now()
FROM security_control_feedback
GROUP BY tenant_id, action_type, target
HAVING COUNT(*) FILTER (WHERE verification IN ('EFFECTIVE','INEFFECTIVE','NEUTRAL')) > 0
ON CONFLICT (tenant_id, action_type, target) DO UPDATE SET
  evidence_count=EXCLUDED.evidence_count,
  effective_count=EXCLUDED.effective_count,
  ineffective_count=EXCLUDED.ineffective_count,
  neutral_count=EXCLUDED.neutral_count,
  effectiveness_score=EXCLUDED.effectiveness_score,
  confidence=EXCLUDED.confidence,
  last_verification=EXCLUDED.last_verification,
  updated_at=now();

-- END V34__adaptive_control_effectiveness.sql

-- ============================================================================
-- BEGIN V35__policy_auto_tuning_approval_loop.sql
-- ============================================================================
-- 3.19 Policy Auto-Tuning: simulation and human-approved draft generation.
-- This table never grants permission to publish or enforce a policy automatically.
CREATE TABLE IF NOT EXISTS policy_tuning_proposals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  profile_id UUID NOT NULL,
  base_policy_id UUID NOT NULL,
  base_policy_version INTEGER NOT NULL,
  proposed_priority INTEGER NOT NULL,
  priority_delta INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',
  reason TEXT NOT NULL,
  simulation_status TEXT NOT NULL DEFAULT 'NOT_RUN',
  simulation_result JSONB,
  approved_policy_id UUID,
  requested_by TEXT,
  approved_by TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  approved_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_policy_tuning_proposals_tenant_created
  ON policy_tuning_proposals(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_policy_tuning_proposals_status
  ON policy_tuning_proposals(tenant_id, status, created_at DESC);
ALTER TABLE policy_tuning_proposals ENABLE ROW LEVEL SECURITY;
ALTER TABLE policy_tuning_proposals FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS policy_tuning_proposals_tenant_policy ON policy_tuning_proposals;
CREATE POLICY policy_tuning_proposals_tenant_policy
  ON policy_tuning_proposals
  USING (tenant_id::text = current_setting('app.tenant_id', true))
  WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

-- END V35__policy_auto_tuning_approval_loop.sql

-- ============================================================================
-- BEGIN V36__canary_observation_and_auto_rollback.sql
-- ============================================================================
-- 3.20 Canary rollout guard: durable observations and automatic safety aborts.
CREATE TABLE IF NOT EXISTS canary_observations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  deployment_id UUID NOT NULL,
  observed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  canary_events INTEGER NOT NULL DEFAULT 0,
  baseline_events INTEGER NOT NULL DEFAULT 0,
  canary_deny_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
  baseline_deny_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
  canary_high_risk_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
  baseline_high_risk_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
  canary_avg_risk DOUBLE PRECISION NOT NULL DEFAULT 0,
  baseline_avg_risk DOUBLE PRECISION NOT NULL DEFAULT 0,
  risk_delta DOUBLE PRECISION NOT NULL DEFAULT 0,
  decision_delta DOUBLE PRECISION NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'HEALTHY',
  reason TEXT
);
CREATE INDEX IF NOT EXISTS idx_canary_observations_deployment_time
  ON canary_observations(tenant_id, deployment_id, observed_at DESC);
ALTER TABLE canary_observations ENABLE ROW LEVEL SECURITY;
ALTER TABLE canary_observations FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS canary_observations_tenant_policy ON canary_observations;
CREATE POLICY canary_observations_tenant_policy ON canary_observations
  USING (tenant_id::text = current_setting('app.tenant_id', true))
  WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE policy_deployments ADD COLUMN IF NOT EXISTS auto_rollback_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE policy_deployments ADD COLUMN IF NOT EXISTS auto_stop_threshold DOUBLE PRECISION NOT NULL DEFAULT 20;
ALTER TABLE policy_deployments ADD COLUMN IF NOT EXISTS min_canary_events INTEGER NOT NULL DEFAULT 20;

-- END V36__canary_observation_and_auto_rollback.sql

-- ============================================================================
-- BEGIN V37__enterprise_runtime_integration.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS enterprise_runtime_audit (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 operation VARCHAR(80) NOT NULL, provider VARCHAR(80), status VARCHAR(20) NOT NULL,
 detail JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_enterprise_runtime_audit_tenant ON enterprise_runtime_audit(tenant_id,created_at DESC);
ALTER TABLE enterprise_runtime_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE enterprise_runtime_audit
FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_enterprise_runtime_audit ON enterprise_runtime_audit USING (tenant_id =
NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

-- END V37__enterprise_runtime_integration.sql

-- ============================================================================
-- BEGIN V38__enterprise_runtime_hardening.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS enterprise_sso_preflight_sessions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 state_hash VARCHAR(128) NOT NULL, nonce_hash VARCHAR(128) NOT NULL, provider VARCHAR(80) NOT NULL,
 issuer TEXT NOT NULL, client_id TEXT NOT NULL, redirect_uri TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 expires_at TIMESTAMPTZ NOT NULL, used_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_sso_preflight_state ON enterprise_sso_preflight_sessions(state_hash);
CREATE INDEX IF NOT EXISTS idx_sso_preflight_tenant ON enterprise_sso_preflight_sessions(tenant_id, created_at DESC);
ALTER TABLE enterprise_sso_preflight_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE enterprise_sso_preflight_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_sso_preflight ON enterprise_sso_preflight_sessions USING (tenant_id =
NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

CREATE TABLE IF NOT EXISTS compliance_audit_bundles (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
 assessment_id UUID NOT NULL REFERENCES compliance_assessments(id),
 bundle_hash VARCHAR(128) NOT NULL, signature TEXT, key_id VARCHAR(160),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by VARCHAR(160)
);
CREATE INDEX IF NOT EXISTS idx_compliance_audit_bundle_tenant ON compliance_audit_bundles(tenant_id, created_at DESC);
ALTER TABLE compliance_audit_bundles ENABLE ROW LEVEL SECURITY;
ALTER TABLE compliance_audit_bundles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_compliance_audit_bundle ON compliance_audit_bundles USING (tenant_id =
NULLIF(current_setting('app.tenant_id',
true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id',
true),'')::uuid);

-- END V38__enterprise_runtime_hardening.sql

-- ============================================================================
-- BEGIN V39__deployment_resilience_audit.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS deployment_resilience_events (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID REFERENCES tenants(id),
 event_type VARCHAR(80) NOT NULL,
 release_version VARCHAR(40) NOT NULL,
 status VARCHAR(40) NOT NULL,
 details JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_deployment_resilience_events_tenant ON deployment_resilience_events(tenant_id,
created_at DESC);
ALTER TABLE deployment_resilience_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE deployment_resilience_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_deployment_resilience_events ON deployment_resilience_events
USING (tenant_id IS NULL OR tenant_id = NULLIF(current_setting('app.tenant_id', true),'')::uuid)
WITH CHECK (tenant_id IS NULL OR tenant_id = NULLIF(current_setting('app.tenant_id', true),'')::uuid);

-- END V39__deployment_resilience_audit.sql

-- ============================================================================
-- BEGIN V40__enterprise_sre_observability.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS sre_incident_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    incident_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    source VARCHAR(64) NOT NULL,
    event_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    correlation_key VARCHAR(256),
    details JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_sre_incident_events_tenant_time
    ON sre_incident_events(tenant_id, event_time DESC);

ALTER TABLE sre_incident_events ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS sre_incident_events_tenant_isolation ON sre_incident_events;
CREATE POLICY sre_incident_events_tenant_isolation
    ON sre_incident_events
    USING (tenant_id::text = current_setting('app.tenant_id', true));

-- END V40__enterprise_sre_observability.sql

-- ============================================================================
-- BEGIN V41__incident_response_and_secops.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS secops_incidents (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, incident_key VARCHAR(128) NOT NULL,
 title VARCHAR(256) NOT NULL, severity VARCHAR(16) NOT NULL, status VARCHAR(32) NOT NULL,
 source VARCHAR(64) NOT NULL, opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 acknowledged_at TIMESTAMPTZ, resolved_at TIMESTAMPTZ, owner VARCHAR(256), summary TEXT,
 UNIQUE (tenant_id, incident_key)
);
CREATE TABLE IF NOT EXISTS secops_incident_evidence (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, incident_id UUID NOT NULL REFERENCES secops_incidents(id),
 evidence_type VARCHAR(64) NOT NULL, source VARCHAR(128) NOT NULL,
 collected_at TIMESTAMPTZ NOT NULL DEFAULT now(), content_hash VARCHAR(128) NOT NULL,
 payload JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE TABLE IF NOT EXISTS secops_response_actions (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, incident_id UUID NOT NULL REFERENCES secops_incidents(id),
 action_type VARCHAR(64) NOT NULL, requested_by VARCHAR(256) NOT NULL,
 approval_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 execution_status VARCHAR(32) NOT NULL DEFAULT 'NOT_EXECUTED',
 requested_at TIMESTAMPTZ NOT NULL DEFAULT now(), approved_at TIMESTAMPTZ,
 executed_at TIMESTAMPTZ, rationale TEXT
);
CREATE TABLE IF NOT EXISTS secops_postmortems (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, incident_id UUID NOT NULL REFERENCES secops_incidents(id),
 root_cause TEXT, customer_impact TEXT, timeline JSONB NOT NULL DEFAULT '[]'::jsonb,
 corrective_actions JSONB NOT NULL DEFAULT '[]'::jsonb, completed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_secops_incidents_tenant_status ON secops_incidents(tenant_id,status,opened_at DESC);
CREATE INDEX IF NOT EXISTS idx_secops_evidence_incident ON secops_incident_evidence(tenant_id,
incident_id,collected_at DESC);
CREATE INDEX IF NOT EXISTS idx_secops_actions_incident ON secops_response_actions(tenant_id,
incident_id,requested_at DESC);
ALTER TABLE secops_incidents ENABLE ROW LEVEL SECURITY;
ALTER TABLE secops_incident_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE secops_response_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE secops_postmortems ENABLE ROW LEVEL SECURITY;
CREATE POLICY secops_incidents_tenant_isolation ON secops_incidents
 USING (tenant_id::text = current_setting('app.tenant_id', true));
CREATE POLICY secops_evidence_tenant_isolation ON secops_incident_evidence
 USING (tenant_id::text = current_setting('app.tenant_id', true));
CREATE POLICY secops_actions_tenant_isolation ON secops_response_actions
 USING (tenant_id::text = current_setting('app.tenant_id', true));
CREATE POLICY secops_postmortems_tenant_isolation ON secops_postmortems
 USING (tenant_id::text = current_setting('app.tenant_id', true));

-- END V41__incident_response_and_secops.sql

-- ============================================================================
-- BEGIN V42__soar_connectors.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS soar_connectors (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, connector_name VARCHAR(128) NOT NULL,
 provider VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'CONFIGURED',
 endpoint_ref VARCHAR(512), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id, connector_name)
);
CREATE TABLE IF NOT EXISTS soar_execution_results (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, incident_id UUID,
 response_action_id UUID, connector_name VARCHAR(128) NOT NULL,
 status VARCHAR(32) NOT NULL, result_hash VARCHAR(128), result JSONB NOT NULL DEFAULT '{}'::jsonb,
 executed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE soar_connectors ENABLE ROW LEVEL SECURITY;
ALTER TABLE soar_execution_results ENABLE ROW LEVEL SECURITY;
CREATE POLICY soar_connectors_tenant ON soar_connectors USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY soar_results_tenant ON soar_execution_results USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V42__soar_connectors.sql

-- ============================================================================
-- BEGIN V43__secops_workflows.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS secops_workflows (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, name VARCHAR(256) NOT NULL,
 version INTEGER NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
 definition JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,name,version)
);
CREATE TABLE IF NOT EXISTS secops_workflow_runs (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, workflow_id UUID NOT NULL REFERENCES secops_workflows(id),
 incident_id UUID, status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
 current_step VARCHAR(128), context JSONB NOT NULL DEFAULT '{}'::jsonb,
 started_at TIMESTAMPTZ NOT NULL DEFAULT now(), finished_at TIMESTAMPTZ
);
ALTER TABLE secops_workflows ENABLE ROW LEVEL SECURITY;
ALTER TABLE secops_workflow_runs ENABLE ROW LEVEL SECURITY;
CREATE POLICY secops_workflows_tenant ON secops_workflows USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY secops_workflow_runs_tenant ON secops_workflow_runs USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V43__secops_workflows.sql

-- ============================================================================
-- BEGIN V44__enterprise_security_governance.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS governance_approvals (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, object_type VARCHAR(64) NOT NULL,
 object_id UUID NOT NULL, requested_by VARCHAR(256) NOT NULL,
 approved_by VARCHAR(256), status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 reason TEXT, requested_at TIMESTAMPTZ NOT NULL DEFAULT now(), decided_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS governance_audit_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, actor VARCHAR(256) NOT NULL,
 event_type VARCHAR(128) NOT NULL, object_type VARCHAR(64), object_id UUID,
 event_hash VARCHAR(128) NOT NULL, payload JSONB NOT NULL DEFAULT '{}'::jsonb,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_governance_audit_tenant_time ON governance_audit_events(tenant_id,occurred_at DESC);
ALTER TABLE governance_approvals ENABLE ROW LEVEL SECURITY;
ALTER TABLE governance_audit_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY governance_approvals_tenant ON governance_approvals USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY governance_audit_tenant ON governance_audit_events USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V44__enterprise_security_governance.sql

-- ============================================================================
-- BEGIN V45__autonomous_security_operations.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS autonomous_security_runs (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, incident_id UUID,
 mode VARCHAR(32) NOT NULL DEFAULT 'ASSISTED',
 stage VARCHAR(64) NOT NULL DEFAULT 'DETECT',
 confidence NUMERIC(6,3), risk_score NUMERIC(8,3),
 recommendation JSONB NOT NULL DEFAULT '{}'::jsonb,
 approval_required BOOLEAN NOT NULL DEFAULT TRUE,
 approved_by VARCHAR(256), status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
 started_at TIMESTAMPTZ NOT NULL DEFAULT now(), finished_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS autonomous_security_feedback (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, run_id UUID NOT NULL REFERENCES autonomous_security_runs(id),
 outcome VARCHAR(32) NOT NULL, effectiveness_score NUMERIC(6,3),
 evidence_hash VARCHAR(128), notes TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE autonomous_security_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE autonomous_security_feedback ENABLE ROW LEVEL SECURITY;
CREATE POLICY autonomous_runs_tenant ON autonomous_security_runs USING (tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY autonomous_feedback_tenant ON autonomous_security_feedback USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V45__autonomous_security_operations.sql

-- ============================================================================
-- BEGIN V46__threat_intelligence_fusion.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS threat_intelligence_indicators (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, indicator_type VARCHAR(32) NOT NULL,
 indicator_value TEXT NOT NULL, source VARCHAR(256) NOT NULL, confidence NUMERIC(6,3),
 severity VARCHAR(32), tags JSONB NOT NULL DEFAULT '[]'::jsonb,
 first_seen TIMESTAMPTZ, last_seen TIMESTAMPTZ, active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,indicator_type,indicator_value,source)
);
CREATE TABLE IF NOT EXISTS threat_intelligence_matches (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, indicator_id UUID NOT NULL,
 object_type VARCHAR(64), object_id UUID, match_context JSONB NOT NULL DEFAULT '{}'::jsonb,
 matched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE threat_intelligence_indicators ENABLE ROW LEVEL SECURITY;
ALTER TABLE threat_intelligence_matches ENABLE ROW LEVEL SECURITY;
CREATE POLICY ti_indicators_tenant ON threat_intelligence_indicators USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY ti_matches_tenant ON threat_intelligence_matches USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V46__threat_intelligence_fusion.sql

-- ============================================================================
-- BEGIN V47__attack_path_analysis.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS attack_path_nodes (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, node_type VARCHAR(64) NOT NULL,
 asset_ref VARCHAR(512) NOT NULL, risk_score NUMERIC(8,3), attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS attack_path_edges (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, source_node UUID NOT NULL, target_node UUID NOT NULL,
 relation VARCHAR(128) NOT NULL, weight NUMERIC(8,3) DEFAULT 1,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS attack_paths (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, source_node UUID, target_node UUID,
 criticality NUMERIC(8,3), path JSONB NOT NULL DEFAULT '[]'::jsonb, status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE attack_path_nodes ENABLE ROW LEVEL SECURITY;
ALTER TABLE attack_path_edges ENABLE ROW LEVEL SECURITY;
ALTER TABLE attack_paths ENABLE ROW LEVEL SECURITY;
CREATE POLICY attack_nodes_tenant ON attack_path_nodes USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY attack_edges_tenant ON attack_path_edges USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY attack_paths_tenant ON attack_paths USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V47__attack_path_analysis.sql

-- ============================================================================
-- BEGIN V48__identity_threat_detection.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS identity_risk_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, subject_ref VARCHAR(512) NOT NULL,
 event_type VARCHAR(128) NOT NULL, source_ip INET, risk_score NUMERIC(8,3),
 factors JSONB NOT NULL DEFAULT '{}'::jsonb, occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS identity_risk_profiles (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, subject_ref VARCHAR(512) NOT NULL,
 risk_score NUMERIC(8,3) NOT NULL DEFAULT 0, risk_level VARCHAR(32) NOT NULL DEFAULT 'LOW',
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,subject_ref)
);
ALTER TABLE identity_risk_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity_risk_profiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY identity_events_tenant ON identity_risk_events USING (tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY identity_profiles_tenant ON identity_risk_profiles USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V48__identity_threat_detection.sql

-- ============================================================================
-- BEGIN V49__cloud_security_operations.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS cloud_assets (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, provider VARCHAR(32) NOT NULL,
 account_ref VARCHAR(256) NOT NULL, asset_ref VARCHAR(512) NOT NULL,
 asset_type VARCHAR(128), risk_score NUMERIC(8,3), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 discovered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,provider,asset_ref)
);
CREATE TABLE IF NOT EXISTS cloud_findings (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, asset_id UUID,
 finding_type VARCHAR(128) NOT NULL, severity VARCHAR(32), evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
 status VARCHAR(32) NOT NULL DEFAULT 'OPEN', created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE cloud_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE cloud_findings ENABLE ROW LEVEL SECURITY;
CREATE POLICY cloud_assets_tenant ON cloud_assets USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY cloud_findings_tenant ON cloud_findings USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V49__cloud_security_operations.sql

-- ============================================================================
-- BEGIN V50__endpoint_xdr_operations.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS xdr_entities (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, entity_type VARCHAR(64) NOT NULL,
 entity_ref VARCHAR(512) NOT NULL, hostname VARCHAR(256), attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
 first_seen TIMESTAMPTZ, last_seen TIMESTAMPTZ, UNIQUE(tenant_id,entity_type,entity_ref)
);
CREATE TABLE IF NOT EXISTS xdr_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, entity_id UUID,
 observation_type VARCHAR(128) NOT NULL, severity VARCHAR(32), data JSONB NOT NULL DEFAULT '{}'::jsonb,
 observed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE xdr_entities ENABLE ROW LEVEL SECURITY;
ALTER TABLE xdr_observations ENABLE ROW LEVEL SECURITY;
CREATE POLICY xdr_entities_tenant ON xdr_entities USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY xdr_observations_tenant ON xdr_observations USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V50__endpoint_xdr_operations.sql

-- ============================================================================
-- BEGIN V51__security_data_lake.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS security_data_lake_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, event_time TIMESTAMPTZ NOT NULL,
 source_type VARCHAR(64) NOT NULL, source_ref VARCHAR(512), event_type VARCHAR(128) NOT NULL,
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, payload_hash VARCHAR(128) NOT NULL,
 ingested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_security_lake_tenant_time ON security_data_lake_events(tenant_id,event_time DESC);
CREATE INDEX IF NOT EXISTS idx_security_lake_type ON security_data_lake_events(tenant_id,event_type);
ALTER TABLE security_data_lake_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY security_lake_tenant ON security_data_lake_events USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V51__security_data_lake.sql

-- ============================================================================
-- BEGIN V52__ai_security_analyst.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS ai_security_analyses (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, incident_id UUID,
 model_ref VARCHAR(256) NOT NULL, analysis_type VARCHAR(64) NOT NULL,
 prompt_hash VARCHAR(128) NOT NULL, output JSONB NOT NULL DEFAULT '{}'::jsonb,
 confidence NUMERIC(6,3), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE ai_security_analyses ENABLE ROW LEVEL SECURITY;
CREATE POLICY ai_security_analyses_tenant ON ai_security_analyses USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V52__ai_security_analyst.sql

-- ============================================================================
-- BEGIN V53__continuous_threat_exposure_management.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS exposure_assessments (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, asset_ref VARCHAR(512) NOT NULL,
 exposure_score NUMERIC(8,3) NOT NULL, attack_path_score NUMERIC(8,3) DEFAULT 0,
 identity_score NUMERIC(8,3) DEFAULT 0, vulnerability_score NUMERIC(8,3) DEFAULT 0,
 observed_threat_score NUMERIC(8,3) DEFAULT 0, priority VARCHAR(32),
 evidence JSONB NOT NULL DEFAULT '{}'::jsonb, assessed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE exposure_assessments ENABLE ROW LEVEL SECURITY;
CREATE POLICY exposure_assessments_tenant ON exposure_assessments USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V53__continuous_threat_exposure_management.sql

-- ============================================================================
-- BEGIN V54__enterprise_security_command_center.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS security_command_center_snapshots (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, overall_risk NUMERIC(8,3),
 exposure_score NUMERIC(8,3), incident_count INTEGER DEFAULT 0,
 active_response_count INTEGER DEFAULT 0, compliance_score NUMERIC(8,3),
 snapshot JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE security_command_center_snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY command_center_tenant ON security_command_center_snapshots USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V54__enterprise_security_command_center.sql

-- ============================================================================
-- BEGIN V55__autonomous_cyber_defense_platform.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS autonomous_defense_cycles (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, incident_id UUID,
 phase VARCHAR(64) NOT NULL, risk_score NUMERIC(8,3), confidence NUMERIC(6,3),
 recommendation JSONB NOT NULL DEFAULT '{}'::jsonb,
 execution_ref UUID, verification JSONB NOT NULL DEFAULT '{}'::jsonb,
 learning JSONB NOT NULL DEFAULT '{}'::jsonb,
 status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
 started_at TIMESTAMPTZ NOT NULL DEFAULT now(), completed_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS autonomous_defense_metrics (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, cycle_id UUID NOT NULL,
 detection_quality NUMERIC(6,3), response_effectiveness NUMERIC(6,3),
 false_positive_rate NUMERIC(6,3), evidence_completeness NUMERIC(6,3),
 measured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE autonomous_defense_cycles ENABLE ROW LEVEL SECURITY;
ALTER TABLE autonomous_defense_metrics ENABLE ROW LEVEL SECURITY;
CREATE POLICY autonomous_cycles_tenant ON autonomous_defense_cycles USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY autonomous_metrics_tenant ON autonomous_defense_metrics USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V55__autonomous_cyber_defense_platform.sql

-- ============================================================================
-- BEGIN V56__zero_trust_network_defense.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS network_security_flows (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, source_ref VARCHAR(512), destination_ref VARCHAR(512),
 protocol VARCHAR(32), action VARCHAR(32), risk_score NUMERIC(8,3), segment VARCHAR(128),
 observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE TABLE IF NOT EXISTS network_microsegments (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, name VARCHAR(256) NOT NULL,
 definition JSONB NOT NULL DEFAULT '{}'::jsonb, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,name)
);
ALTER TABLE network_security_flows ENABLE ROW LEVEL SECURITY;
ALTER TABLE network_microsegments ENABLE ROW LEVEL SECURITY;
CREATE POLICY network_flows_tenant ON network_security_flows USING (tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY network_segments_tenant ON network_microsegments USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V56__zero_trust_network_defense.sql

-- ============================================================================
-- BEGIN V57__software_supply_chain_security.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS supply_chain_components (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, component_ref VARCHAR(512) NOT NULL,
 version VARCHAR(128), ecosystem VARCHAR(64), sbom_hash VARCHAR(128), risk_score NUMERIC(8,3),
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,component_ref,version)
);
CREATE TABLE IF NOT EXISTS supply_chain_findings (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, component_id UUID,
 finding_type VARCHAR(128), severity VARCHAR(32), cve VARCHAR(64), evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE supply_chain_components ENABLE ROW LEVEL SECURITY;
ALTER TABLE supply_chain_findings ENABLE ROW LEVEL SECURITY;
CREATE POLICY supply_components_tenant ON supply_chain_components USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY supply_findings_tenant ON supply_chain_findings USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V57__software_supply_chain_security.sql

-- ============================================================================
-- BEGIN V58__kubernetes_runtime_defense.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS kubernetes_workloads (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, cluster_ref VARCHAR(256), namespace VARCHAR(256),
 workload_ref VARCHAR(512), workload_type VARCHAR(64), risk_score NUMERIC(8,3),
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb, observed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS kubernetes_runtime_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, workload_id UUID,
 event_type VARCHAR(128), severity VARCHAR(32), evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE kubernetes_workloads ENABLE ROW LEVEL SECURITY;
ALTER TABLE kubernetes_runtime_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY k8s_workloads_tenant ON kubernetes_workloads USING (tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY k8s_runtime_tenant ON kubernetes_runtime_events USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V58__kubernetes_runtime_defense.sql

-- ============================================================================
-- BEGIN V59__saas_security_posture.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS saas_applications (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, provider VARCHAR(128), app_ref VARCHAR(512),
 trust_score NUMERIC(8,3), risk_score NUMERIC(8,3), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 discovered_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,provider,app_ref)
);
CREATE TABLE IF NOT EXISTS saas_findings (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, app_id UUID, finding_type VARCHAR(128),
 severity VARCHAR(32), evidence JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE saas_applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE saas_findings ENABLE ROW LEVEL SECURITY;
CREATE POLICY saas_apps_tenant ON saas_applications USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY saas_findings_tenant ON saas_findings USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V59__saas_security_posture.sql

-- ============================================================================
-- BEGIN V60__data_security_posture.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS data_assets (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, asset_ref VARCHAR(512) NOT NULL,
 classification VARCHAR(64), sensitivity NUMERIC(8,3), exposure_score NUMERIC(8,3),
 owner_ref VARCHAR(256), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 discovered_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,asset_ref)
);
CREATE TABLE IF NOT EXISTS data_access_risks (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, asset_id UUID,
 subject_ref VARCHAR(512), access_type VARCHAR(64), risk_score NUMERIC(8,3),
 evidence JSONB NOT NULL DEFAULT '{}'::jsonb, observed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE data_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE data_access_risks ENABLE ROW LEVEL SECURITY;
CREATE POLICY data_assets_tenant ON data_assets USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY data_access_tenant ON data_access_risks USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V60__data_security_posture.sql

-- ============================================================================
-- BEGIN V61__privacy_insider_risk.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS insider_risk_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, subject_ref VARCHAR(512) NOT NULL,
 event_type VARCHAR(128), risk_score NUMERIC(8,3), privacy_impact NUMERIC(8,3),
 evidence JSONB NOT NULL DEFAULT '{}'::jsonb, occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS insider_risk_profiles (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, subject_ref VARCHAR(512) NOT NULL,
 risk_score NUMERIC(8,3) DEFAULT 0, risk_level VARCHAR(32) DEFAULT 'LOW',
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,subject_ref)
);
ALTER TABLE insider_risk_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE insider_risk_profiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY insider_events_tenant ON insider_risk_events USING (tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY insider_profiles_tenant ON insider_risk_profiles USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V61__privacy_insider_risk.sql

-- ============================================================================
-- BEGIN V62__cyber_deception.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS deception_assets (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, asset_type VARCHAR(64) NOT NULL,
 asset_ref VARCHAR(512) NOT NULL, status VARCHAR(32) DEFAULT 'ACTIVE',
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,asset_ref)
);
CREATE TABLE IF NOT EXISTS deception_hits (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, asset_id UUID,
 subject_ref VARCHAR(512), hit_type VARCHAR(128), confidence NUMERIC(6,3),
 evidence JSONB NOT NULL DEFAULT '{}'::jsonb, occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE deception_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE deception_hits ENABLE ROW LEVEL SECURITY;
CREATE POLICY deception_assets_tenant ON deception_assets USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY deception_hits_tenant ON deception_hits USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V62__cyber_deception.sql

-- ============================================================================
-- BEGIN V63__digital_twin_attack_simulation.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS digital_twin_scenarios (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, name VARCHAR(256) NOT NULL,
 baseline_ref VARCHAR(512), scenario JSONB NOT NULL DEFAULT '{}'::jsonb,
 status VARCHAR(32) DEFAULT 'DRAFT', created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,name)
);
CREATE TABLE IF NOT EXISTS digital_twin_runs (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, scenario_id UUID,
 predicted_risk NUMERIC(8,3), predicted_impact NUMERIC(8,3),
 predicted_path JSONB NOT NULL DEFAULT '[]'::jsonb, outcome JSONB NOT NULL DEFAULT '{}'::jsonb,
 status VARCHAR(32) DEFAULT 'COMPLETED', started_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE digital_twin_scenarios ENABLE ROW LEVEL SECURITY;
ALTER TABLE digital_twin_runs ENABLE ROW LEVEL SECURITY;
CREATE POLICY twin_scenarios_tenant ON digital_twin_scenarios USING (tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY twin_runs_tenant ON digital_twin_runs USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V63__digital_twin_attack_simulation.sql

-- ============================================================================
-- BEGIN V64__global_cyber_defense_mesh.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS defense_mesh_nodes (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, region VARCHAR(128), cluster_ref VARCHAR(256),
 status VARCHAR(32) DEFAULT 'ACTIVE', capabilities JSONB NOT NULL DEFAULT '{}'::jsonb,
 last_heartbeat TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS defense_mesh_signals (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, node_id UUID,
 signal_type VARCHAR(128), correlation_key VARCHAR(256), risk_score NUMERIC(8,3),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE defense_mesh_nodes ENABLE ROW LEVEL SECURITY;
ALTER TABLE defense_mesh_signals ENABLE ROW LEVEL SECURITY;
CREATE POLICY mesh_nodes_tenant ON defense_mesh_nodes USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY mesh_signals_tenant ON defense_mesh_signals USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V64__global_cyber_defense_mesh.sql

-- ============================================================================
-- BEGIN V65__autonomous_cyber_defense_os.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS cyber_defense_os_cycles (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, phase VARCHAR(64) NOT NULL,
 risk_score NUMERIC(8,3), confidence NUMERIC(6,3), prevention JSONB NOT NULL DEFAULT '{}'::jsonb,
 detection JSONB NOT NULL DEFAULT '{}'::jsonb, response JSONB NOT NULL DEFAULT '{}'::jsonb,
 recovery JSONB NOT NULL DEFAULT '{}'::jsonb, learning JSONB NOT NULL DEFAULT '{}'::jsonb,
 status VARCHAR(32) DEFAULT 'ACTIVE', started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 completed_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS cyber_defense_os_metrics (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, cycle_id UUID,
 prevention_effectiveness NUMERIC(6,3), detection_quality NUMERIC(6,3),
 response_effectiveness NUMERIC(6,3), recovery_effectiveness NUMERIC(6,3),
 learning_gain NUMERIC(6,3), measured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE cyber_defense_os_cycles ENABLE ROW LEVEL SECURITY;
ALTER TABLE cyber_defense_os_metrics ENABLE ROW LEVEL SECURITY;
CREATE POLICY defense_os_cycles_tenant ON cyber_defense_os_cycles USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY defense_os_metrics_tenant ON cyber_defense_os_metrics USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V65__autonomous_cyber_defense_os.sql

-- ============================================================================
-- BEGIN V66__ai_threat_hunting.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS ai_threat_hunts (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, query_ref VARCHAR(512), hunt_type VARCHAR(128),
 risk_score NUMERIC(8,3), confidence NUMERIC(6,3), findings JSONB NOT NULL DEFAULT '[]'::jsonb,
 status VARCHAR(32) DEFAULT 'COMPLETED', started_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE ai_threat_hunts ENABLE ROW LEVEL SECURITY;
CREATE POLICY ai_threat_hunts_tenant ON ai_threat_hunts USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V66__ai_threat_hunting.sql

-- ============================================================================
-- BEGIN V67__autonomous_soc.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS autonomous_soc_cases (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, incident_ref VARCHAR(512), phase VARCHAR(64),
 risk_score NUMERIC(8,3), recommendation JSONB NOT NULL DEFAULT '{}'::jsonb,
 approval_required BOOLEAN NOT NULL DEFAULT TRUE, status VARCHAR(32) DEFAULT 'OPEN',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE autonomous_soc_cases ENABLE ROW LEVEL SECURITY;
CREATE POLICY autonomous_soc_cases_tenant ON autonomous_soc_cases USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V67__autonomous_soc.sql

-- ============================================================================
-- BEGIN V68__cloud_native_defense.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS cloud_native_assets (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, provider VARCHAR(32), account_ref VARCHAR(256),
 asset_ref VARCHAR(512), asset_type VARCHAR(128), risk_score NUMERIC(8,3),
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb, observed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS cloud_native_findings (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, asset_id UUID, finding_type VARCHAR(128),
 severity VARCHAR(32), evidence JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE cloud_native_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE cloud_native_findings ENABLE ROW LEVEL SECURITY;
CREATE POLICY cloud_assets_tenant ON cloud_native_assets USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY cloud_findings_tenant ON cloud_native_findings USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V68__cloud_native_defense.sql

-- ============================================================================
-- BEGIN V69__api_security_operations.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS api_security_assets (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, api_ref VARCHAR(512), owner_ref VARCHAR(256),
 exposure_score NUMERIC(8,3), abuse_score NUMERIC(8,3), shadow BOOLEAN DEFAULT FALSE,
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb, discovered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,api_ref)
);
CREATE TABLE IF NOT EXISTS api_security_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, api_id UUID, event_type VARCHAR(128),
 risk_score NUMERIC(8,3), evidence JSONB NOT NULL DEFAULT '{}'::jsonb, occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE api_security_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_security_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY api_assets_tenant ON api_security_assets USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY api_events_tenant ON api_security_events USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V69__api_security_operations.sql

-- ============================================================================
-- BEGIN V70__identity_fabric.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS identity_fabric_entities (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, entity_ref VARCHAR(512) NOT NULL,
 entity_type VARCHAR(64), trust_score NUMERIC(8,3), risk_score NUMERIC(8,3),
 attributes JSONB NOT NULL DEFAULT '{}'::jsonb, observed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,entity_ref)
);
CREATE TABLE IF NOT EXISTS identity_fabric_relationships (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, subject_ref VARCHAR(512), object_ref VARCHAR(512),
 relationship_type VARCHAR(128), risk_score NUMERIC(8,3), metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
ALTER TABLE identity_fabric_entities ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity_fabric_relationships ENABLE ROW LEVEL SECURITY;
CREATE POLICY identity_fabric_entities_tenant ON identity_fabric_entities USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY identity_fabric_relationships_tenant ON identity_fabric_relationships USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V70__identity_fabric.sql

-- ============================================================================
-- BEGIN V71__ransomware_defense.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS ransomware_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, subject_ref VARCHAR(512), behavior_type VARCHAR(128),
 risk_score NUMERIC(8,3), affected_assets INTEGER DEFAULT 0, evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS ransomware_recovery_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, scope_ref VARCHAR(512), recovery_point VARCHAR(256),
 readiness_score NUMERIC(8,3), status VARCHAR(32) DEFAULT 'READY', metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
ALTER TABLE ransomware_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE ransomware_recovery_plans ENABLE ROW LEVEL SECURITY;
CREATE POLICY ransomware_events_tenant ON ransomware_events USING (tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY ransomware_plans_tenant ON ransomware_recovery_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V71__ransomware_defense.sql

-- ============================================================================
-- BEGIN V72__security_knowledge_graph.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS security_graph_nodes (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, node_type VARCHAR(128), node_ref VARCHAR(512),
 risk_score NUMERIC(8,3), properties JSONB NOT NULL DEFAULT '{}'::jsonb,
 UNIQUE(tenant_id,node_type,node_ref)
);
CREATE TABLE IF NOT EXISTS security_graph_edges (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, source_node UUID, target_node UUID,
 relation_type VARCHAR(128), risk_score NUMERIC(8,3), properties JSONB NOT NULL DEFAULT '{}'::jsonb
);
ALTER TABLE security_graph_nodes ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_graph_edges ENABLE ROW LEVEL SECURITY;
CREATE POLICY security_graph_nodes_tenant ON security_graph_nodes USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY security_graph_edges_tenant ON security_graph_edges USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V72__security_knowledge_graph.sql

-- ============================================================================
-- BEGIN V73__predictive_cyber_defense.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS predictive_defense_predictions (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, target_ref VARCHAR(512), prediction_type VARCHAR(128),
 probability NUMERIC(8,5), horizon_minutes INTEGER, contributing_signals JSONB NOT NULL DEFAULT '[]'::jsonb,
 recommended_controls JSONB NOT NULL DEFAULT '[]'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE predictive_defense_predictions ENABLE ROW LEVEL SECURITY;
CREATE POLICY predictive_defense_tenant ON predictive_defense_predictions USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V73__predictive_cyber_defense.sql

-- ============================================================================
-- BEGIN V74__global_soc_federation.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS soc_federation_nodes (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, region VARCHAR(128), soc_ref VARCHAR(256),
 trust_score NUMERIC(8,3), status VARCHAR(32) DEFAULT 'ACTIVE', capabilities JSONB NOT NULL DEFAULT '{}'::jsonb,
 last_heartbeat TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS soc_federation_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, source_node UUID, event_type VARCHAR(128),
 correlation_key VARCHAR(256), severity VARCHAR(32), payload JSONB NOT NULL DEFAULT '{}'::jsonb,
 received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE soc_federation_nodes ENABLE ROW LEVEL SECURITY;
ALTER TABLE soc_federation_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY soc_federation_nodes_tenant ON soc_federation_nodes USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY soc_federation_events_tenant ON soc_federation_events USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V74__global_soc_federation.sql

-- ============================================================================
-- BEGIN V75__cyber_defense_intelligence_os.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS cyber_defense_intelligence_cycles (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, phase VARCHAR(64) NOT NULL,
 threat_probability NUMERIC(8,5), risk_score NUMERIC(8,3), confidence NUMERIC(6,3),
 knowledge_context JSONB NOT NULL DEFAULT '{}'::jsonb, prediction JSONB NOT NULL DEFAULT '{}'::jsonb,
 recommendation JSONB NOT NULL DEFAULT '{}'::jsonb, governance JSONB NOT NULL DEFAULT '{}'::jsonb,
 response JSONB NOT NULL DEFAULT '{}'::jsonb, verification JSONB NOT NULL DEFAULT '{}'::jsonb,
 recovery JSONB NOT NULL DEFAULT '{}'::jsonb, learning JSONB NOT NULL DEFAULT '{}'::jsonb,
 status VARCHAR(32) DEFAULT 'ACTIVE', started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 completed_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS cyber_defense_intelligence_metrics (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, cycle_id UUID,
 prediction_accuracy NUMERIC(8,5), detection_quality NUMERIC(8,3), response_quality NUMERIC(8,3),
 recovery_quality NUMERIC(8,3), learning_gain NUMERIC(8,3), measured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE cyber_defense_intelligence_cycles ENABLE ROW LEVEL SECURITY;
ALTER TABLE cyber_defense_intelligence_metrics ENABLE ROW LEVEL SECURITY;
CREATE POLICY cdi_cycles_tenant ON cyber_defense_intelligence_cycles USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY cdi_metrics_tenant ON cyber_defense_intelligence_metrics USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V75__cyber_defense_intelligence_os.sql

-- ============================================================================
-- BEGIN V76__ai_security_reasoning.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS ai_security_reasoning_runs (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, case_ref VARCHAR(512), hypothesis TEXT,
 reasoning JSONB NOT NULL DEFAULT '{}'::jsonb, confidence NUMERIC(6,3),
 recommended_actions JSONB NOT NULL DEFAULT '[]'::jsonb,
 status VARCHAR(32) DEFAULT 'COMPLETED', created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE ai_security_reasoning_runs ENABLE ROW LEVEL SECURITY;
CREATE POLICY ai_reasoning_tenant ON ai_security_reasoning_runs USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V76__ai_security_reasoning.sql

-- ============================================================================
-- BEGIN V77__realtime_security_graph.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS realtime_security_graph_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, node_ref VARCHAR(512), event_type VARCHAR(128),
 relationship VARCHAR(128), risk_delta NUMERIC(8,3), payload JSONB NOT NULL DEFAULT '{}'::jsonb,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE realtime_security_graph_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY realtime_graph_tenant ON realtime_security_graph_events USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V77__realtime_security_graph.sql

-- ============================================================================
-- BEGIN V78__autonomous_detection_engineering.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS detection_engineering_rules (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, name VARCHAR(256) NOT NULL,
 rule_definition JSONB NOT NULL DEFAULT '{}'::jsonb, effectiveness_score NUMERIC(8,3),
 status VARCHAR(32) DEFAULT 'DRAFT', version INTEGER DEFAULT 1,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS detection_engineering_tests (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, rule_id UUID,
 test_type VARCHAR(128), result VARCHAR(32), coverage_score NUMERIC(8,3),
 evidence JSONB NOT NULL DEFAULT '{}'::jsonb, executed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE detection_engineering_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE detection_engineering_tests ENABLE ROW LEVEL SECURITY;
CREATE POLICY detection_rules_tenant ON detection_engineering_rules USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY detection_tests_tenant ON detection_engineering_tests USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V78__autonomous_detection_engineering.sql

-- ============================================================================
-- BEGIN V79__autonomous_response_engineering.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS response_engineering_playbooks (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, name VARCHAR(256) NOT NULL,
 definition JSONB NOT NULL DEFAULT '{}'::jsonb, safety_score NUMERIC(8,3),
 status VARCHAR(32) DEFAULT 'DRAFT', version INTEGER DEFAULT 1,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS response_engineering_tests (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, playbook_id UUID,
 simulation_result JSONB NOT NULL DEFAULT '{}'::jsonb, rollback_verified BOOLEAN DEFAULT false,
 executed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE response_engineering_playbooks ENABLE ROW LEVEL SECURITY;
ALTER TABLE response_engineering_tests ENABLE ROW LEVEL SECURITY;
CREATE POLICY response_playbooks_tenant ON response_engineering_playbooks USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY response_tests_tenant ON response_engineering_tests USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V79__autonomous_response_engineering.sql

-- ============================================================================
-- BEGIN V80__security_digital_twin_2.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS security_twin_models (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, model_name VARCHAR(256) NOT NULL,
 model_version VARCHAR(64), topology JSONB NOT NULL DEFAULT '{}'::jsonb,
 fidelity_score NUMERIC(8,3), status VARCHAR(32) DEFAULT 'ACTIVE',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,model_name,model_version)
);
CREATE TABLE IF NOT EXISTS security_twin_experiments (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, model_id UUID, experiment JSONB NOT NULL DEFAULT '{}'::jsonb,
 predicted_risk NUMERIC(8,3), predicted_blast_radius NUMERIC(8,3), result JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE security_twin_models ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_twin_experiments ENABLE ROW LEVEL SECURITY;
CREATE POLICY twin_models_tenant ON security_twin_models USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY twin_experiments_tenant ON security_twin_experiments USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V80__security_digital_twin_2.sql

-- ============================================================================
-- BEGIN V81__cross_cloud_autonomous_defense.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS cross_cloud_accounts (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, provider VARCHAR(64), account_ref VARCHAR(256),
 trust_score NUMERIC(8,3), capabilities JSONB NOT NULL DEFAULT '{}'::jsonb, status VARCHAR(32) DEFAULT 'ACTIVE',
 UNIQUE(tenant_id,provider,account_ref)
);
CREATE TABLE IF NOT EXISTS cross_cloud_defense_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, account_id UUID, event_type VARCHAR(128),
 risk_score NUMERIC(8,3), correlated_ref VARCHAR(512), evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE cross_cloud_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE cross_cloud_defense_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY cross_cloud_accounts_tenant ON cross_cloud_accounts USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY cross_cloud_events_tenant ON cross_cloud_defense_events USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V81__cross_cloud_autonomous_defense.sql

-- ============================================================================
-- BEGIN V82__global_threat_intelligence_network.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS global_threat_intel_sources (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, source_ref VARCHAR(512), trust_score NUMERIC(8,3),
 feed_type VARCHAR(128), metadata JSONB NOT NULL DEFAULT '{}'::jsonb, last_seen TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS global_threat_intel_signals (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, source_id UUID, indicator VARCHAR(512),
 indicator_type VARCHAR(64), confidence NUMERIC(6,3), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
ALTER TABLE global_threat_intel_sources ENABLE ROW LEVEL SECURITY;
ALTER TABLE global_threat_intel_signals ENABLE ROW LEVEL SECURITY;
CREATE POLICY global_ti_sources_tenant ON global_threat_intel_sources USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY global_ti_signals_tenant ON global_threat_intel_signals USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V82__global_threat_intelligence_network.sql

-- ============================================================================
-- BEGIN V83__enterprise_security_ai_governance.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS security_ai_governance_policies (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, policy_name VARCHAR(256) NOT NULL,
 policy_definition JSONB NOT NULL DEFAULT '{}'::jsonb, risk_tier VARCHAR(32),
 human_approval_required BOOLEAN DEFAULT true, status VARCHAR(32) DEFAULT 'ACTIVE',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,policy_name)
);
CREATE TABLE IF NOT EXISTS security_ai_governance_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, policy_id UUID, model_ref VARCHAR(256),
 decision_type VARCHAR(128), input_hash VARCHAR(128), output_hash VARCHAR(128),
 approved_by VARCHAR(256), evidence JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE security_ai_governance_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_ai_governance_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY ai_gov_policies_tenant ON security_ai_governance_policies USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY ai_gov_events_tenant ON security_ai_governance_events USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V83__enterprise_security_ai_governance.sql

-- ============================================================================
-- BEGIN V84__platform_certification_validation.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS platform_validation_runs (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, release_ref VARCHAR(128), test_suite VARCHAR(256),
 result VARCHAR(32), score NUMERIC(8,3), evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
 executed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS platform_validation_controls (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, control_ref VARCHAR(256), control_name VARCHAR(512),
 required BOOLEAN DEFAULT true, status VARCHAR(32) DEFAULT 'PENDING', evidence JSONB NOT NULL DEFAULT '{}'::jsonb
);
ALTER TABLE platform_validation_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_validation_controls ENABLE ROW LEVEL SECURITY;
CREATE POLICY validation_runs_tenant ON platform_validation_runs USING (tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY validation_controls_tenant ON platform_validation_controls USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V84__platform_certification_validation.sql

-- ============================================================================
-- BEGIN V85__enterprise_autonomous_cyber_defense_platform.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS enterprise_cyber_defense_platform_cycles (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, release_ref VARCHAR(128),
 platform_score NUMERIC(8,3), threat_level VARCHAR(32), intelligence_state VARCHAR(64),
 lifecycle JSONB NOT NULL DEFAULT '[]'::jsonb, active_domains JSONB NOT NULL DEFAULT '[]'::jsonb,
 recommendations JSONB NOT NULL DEFAULT '[]'::jsonb, governance JSONB NOT NULL DEFAULT '{}'::jsonb,
 status VARCHAR(32) DEFAULT 'ACTIVE', started_at TIMESTAMPTZ NOT NULL DEFAULT now(), completed_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS enterprise_cyber_defense_metrics (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, cycle_id UUID,
 prevention_score NUMERIC(8,3), detection_score NUMERIC(8,3), prediction_score NUMERIC(8,3),
 response_score NUMERIC(8,3), recovery_score NUMERIC(8,3), learning_score NUMERIC(8,3),
 measured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE enterprise_cyber_defense_platform_cycles ENABLE ROW LEVEL SECURITY;
ALTER TABLE enterprise_cyber_defense_metrics ENABLE ROW LEVEL SECURITY;
CREATE POLICY enterprise_platform_cycles_tenant ON enterprise_cyber_defense_platform_cycles USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY enterprise_platform_metrics_tenant ON enterprise_cyber_defense_metrics USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V85__enterprise_autonomous_cyber_defense_platform.sql

-- ============================================================================
-- BEGIN V86__v4_organization_federation.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS v4_security_organizations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, organization_ref VARCHAR(256) NOT NULL,
 parent_organization_id UUID, organization_type VARCHAR(64) DEFAULT 'ENTERPRISE',
 trust_boundary VARCHAR(64) DEFAULT 'ISOLATED', status VARCHAR(32) DEFAULT 'ACTIVE',
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,organization_ref)
);
CREATE TABLE IF NOT EXISTS v4_federation_relationships (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, source_org_id UUID NOT NULL, target_org_id UUID NOT NULL,
 relationship_type VARCHAR(64) NOT NULL, permissions JSONB NOT NULL DEFAULT '{}'::jsonb,
 expires_at TIMESTAMPTZ, status VARCHAR(32) DEFAULT 'ACTIVE', created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE v4_security_organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_federation_relationships ENABLE ROW LEVEL SECURITY;
CREATE POLICY v4_org_tenant ON v4_security_organizations USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY v4_federation_tenant ON v4_federation_relationships USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V86__v4_organization_federation.sql

-- ============================================================================
-- BEGIN V87__v4_event_fabric.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS v4_security_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, event_id VARCHAR(256) NOT NULL,
 source_type VARCHAR(128), source_ref VARCHAR(512), event_type VARCHAR(256) NOT NULL,
 schema_version VARCHAR(64) DEFAULT '4.0', occurred_at TIMESTAMPTZ NOT NULL,
 received_at TIMESTAMPTZ NOT NULL DEFAULT now(), correlation_id VARCHAR(256),
 trace_id VARCHAR(256), severity VARCHAR(32), payload JSONB NOT NULL DEFAULT '{}'::jsonb,
 provenance JSONB NOT NULL DEFAULT '{}'::jsonb, UNIQUE(tenant_id,event_id)
);
CREATE INDEX IF NOT EXISTS idx_v4_events_tenant_time ON v4_security_events(tenant_id,occurred_at);
ALTER TABLE v4_security_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY v4_events_tenant ON v4_security_events USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V87__v4_event_fabric.sql

-- ============================================================================
-- BEGIN V88__v4_evidence_provenance.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS v4_evidence_objects (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, evidence_ref VARCHAR(512) NOT NULL,
 evidence_type VARCHAR(128), source_ref VARCHAR(512), content_hash VARCHAR(128) NOT NULL,
 collected_at TIMESTAMPTZ, chain_of_custody JSONB NOT NULL DEFAULT '{}'::jsonb,
 classification VARCHAR(64), integrity_status VARCHAR(32) DEFAULT 'UNVERIFIED',
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,evidence_ref)
);
CREATE TABLE IF NOT EXISTS v4_evidence_links (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, evidence_id UUID NOT NULL,
 subject_ref VARCHAR(512), relationship VARCHAR(128), linked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE v4_evidence_objects ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_evidence_links ENABLE ROW LEVEL SECURITY;
CREATE POLICY v4_evidence_tenant ON v4_evidence_objects USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY v4_evidence_links_tenant ON v4_evidence_links USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V88__v4_evidence_provenance.sql

-- ============================================================================
-- BEGIN V89__v4_governed_agents.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS v4_agents (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, agent_ref VARCHAR(256) NOT NULL,
 agent_type VARCHAR(128), trust_level VARCHAR(32) DEFAULT 'BOUNDED',
 status VARCHAR(32) DEFAULT 'ACTIVE', identity_ref VARCHAR(512),
 policy_ref VARCHAR(512), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,agent_ref)
);
CREATE TABLE IF NOT EXISTS v4_agent_capabilities (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, agent_id UUID NOT NULL,
 capability VARCHAR(256) NOT NULL, scope JSONB NOT NULL DEFAULT '{}'::jsonb,
 approval_mode VARCHAR(64) DEFAULT 'REQUIRED', expires_at TIMESTAMPTZ,
 UNIQUE(tenant_id,agent_id,capability)
);
ALTER TABLE v4_agents ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_agent_capabilities ENABLE ROW LEVEL SECURITY;
CREATE POLICY v4_agents_tenant ON v4_agents USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY v4_agent_caps_tenant ON v4_agent_capabilities USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V89__v4_governed_agents.sql

-- ============================================================================
-- BEGIN V90__v4_policy_decision_plane.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS v4_policy_decisions (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, subject_ref VARCHAR(512), action VARCHAR(256),
 resource_ref VARCHAR(512), policy_ref VARCHAR(512), decision VARCHAR(32) NOT NULL,
 reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb, input_hash VARCHAR(128),
 decided_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_v4_policy_decisions_tenant_time ON v4_policy_decisions(tenant_id,decided_at);
ALTER TABLE v4_policy_decisions ENABLE ROW LEVEL SECURITY;
CREATE POLICY v4_policy_decisions_tenant ON v4_policy_decisions USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V90__v4_policy_decision_plane.sql

-- ============================================================================
-- BEGIN V91__v4_stream_processing.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS v4_stream_checkpoints (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, stream_ref VARCHAR(256) NOT NULL,
 partition_ref VARCHAR(256), sequence_ref VARCHAR(256), checkpoint_hash VARCHAR(128),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,stream_ref,partition_ref)
);
CREATE TABLE IF NOT EXISTS v4_stream_dead_letters (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, stream_ref VARCHAR(256), event_ref VARCHAR(512),
 failure_code VARCHAR(128), payload JSONB NOT NULL DEFAULT '{}'::jsonb,
 first_failed_at TIMESTAMPTZ NOT NULL DEFAULT now(), retry_count INTEGER DEFAULT 0
);
ALTER TABLE v4_stream_checkpoints ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_stream_dead_letters ENABLE ROW LEVEL SECURITY;
CREATE POLICY v4_stream_checkpoints_tenant ON v4_stream_checkpoints USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY v4_stream_dlq_tenant ON v4_stream_dead_letters USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V91__v4_stream_processing.sql

-- ============================================================================
-- BEGIN V92__v4_intelligence_context.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS v4_intelligence_contexts (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, context_ref VARCHAR(512) NOT NULL,
 subject_ref VARCHAR(512), context_type VARCHAR(128), graph_snapshot_ref VARCHAR(512),
 evidence_refs JSONB NOT NULL DEFAULT '[]'::jsonb, hypotheses JSONB NOT NULL DEFAULT '[]'::jsonb,
 risk_score NUMERIC(8,3), confidence NUMERIC(8,3),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,context_ref)
);
ALTER TABLE v4_intelligence_contexts ENABLE ROW LEVEL SECURITY;
CREATE POLICY v4_intel_context_tenant ON v4_intelligence_contexts USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V92__v4_intelligence_context.sql

-- ============================================================================
-- BEGIN V93__v4_execution_contracts.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS v4_execution_contracts (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, contract_ref VARCHAR(256) NOT NULL,
 capability VARCHAR(256) NOT NULL, target_ref VARCHAR(512), request_hash VARCHAR(128),
 idempotency_key VARCHAR(256) NOT NULL, approval_ref VARCHAR(512),
 rollback_contract JSONB NOT NULL DEFAULT '{}'::jsonb, status VARCHAR(32) DEFAULT 'PROPOSED',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,idempotency_key)
);
CREATE TABLE IF NOT EXISTS v4_execution_results (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, contract_id UUID NOT NULL,
 executor_ref VARCHAR(512), result VARCHAR(32), output JSONB NOT NULL DEFAULT '{}'::jsonb,
 rollback_status VARCHAR(32), executed_at TIMESTAMPTZ
);
ALTER TABLE v4_execution_contracts ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_execution_results ENABLE ROW LEVEL SECURITY;
CREATE POLICY v4_exec_contracts_tenant ON v4_execution_contracts USING (tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY v4_exec_results_tenant ON v4_execution_results USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V93__v4_execution_contracts.sql

-- ============================================================================
-- BEGIN V94__v4_trust_attestation.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS v4_trust_attestations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, subject_ref VARCHAR(512) NOT NULL,
 attestation_type VARCHAR(128), issuer_ref VARCHAR(512), evidence_hash VARCHAR(128),
 trust_score NUMERIC(8,3), valid_from TIMESTAMPTZ, valid_until TIMESTAMPTZ,
 status VARCHAR(32) DEFAULT 'ACTIVE', claims JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE v4_trust_attestations ENABLE ROW LEVEL SECURITY;
CREATE POLICY v4_attestation_tenant ON v4_trust_attestations USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V94__v4_trust_attestation.sql

-- ============================================================================
-- BEGIN V95__v4_platform_control_plane.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS v4_platform_cells (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, cell_ref VARCHAR(256) NOT NULL,
 region VARCHAR(128), control_plane_ref VARCHAR(512), data_plane_ref VARCHAR(512),
 intelligence_plane_ref VARCHAR(512), agent_plane_ref VARCHAR(512),
 execution_plane_ref VARCHAR(512), trust_plane_ref VARCHAR(512),
 health_status VARCHAR(32) DEFAULT 'HEALTHY', capabilities JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,cell_ref)
);
CREATE TABLE IF NOT EXISTS v4_platform_cycles (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, cell_id UUID,
 cycle_ref VARCHAR(256) NOT NULL, platform_state VARCHAR(64),
 decisions_count INTEGER DEFAULT 0, blocked_actions_count INTEGER DEFAULT 0,
 approved_actions_count INTEGER DEFAULT 0, evidence_count INTEGER DEFAULT 0,
 risk_score NUMERIC(8,3), health_score NUMERIC(8,3),
 started_at TIMESTAMPTZ NOT NULL DEFAULT now(), completed_at TIMESTAMPTZ
);
ALTER TABLE v4_platform_cells ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_platform_cycles ENABLE ROW LEVEL SECURITY;
CREATE POLICY v4_cells_tenant ON v4_platform_cells USING (tenant_id::text=current_setting('app.tenant_id',true));
CREATE POLICY v4_cycles_tenant ON v4_platform_cycles USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V95__v4_platform_control_plane.sql

-- ============================================================================
-- BEGIN V96__v4_1_knowledge_graph.sql
-- ============================================================================
-- 4.1.0 — Defense Knowledge Graph
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_knowledge_nodes (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, node_ref VARCHAR(512) NOT NULL,
 node_type VARCHAR(128) NOT NULL, external_ref VARCHAR(512), attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
 confidence NUMERIC(8,3), valid_from TIMESTAMPTZ, valid_until TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,node_ref)
);
CREATE TABLE IF NOT EXISTS v4_knowledge_edges (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, edge_ref VARCHAR(512) NOT NULL,
 source_ref VARCHAR(512) NOT NULL, target_ref VARCHAR(512) NOT NULL, relation_type VARCHAR(128) NOT NULL,
 evidence_refs JSONB NOT NULL DEFAULT '[]'::jsonb, confidence NUMERIC(8,
 3), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,edge_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_knowledge_nodes_tenant_created ON v4_knowledge_nodes(tenant_id,created_at);
CREATE INDEX IF NOT EXISTS idx_v4_knowledge_edges_tenant_created ON v4_knowledge_edges(tenant_id,created_at);

ALTER TABLE v4_knowledge_nodes ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_knowledge_edges ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_knowledge_nodes_tenant ON v4_knowledge_nodes;
CREATE POLICY v4_knowledge_nodes_tenant ON v4_knowledge_nodes USING (tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_knowledge_edges_tenant ON v4_knowledge_edges;
CREATE POLICY v4_knowledge_edges_tenant ON v4_knowledge_edges USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V96__v4_1_knowledge_graph.sql

-- ============================================================================
-- BEGIN V97__v4_2_threat_intelligence_fusion.sql
-- ============================================================================
-- 4.2.0 — Threat Intelligence Fusion
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_threat_intelligence_sources (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, source_ref VARCHAR(512) NOT NULL,
 source_type VARCHAR(128), trust_score NUMERIC(8,3), freshness_seconds INTEGER,
 sharing_policy VARCHAR(128), status VARCHAR(32) DEFAULT 'ACTIVE', metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,source_ref)
);
CREATE TABLE IF NOT EXISTS v4_threat_intelligence_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, observation_ref VARCHAR(512) NOT NULL,
 source_ref VARCHAR(512) NOT NULL, indicator_type VARCHAR(128), indicator_value VARCHAR(1024),
 confidence NUMERIC(8,3), observed_at TIMESTAMPTZ, expires_at TIMESTAMPTZ,
 context JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,observation_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_threat_intelligence_sources_tenant_created ON
v4_threat_intelligence_sources(tenant_id,
created_at);
CREATE INDEX IF NOT EXISTS idx_v4_threat_intelligence_observations_tenant_created ON
v4_threat_intelligence_observations(tenant_id,
created_at);

ALTER TABLE v4_threat_intelligence_sources ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_threat_intelligence_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_threat_intelligence_sources_tenant ON v4_threat_intelligence_sources;
CREATE POLICY v4_threat_intelligence_sources_tenant ON v4_threat_intelligence_sources USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_threat_intelligence_observations_tenant ON v4_threat_intelligence_observations;
CREATE POLICY v4_threat_intelligence_observations_tenant ON v4_threat_intelligence_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V97__v4_2_threat_intelligence_fusion.sql

-- ============================================================================
-- BEGIN V98__v4_3_predictive_defense.sql
-- ============================================================================
-- 4.3.0 — Predictive Defense
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_prediction_models (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, model_ref VARCHAR(512) NOT NULL,
 model_type VARCHAR(128), version VARCHAR(128), risk_domain VARCHAR(128),
 governance_status VARCHAR(64) DEFAULT 'APPROVED', metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,model_ref)
);
CREATE TABLE IF NOT EXISTS v4_prediction_runs (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, run_ref VARCHAR(512) NOT NULL,
 model_ref VARCHAR(512) NOT NULL, subject_ref VARCHAR(512), forecast JSONB NOT NULL DEFAULT '{}'::jsonb,
 confidence NUMERIC(8,3), horizon_seconds INTEGER, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,run_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_prediction_models_tenant_created ON v4_prediction_models(tenant_id,created_at);
CREATE INDEX IF NOT EXISTS idx_v4_prediction_runs_tenant_created ON v4_prediction_runs(tenant_id,created_at);

ALTER TABLE v4_prediction_models ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_prediction_runs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_prediction_models_tenant ON v4_prediction_models;
CREATE POLICY v4_prediction_models_tenant ON v4_prediction_models USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_prediction_runs_tenant ON v4_prediction_runs;
CREATE POLICY v4_prediction_runs_tenant ON v4_prediction_runs USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V98__v4_3_predictive_defense.sql

-- ============================================================================
-- BEGIN V99__v4_4_adaptive_policy.sql
-- ============================================================================
-- 4.4.0 — Adaptive Policy
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_policy_adaptation_proposals (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, proposal_ref VARCHAR(512) NOT NULL,
 policy_ref VARCHAR(512) NOT NULL, trigger_ref VARCHAR(512), proposed_change JSONB NOT NULL DEFAULT '{}'::jsonb,
 risk_delta NUMERIC(8,3), status VARCHAR(32) DEFAULT 'PROPOSED', created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,proposal_ref)
);
CREATE TABLE IF NOT EXISTS v4_policy_adaptation_decisions (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, decision_ref VARCHAR(512) NOT NULL,
 proposal_ref VARCHAR(512) NOT NULL, decision VARCHAR(64) NOT NULL, approver_ref VARCHAR(512),
 reason JSONB NOT NULL DEFAULT '{}'::jsonb, decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,decision_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_policy_adaptation_proposals_tenant_created ON
v4_policy_adaptation_proposals(tenant_id,
created_at);
CREATE INDEX IF NOT EXISTS idx_v4_policy_adaptation_decisions_tenant_created ON
v4_policy_adaptation_decisions(tenant_id,
decided_at);

ALTER TABLE v4_policy_adaptation_proposals ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_policy_adaptation_decisions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_policy_adaptation_proposals_tenant ON v4_policy_adaptation_proposals;
CREATE POLICY v4_policy_adaptation_proposals_tenant ON v4_policy_adaptation_proposals USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_policy_adaptation_decisions_tenant ON v4_policy_adaptation_decisions;
CREATE POLICY v4_policy_adaptation_decisions_tenant ON v4_policy_adaptation_decisions USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V99__v4_4_adaptive_policy.sql

-- ============================================================================
-- BEGIN V100__v4_5_autonomous_missions.sql
-- ============================================================================
-- 4.5.0 — Autonomous Mission Control
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_defense_missions (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, mission_ref VARCHAR(512) NOT NULL,
 objective VARCHAR(1024) NOT NULL, priority INTEGER DEFAULT 100, autonomy_mode VARCHAR(64) DEFAULT 'GOVERNED',
 status VARCHAR(32) DEFAULT 'PROPOSED', policy_ref VARCHAR(512), approval_ref VARCHAR(512),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,mission_ref)
);
CREATE TABLE IF NOT EXISTS v4_mission_steps (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, mission_id UUID NOT NULL,
 step_ref VARCHAR(512) NOT NULL, sequence_no INTEGER NOT NULL, capability VARCHAR(256),
 contract_ref VARCHAR(512), status VARCHAR(32) DEFAULT 'PENDING', verification JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,mission_id,
 step_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_defense_missions_tenant_created ON v4_defense_missions(tenant_id,created_at);
CREATE INDEX IF NOT EXISTS idx_v4_mission_steps_tenant_created ON v4_mission_steps(tenant_id,created_at);

ALTER TABLE v4_defense_missions ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_mission_steps ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_defense_missions_tenant ON v4_defense_missions;
CREATE POLICY v4_defense_missions_tenant ON v4_defense_missions USING (tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_mission_steps_tenant ON v4_mission_steps;
CREATE POLICY v4_mission_steps_tenant ON v4_mission_steps USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V100__v4_5_autonomous_missions.sql

-- ============================================================================
-- BEGIN V101__v4_6_multi_agent_coordination.sql
-- ============================================================================
-- 4.6.0 — Multi-Agent Coordination
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_agent_teams (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, team_ref VARCHAR(512) NOT NULL,
 objective VARCHAR(1024), coordination_policy VARCHAR(512), status VARCHAR(32) DEFAULT 'ACTIVE',
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,team_ref)
);
CREATE TABLE IF NOT EXISTS v4_agent_coordination_tasks (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, task_ref VARCHAR(512) NOT NULL,
 team_ref VARCHAR(512) NOT NULL, agent_ref VARCHAR(512), role VARCHAR(128),
 dependency_refs JSONB NOT NULL DEFAULT '[]'::jsonb, conflict_policy VARCHAR(128) DEFAULT 'BLOCK',
 status VARCHAR(32) DEFAULT 'PENDING', created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,task_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_agent_teams_tenant_created ON v4_agent_teams(tenant_id,created_at);
CREATE INDEX IF NOT EXISTS idx_v4_agent_coordination_tasks_tenant_created ON v4_agent_coordination_tasks(tenant_id,
created_at);

ALTER TABLE v4_agent_teams ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_agent_coordination_tasks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_agent_teams_tenant ON v4_agent_teams;
CREATE POLICY v4_agent_teams_tenant ON v4_agent_teams USING (tenant_id::text=current_setting('app.tenant_id',true));
DROP POLICY IF EXISTS v4_agent_coordination_tasks_tenant ON v4_agent_coordination_tasks;
CREATE POLICY v4_agent_coordination_tasks_tenant ON v4_agent_coordination_tasks USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V101__v4_6_multi_agent_coordination.sql

-- ============================================================================
-- BEGIN V102__v4_7_cross_domain_defense.sql
-- ============================================================================
-- 4.7.0 — Cross-Domain Defense
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_defense_domains (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, domain_ref VARCHAR(512) NOT NULL,
 domain_type VARCHAR(128) NOT NULL, control_plane_ref VARCHAR(512), trust_level VARCHAR(64) DEFAULT 'BOUNDED',
 status VARCHAR(32) DEFAULT 'ACTIVE', metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,domain_ref)
);
CREATE TABLE IF NOT EXISTS v4_domain_links (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, link_ref VARCHAR(512) NOT NULL,
 source_domain_ref VARCHAR(512) NOT NULL, target_domain_ref VARCHAR(512) NOT NULL,
 allowed_capabilities JSONB NOT NULL DEFAULT '[]'::jsonb, policy_ref VARCHAR(512),
 status VARCHAR(32) DEFAULT 'ACTIVE', created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,link_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_defense_domains_tenant_created ON v4_defense_domains(tenant_id,created_at);
CREATE INDEX IF NOT EXISTS idx_v4_domain_links_tenant_created ON v4_domain_links(tenant_id,created_at);

ALTER TABLE v4_defense_domains ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_domain_links ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_defense_domains_tenant ON v4_defense_domains;
CREATE POLICY v4_defense_domains_tenant ON v4_defense_domains USING (tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_domain_links_tenant ON v4_domain_links;
CREATE POLICY v4_domain_links_tenant ON v4_domain_links USING (tenant_id::text=current_setting('app.tenant_id',true));

-- END V102__v4_7_cross_domain_defense.sql

-- ============================================================================
-- BEGIN V103__v4_8_federated_intelligence.sql
-- ============================================================================
-- 4.8.0 — Federated Intelligence
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_intelligence_federations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, federation_ref VARCHAR(512) NOT NULL,
 peer_org_ref VARCHAR(512) NOT NULL, trust_requirement VARCHAR(128), data_classification VARCHAR(128),
 sharing_policy VARCHAR(512), status VARCHAR(32) DEFAULT 'ACTIVE', created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,federation_ref)
);
CREATE TABLE IF NOT EXISTS v4_intelligence_exchange_records (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, exchange_ref VARCHAR(512) NOT NULL,
 federation_ref VARCHAR(512) NOT NULL, direction VARCHAR(32) NOT NULL, intelligence_ref VARCHAR(512),
 content_hash VARCHAR(128), attestation_ref VARCHAR(512), status VARCHAR(32) DEFAULT 'RECEIVED',
 exchanged_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,exchange_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_intelligence_federations_tenant_created ON v4_intelligence_federations(tenant_id,
created_at);
CREATE INDEX IF NOT EXISTS idx_v4_intelligence_exchange_records_tenant_created ON
v4_intelligence_exchange_records(tenant_id,
exchanged_at);

ALTER TABLE v4_intelligence_federations ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_intelligence_exchange_records ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_intelligence_federations_tenant ON v4_intelligence_federations;
CREATE POLICY v4_intelligence_federations_tenant ON v4_intelligence_federations USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_intelligence_exchange_records_tenant ON v4_intelligence_exchange_records;
CREATE POLICY v4_intelligence_exchange_records_tenant ON v4_intelligence_exchange_records USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V103__v4_8_federated_intelligence.sql

-- ============================================================================
-- BEGIN V104__v4_9_continuous_defense_loop.sql
-- ============================================================================
-- 4.9.0 — Continuous Defense Loop
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_defense_cycles (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, cycle_ref VARCHAR(512) NOT NULL,
 detect_ref VARCHAR(512), understand_ref VARCHAR(512), decide_ref VARCHAR(512),
 execute_ref VARCHAR(512), verify_ref VARCHAR(512), status VARCHAR(32) DEFAULT 'RUNNING',
 started_at TIMESTAMPTZ NOT NULL DEFAULT now(), completed_at TIMESTAMPTZ,
 UNIQUE(tenant_id,cycle_ref)
);
CREATE TABLE IF NOT EXISTS v4_defense_feedback (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, feedback_ref VARCHAR(512) NOT NULL,
 cycle_ref VARCHAR(512) NOT NULL, outcome VARCHAR(64), learning JSONB NOT NULL DEFAULT '{}'::jsonb,
 evidence_refs JSONB NOT NULL DEFAULT '[]'::jsonb, applied_to_context BOOLEAN DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,feedback_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_defense_cycles_tenant_created ON v4_defense_cycles(tenant_id,started_at);
CREATE INDEX IF NOT EXISTS idx_v4_defense_feedback_tenant_created ON v4_defense_feedback(tenant_id,created_at);

ALTER TABLE v4_defense_cycles ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_defense_feedback ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_defense_cycles_tenant ON v4_defense_cycles;
CREATE POLICY v4_defense_cycles_tenant ON v4_defense_cycles USING (tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_defense_feedback_tenant ON v4_defense_feedback;
CREATE POLICY v4_defense_feedback_tenant ON v4_defense_feedback USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V104__v4_9_continuous_defense_loop.sql

-- ============================================================================
-- BEGIN V105__v4_10_autonomous_defense_governance.sql
-- ============================================================================
-- 4.10.0 — Autonomous Defense Governance
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_autonomy_profiles (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, profile_ref VARCHAR(512) NOT NULL,
 autonomy_level VARCHAR(64) NOT NULL, scope JSONB NOT NULL DEFAULT '{}'::jsonb,
 approval_threshold NUMERIC(8,3), status VARCHAR(32) DEFAULT 'ACTIVE', created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,profile_ref)
);
CREATE TABLE IF NOT EXISTS v4_autonomy_guardrails (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, guardrail_ref VARCHAR(512) NOT NULL,
 profile_ref VARCHAR(512) NOT NULL, capability VARCHAR(256), constraint_type VARCHAR(128),
 constraint_value JSONB NOT NULL DEFAULT '{}'::jsonb, enforcement VARCHAR(64) DEFAULT 'BLOCK',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,guardrail_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_autonomy_profiles_tenant_created ON v4_autonomy_profiles(tenant_id,created_at);
CREATE INDEX IF NOT EXISTS idx_v4_autonomy_guardrails_tenant_created ON v4_autonomy_guardrails(tenant_id,created_at);

ALTER TABLE v4_autonomy_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_autonomy_guardrails ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_autonomy_profiles_tenant ON v4_autonomy_profiles;
CREATE POLICY v4_autonomy_profiles_tenant ON v4_autonomy_profiles USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_autonomy_guardrails_tenant ON v4_autonomy_guardrails;
CREATE POLICY v4_autonomy_guardrails_tenant ON v4_autonomy_guardrails USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V105__v4_10_autonomous_defense_governance.sql

-- ============================================================================
-- BEGIN V106__v4_11_intelligence_mesh.sql
-- ============================================================================
-- 4.11.0 — Intelligence Mesh
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_intelligence_mesh_nodes (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, node_ref VARCHAR(512) NOT NULL,
 node_type VARCHAR(128), intelligence_domains JSONB NOT NULL DEFAULT '[]'::jsonb,
 trust_score NUMERIC(8,3), status VARCHAR(32) DEFAULT 'ACTIVE', created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,node_ref)
);
CREATE TABLE IF NOT EXISTS v4_intelligence_mesh_routes (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, route_ref VARCHAR(512) NOT NULL,
 source_node_ref VARCHAR(512) NOT NULL, target_node_ref VARCHAR(512) NOT NULL,
 topic VARCHAR(512), qos VARCHAR(64), policy_ref VARCHAR(512), status VARCHAR(32) DEFAULT 'ACTIVE',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,route_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_intelligence_mesh_nodes_tenant_created ON v4_intelligence_mesh_nodes(tenant_id,
created_at);
CREATE INDEX IF NOT EXISTS idx_v4_intelligence_mesh_routes_tenant_created ON v4_intelligence_mesh_routes(tenant_id,
created_at);

ALTER TABLE v4_intelligence_mesh_nodes ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_intelligence_mesh_routes ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_intelligence_mesh_nodes_tenant ON v4_intelligence_mesh_nodes;
CREATE POLICY v4_intelligence_mesh_nodes_tenant ON v4_intelligence_mesh_nodes USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_intelligence_mesh_routes_tenant ON v4_intelligence_mesh_routes;
CREATE POLICY v4_intelligence_mesh_routes_tenant ON v4_intelligence_mesh_routes USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V106__v4_11_intelligence_mesh.sql

-- ============================================================================
-- BEGIN V107__v4_12_mission_assurance.sql
-- ============================================================================
-- 4.12.0 — Mission Assurance
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_mission_assurance_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 mission_ref VARCHAR(512) NOT NULL, assurance_level VARCHAR(64) DEFAULT 'STANDARD',
 required_controls JSONB NOT NULL DEFAULT '[]'::jsonb, status VARCHAR(32) DEFAULT 'ACTIVE',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_mission_assurance_checks (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, check_ref VARCHAR(512) NOT NULL,
 plan_ref VARCHAR(512) NOT NULL, control_type VARCHAR(128) NOT NULL, result VARCHAR(64),
 evidence_ref VARCHAR(512), checked_at TIMESTAMPTZ, details JSONB NOT NULL DEFAULT '{}'::jsonb,
 UNIQUE(tenant_id,check_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_mission_assurance_plans_tenant_created ON v4_mission_assurance_plans(tenant_id,
created_at);
CREATE INDEX IF NOT EXISTS idx_v4_mission_assurance_checks_tenant_created ON v4_mission_assurance_checks(tenant_id,
checked_at);

ALTER TABLE v4_mission_assurance_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_mission_assurance_checks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_mission_assurance_plans_tenant ON v4_mission_assurance_plans;
CREATE POLICY v4_mission_assurance_plans_tenant ON v4_mission_assurance_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_mission_assurance_checks_tenant ON v4_mission_assurance_checks;
CREATE POLICY v4_mission_assurance_checks_tenant ON v4_mission_assurance_checks USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V107__v4_12_mission_assurance.sql

-- ============================================================================
-- BEGIN V108__v4_13_defense_simulation.sql
-- ============================================================================
-- 4.13.0 — Defense Simulation
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_simulation_scenarios (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, scenario_ref VARCHAR(512) NOT NULL,
 scenario_type VARCHAR(128), baseline_context JSONB NOT NULL DEFAULT '{}'::jsonb,
 attack_graph JSONB NOT NULL DEFAULT '{}'::jsonb, status VARCHAR(32) DEFAULT 'READY',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,scenario_ref)
);
CREATE TABLE IF NOT EXISTS v4_simulation_runs (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, run_ref VARCHAR(512) NOT NULL,
 scenario_ref VARCHAR(512) NOT NULL, seed VARCHAR(256), outcome JSONB NOT NULL DEFAULT '{}'::jsonb,
 counterfactuals JSONB NOT NULL DEFAULT '[]'::jsonb, started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 completed_at TIMESTAMPTZ, UNIQUE(tenant_id,run_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_simulation_scenarios_tenant_created ON v4_simulation_scenarios(tenant_id,created_at);
CREATE INDEX IF NOT EXISTS idx_v4_simulation_runs_tenant_created ON v4_simulation_runs(tenant_id,started_at);

ALTER TABLE v4_simulation_scenarios ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_simulation_runs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_simulation_scenarios_tenant ON v4_simulation_scenarios;
CREATE POLICY v4_simulation_scenarios_tenant ON v4_simulation_scenarios USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_simulation_runs_tenant ON v4_simulation_runs;
CREATE POLICY v4_simulation_runs_tenant ON v4_simulation_runs USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V108__v4_13_defense_simulation.sql

-- ============================================================================
-- BEGIN V109__v4_14_defense_optimization.sql
-- ============================================================================
-- 4.14.0 — Defense Optimization
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_optimization_objectives (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, objective_ref VARCHAR(512) NOT NULL,
 objective_type VARCHAR(128), weights JSONB NOT NULL DEFAULT '{}'::jsonb,
 constraints JSONB NOT NULL DEFAULT '{}'::jsonb, status VARCHAR(32) DEFAULT 'ACTIVE',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,objective_ref)
);
CREATE TABLE IF NOT EXISTS v4_optimization_runs (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, run_ref VARCHAR(512) NOT NULL,
 objective_ref VARCHAR(512) NOT NULL, candidate_set JSONB NOT NULL DEFAULT '[]'::jsonb,
 selected_plan JSONB NOT NULL DEFAULT '{}'::jsonb, score NUMERIC(12,4),
 rationale JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,run_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_optimization_objectives_tenant_created ON v4_optimization_objectives(tenant_id,
created_at);
CREATE INDEX IF NOT EXISTS idx_v4_optimization_runs_tenant_created ON v4_optimization_runs(tenant_id,created_at);

ALTER TABLE v4_optimization_objectives ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_optimization_runs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_optimization_objectives_tenant ON v4_optimization_objectives;
CREATE POLICY v4_optimization_objectives_tenant ON v4_optimization_objectives USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_optimization_runs_tenant ON v4_optimization_runs;
CREATE POLICY v4_optimization_runs_tenant ON v4_optimization_runs USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V109__v4_14_defense_optimization.sql

-- ============================================================================
-- BEGIN V110__v4_15_trust_federation.sql
-- ============================================================================
-- 4.15.0 — Trust Federation
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_trust_domains (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, domain_ref VARCHAR(512) NOT NULL,
 issuer_ref VARCHAR(512) NOT NULL, trust_model VARCHAR(128), assurance_level VARCHAR(64),
 status VARCHAR(32) DEFAULT 'ACTIVE', metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,domain_ref)
);
CREATE TABLE IF NOT EXISTS v4_trust_assertions (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, assertion_ref VARCHAR(512) NOT NULL,
 domain_ref VARCHAR(512) NOT NULL, subject_ref VARCHAR(512) NOT NULL, claim JSONB NOT NULL DEFAULT '{}'::jsonb,
 evidence_ref VARCHAR(512), expires_at TIMESTAMPTZ, status VARCHAR(32) DEFAULT 'ACTIVE',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,assertion_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_trust_domains_tenant_created ON v4_trust_domains(tenant_id,created_at);
CREATE INDEX IF NOT EXISTS idx_v4_trust_assertions_tenant_created ON v4_trust_assertions(tenant_id,created_at);

ALTER TABLE v4_trust_domains ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_trust_assertions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_trust_domains_tenant ON v4_trust_domains;
CREATE POLICY v4_trust_domains_tenant ON v4_trust_domains USING (tenant_id::text=current_setting('app.tenant_id',true));
DROP POLICY IF EXISTS v4_trust_assertions_tenant ON v4_trust_assertions;
CREATE POLICY v4_trust_assertions_tenant ON v4_trust_assertions USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V110__v4_15_trust_federation.sql

-- ============================================================================
-- BEGIN V111__v4_16_data_sovereignty.sql
-- ============================================================================
-- 4.16.0 — Data Sovereignty
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_data_residency_policies (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, policy_ref VARCHAR(512) NOT NULL,
 jurisdiction VARCHAR(128) NOT NULL, classification VARCHAR(128), residency_rule JSONB NOT NULL DEFAULT '{}'::jsonb,
 transfer_rule JSONB NOT NULL DEFAULT '{}'::jsonb, status VARCHAR(32) DEFAULT 'ACTIVE',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,policy_ref)
);
CREATE TABLE IF NOT EXISTS v4_data_access_decisions (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, decision_ref VARCHAR(512) NOT NULL,
 policy_ref VARCHAR(512) NOT NULL, subject_ref VARCHAR(512), data_ref VARCHAR(512),
 decision VARCHAR(64) NOT NULL, reason JSONB NOT NULL DEFAULT '{}'::jsonb,
 decided_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,decision_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_data_residency_policies_tenant_created ON v4_data_residency_policies(tenant_id,
created_at);
CREATE INDEX IF NOT EXISTS idx_v4_data_access_decisions_tenant_created ON v4_data_access_decisions(tenant_id,
decided_at);

ALTER TABLE v4_data_residency_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_data_access_decisions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_data_residency_policies_tenant ON v4_data_residency_policies;
CREATE POLICY v4_data_residency_policies_tenant ON v4_data_residency_policies USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_data_access_decisions_tenant ON v4_data_access_decisions;
CREATE POLICY v4_data_access_decisions_tenant ON v4_data_access_decisions USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V111__v4_16_data_sovereignty.sql

-- ============================================================================
-- BEGIN V112__v4_17_resilient_defense.sql
-- ============================================================================
-- 4.17.0 — Resilient Defense
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_resilience_profiles (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, profile_ref VARCHAR(512) NOT NULL,
 failure_domains JSONB NOT NULL DEFAULT '[]'::jsonb, recovery_targets JSONB NOT NULL DEFAULT '{}'::jsonb,
 autonomy_fallback VARCHAR(64) DEFAULT 'SAFE', status VARCHAR(32) DEFAULT 'ACTIVE',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,profile_ref)
);
CREATE TABLE IF NOT EXISTS v4_degraded_mode_events (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, event_ref VARCHAR(512) NOT NULL,
 profile_ref VARCHAR(512), failure_domain VARCHAR(256), mode VARCHAR(64) NOT NULL,
 reason JSONB NOT NULL DEFAULT '{}'::jsonb, entered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 exited_at TIMESTAMPTZ, UNIQUE(tenant_id,event_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_resilience_profiles_tenant_created ON v4_resilience_profiles(tenant_id,created_at);
CREATE INDEX IF NOT EXISTS idx_v4_degraded_mode_events_tenant_created ON v4_degraded_mode_events(tenant_id,entered_at);

ALTER TABLE v4_resilience_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_degraded_mode_events ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_resilience_profiles_tenant ON v4_resilience_profiles;
CREATE POLICY v4_resilience_profiles_tenant ON v4_resilience_profiles USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_degraded_mode_events_tenant ON v4_degraded_mode_events;
CREATE POLICY v4_degraded_mode_events_tenant ON v4_degraded_mode_events USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V112__v4_17_resilient_defense.sql

-- ============================================================================
-- BEGIN V113__v4_18_explainability_assurance.sql
-- ============================================================================
-- 4.18.0 — Explainability & Assurance
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_assurance_profiles (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, profile_ref VARCHAR(512) NOT NULL,
 required_artifacts JSONB NOT NULL DEFAULT '[]'::jsonb, minimum_confidence NUMERIC(8,
 3), audit_retention_days INTEGER DEFAULT 2555, status VARCHAR(32) DEFAULT 'ACTIVE',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,profile_ref)
);
CREATE TABLE IF NOT EXISTS v4_decision_explanations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, explanation_ref VARCHAR(512) NOT NULL,
 decision_ref VARCHAR(512) NOT NULL, rationale JSONB NOT NULL DEFAULT '{}'::jsonb,
 evidence_refs JSONB NOT NULL DEFAULT '[]'::jsonb, model_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
 generated_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(tenant_id,explanation_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_assurance_profiles_tenant_created ON v4_assurance_profiles(tenant_id,created_at);
CREATE INDEX IF NOT EXISTS idx_v4_decision_explanations_tenant_created ON v4_decision_explanations(tenant_id,
generated_at);

ALTER TABLE v4_assurance_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_decision_explanations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_assurance_profiles_tenant ON v4_assurance_profiles;
CREATE POLICY v4_assurance_profiles_tenant ON v4_assurance_profiles USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_decision_explanations_tenant ON v4_decision_explanations;
CREATE POLICY v4_decision_explanations_tenant ON v4_decision_explanations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V113__v4_18_explainability_assurance.sql

-- ============================================================================
-- BEGIN V114__v4_19_global_policy_orchestration.sql
-- ============================================================================
-- 4.19.0 — Global Policy Orchestration
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_policy_federation_rules (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, rule_ref VARCHAR(512) NOT NULL,
 policy_ref VARCHAR(512) NOT NULL, target_domains JSONB NOT NULL DEFAULT '[]'::jsonb,
 precedence INTEGER DEFAULT 100, conflict_strategy VARCHAR(64) DEFAULT 'DENY',
 status VARCHAR(32) DEFAULT 'ACTIVE', created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,rule_ref)
);
CREATE TABLE IF NOT EXISTS v4_policy_orchestration_runs (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, run_ref VARCHAR(512) NOT NULL,
 trigger_ref VARCHAR(512), rule_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
 decisions JSONB NOT NULL DEFAULT '[]'::jsonb, conflict_count INTEGER DEFAULT 0,
 status VARCHAR(32) DEFAULT 'COMPLETED', created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,run_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_policy_federation_rules_tenant_created ON v4_policy_federation_rules(tenant_id,
created_at);
CREATE INDEX IF NOT EXISTS idx_v4_policy_orchestration_runs_tenant_created ON v4_policy_orchestration_runs(tenant_id,
created_at);

ALTER TABLE v4_policy_federation_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_policy_orchestration_runs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_policy_federation_rules_tenant ON v4_policy_federation_rules;
CREATE POLICY v4_policy_federation_rules_tenant ON v4_policy_federation_rules USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_policy_orchestration_runs_tenant ON v4_policy_orchestration_runs;
CREATE POLICY v4_policy_orchestration_runs_tenant ON v4_policy_orchestration_runs USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V114__v4_19_global_policy_orchestration.sql

-- ============================================================================
-- BEGIN V115__v4_20_autonomous_cyber_defense_intelligence_infrastructure.sql
-- ============================================================================
-- 4.20.0 — Autonomous Cyber Defense Intelligence Infrastructure
-- ADDITIVE extension of the 4.0 Cyber Defense Intelligence Infrastructure.
-- Tenant isolation is mandatory;
-- no 3.x table is altered or removed.

CREATE TABLE IF NOT EXISTS v4_operating_cells (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, cell_ref VARCHAR(512) NOT NULL,
 operating_model VARCHAR(128) DEFAULT 'GOVERNED_AUTONOMOUS', plane_health JSONB NOT NULL DEFAULT '{}'::jsonb,
 capability_catalog JSONB NOT NULL DEFAULT '[]'::jsonb, sovereignty JSONB NOT NULL DEFAULT '{}'::jsonb,
 status VARCHAR(32) DEFAULT 'ACTIVE', created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,cell_ref)
);
CREATE TABLE IF NOT EXISTS v4_operating_cycles (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, cycle_ref VARCHAR(512) NOT NULL,
 cell_ref VARCHAR(512) NOT NULL, mission_ref VARCHAR(512), intelligence_context_ref VARCHAR(512),
 policy_decision_ref VARCHAR(512), execution_contract_ref VARCHAR(512),
 verification_ref VARCHAR(512), evidence_ref VARCHAR(512), learning_ref VARCHAR(512),
 state VARCHAR(64) DEFAULT 'RUNNING', started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 completed_at TIMESTAMPTZ, UNIQUE(tenant_id,cycle_ref)
);

CREATE INDEX IF NOT EXISTS idx_v4_operating_cells_tenant_created ON v4_operating_cells(tenant_id,created_at);
CREATE INDEX IF NOT EXISTS idx_v4_operating_cycles_tenant_created ON v4_operating_cycles(tenant_id,started_at);

ALTER TABLE v4_operating_cells ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_operating_cycles ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_operating_cells_tenant ON v4_operating_cells;
CREATE POLICY v4_operating_cells_tenant ON v4_operating_cells USING (tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_operating_cycles_tenant ON v4_operating_cycles;
CREATE POLICY v4_operating_cycles_tenant ON v4_operating_cycles USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V115__v4_20_autonomous_cyber_defense_intelligence_infrastructure.sql

-- ============================================================================
-- BEGIN V116__v4_21_autonomous_risk_orchestration.sql
-- ============================================================================
-- Autonomous Risk Orchestration (4_21.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_risk_orchestration_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_risk_orchestration_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_risk_orchestration_plans_tenant_updated ON v4_risk_orchestration_plans(tenant_id,
updated_at);
CREATE INDEX IF NOT EXISTS idx_risk_orchestration_observations_tenant_observed ON
v4_risk_orchestration_observations(tenant_id,
observed_at);
ALTER TABLE v4_risk_orchestration_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_risk_orchestration_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_risk_orchestration_plans_tenant ON v4_risk_orchestration_plans;
CREATE POLICY v4_risk_orchestration_plans_tenant ON v4_risk_orchestration_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_risk_orchestration_observations_tenant ON v4_risk_orchestration_observations;
CREATE POLICY v4_risk_orchestration_observations_tenant ON v4_risk_orchestration_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V116__v4_21_autonomous_risk_orchestration.sql

-- ============================================================================
-- BEGIN V117__v4_22_attack_path_intelligence.sql
-- ============================================================================
-- Attack Path Intelligence (4_22.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_attack_path_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_attack_path_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_attack_path_plans_tenant_updated ON v4_attack_path_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_attack_path_observations_tenant_observed ON v4_attack_path_observations(tenant_id,
observed_at);
ALTER TABLE v4_attack_path_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_attack_path_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_attack_path_plans_tenant ON v4_attack_path_plans;
CREATE POLICY v4_attack_path_plans_tenant ON v4_attack_path_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_attack_path_observations_tenant ON v4_attack_path_observations;
CREATE POLICY v4_attack_path_observations_tenant ON v4_attack_path_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V117__v4_22_attack_path_intelligence.sql

-- ============================================================================
-- BEGIN V118__v4_23_behavioral_intelligence.sql
-- ============================================================================
-- Behavioral Intelligence (4_23.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_behavioral_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_behavioral_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_behavioral_plans_tenant_updated ON v4_behavioral_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_behavioral_observations_tenant_observed ON v4_behavioral_observations(tenant_id,
observed_at);
ALTER TABLE v4_behavioral_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_behavioral_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_behavioral_plans_tenant ON v4_behavioral_plans;
CREATE POLICY v4_behavioral_plans_tenant ON v4_behavioral_plans USING (tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_behavioral_observations_tenant ON v4_behavioral_observations;
CREATE POLICY v4_behavioral_observations_tenant ON v4_behavioral_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V118__v4_23_behavioral_intelligence.sql

-- ============================================================================
-- BEGIN V119__v4_24_threat_prediction_engine.sql
-- ============================================================================
-- Threat Prediction Engine (4_24.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_prediction_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_prediction_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_prediction_plans_tenant_updated ON v4_prediction_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_prediction_observations_tenant_observed ON v4_prediction_observations(tenant_id,
observed_at);
ALTER TABLE v4_prediction_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_prediction_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_prediction_plans_tenant ON v4_prediction_plans;
CREATE POLICY v4_prediction_plans_tenant ON v4_prediction_plans USING (tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_prediction_observations_tenant ON v4_prediction_observations;
CREATE POLICY v4_prediction_observations_tenant ON v4_prediction_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V119__v4_24_threat_prediction_engine.sql

-- ============================================================================
-- BEGIN V120__v4_25_adaptive_defense_strategy.sql
-- ============================================================================
-- Adaptive Defense Strategy (4_25.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_defense_strategy_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_defense_strategy_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_defense_strategy_plans_tenant_updated ON v4_defense_strategy_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_defense_strategy_observations_tenant_observed ON
v4_defense_strategy_observations(tenant_id,
observed_at);
ALTER TABLE v4_defense_strategy_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_defense_strategy_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_defense_strategy_plans_tenant ON v4_defense_strategy_plans;
CREATE POLICY v4_defense_strategy_plans_tenant ON v4_defense_strategy_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_defense_strategy_observations_tenant ON v4_defense_strategy_observations;
CREATE POLICY v4_defense_strategy_observations_tenant ON v4_defense_strategy_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V120__v4_25_adaptive_defense_strategy.sql

-- ============================================================================
-- BEGIN V121__v4_26_agent_memory_learning.sql
-- ============================================================================
-- Agent Memory & Learning (4_26.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_agent_memory_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_agent_memory_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agent_memory_plans_tenant_updated ON v4_agent_memory_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_agent_memory_observations_tenant_observed ON v4_agent_memory_observations(tenant_id,
observed_at);
ALTER TABLE v4_agent_memory_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_agent_memory_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_agent_memory_plans_tenant ON v4_agent_memory_plans;
CREATE POLICY v4_agent_memory_plans_tenant ON v4_agent_memory_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_agent_memory_observations_tenant ON v4_agent_memory_observations;
CREATE POLICY v4_agent_memory_observations_tenant ON v4_agent_memory_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V121__v4_26_agent_memory_learning.sql

-- ============================================================================
-- BEGIN V122__v4_27_agent_skill_governance.sql
-- ============================================================================
-- Agent Skill Governance (4_27.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_agent_skill_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_agent_skill_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agent_skill_plans_tenant_updated ON v4_agent_skill_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_agent_skill_observations_tenant_observed ON v4_agent_skill_observations(tenant_id,
observed_at);
ALTER TABLE v4_agent_skill_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_agent_skill_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_agent_skill_plans_tenant ON v4_agent_skill_plans;
CREATE POLICY v4_agent_skill_plans_tenant ON v4_agent_skill_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_agent_skill_observations_tenant ON v4_agent_skill_observations;
CREATE POLICY v4_agent_skill_observations_tenant ON v4_agent_skill_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V122__v4_27_agent_skill_governance.sql

-- ============================================================================
-- BEGIN V123__v4_28_collective_multi_agent_intelligence.sql
-- ============================================================================
-- Collective Multi-Agent Intelligence (4_28.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_multi_agent_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_multi_agent_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_multi_agent_plans_tenant_updated ON v4_multi_agent_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_multi_agent_observations_tenant_observed ON v4_multi_agent_observations(tenant_id,
observed_at);
ALTER TABLE v4_multi_agent_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_multi_agent_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_multi_agent_plans_tenant ON v4_multi_agent_plans;
CREATE POLICY v4_multi_agent_plans_tenant ON v4_multi_agent_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_multi_agent_observations_tenant ON v4_multi_agent_observations;
CREATE POLICY v4_multi_agent_observations_tenant ON v4_multi_agent_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V123__v4_28_collective_multi_agent_intelligence.sql

-- ============================================================================
-- BEGIN V124__v4_29_autonomous_incident_commander.sql
-- ============================================================================
-- Autonomous Incident Commander (4_29.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_incident_command_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_incident_command_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_incident_command_plans_tenant_updated ON v4_incident_command_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_incident_command_observations_tenant_observed ON
v4_incident_command_observations(tenant_id,
observed_at);
ALTER TABLE v4_incident_command_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_incident_command_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_incident_command_plans_tenant ON v4_incident_command_plans;
CREATE POLICY v4_incident_command_plans_tenant ON v4_incident_command_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_incident_command_observations_tenant ON v4_incident_command_observations;
CREATE POLICY v4_incident_command_observations_tenant ON v4_incident_command_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V124__v4_29_autonomous_incident_commander.sql

-- ============================================================================
-- BEGIN V125__v4_30_cognitive_cyber_defense_platform.sql
-- ============================================================================
-- Cognitive Cyber Defense Platform (4_30.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_cognitive_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_cognitive_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cognitive_plans_tenant_updated ON v4_cognitive_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_cognitive_observations_tenant_observed ON v4_cognitive_observations(tenant_id,
observed_at);
ALTER TABLE v4_cognitive_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_cognitive_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_cognitive_plans_tenant ON v4_cognitive_plans;
CREATE POLICY v4_cognitive_plans_tenant ON v4_cognitive_plans USING (tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_cognitive_observations_tenant ON v4_cognitive_observations;
CREATE POLICY v4_cognitive_observations_tenant ON v4_cognitive_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V125__v4_30_cognitive_cyber_defense_platform.sql

-- ============================================================================
-- BEGIN V126__v4_31_cross_organization_intelligence.sql
-- ============================================================================
-- Cross-Organization Intelligence (4_31.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_cross_org_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_cross_org_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cross_org_plans_tenant_updated ON v4_cross_org_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_cross_org_observations_tenant_observed ON v4_cross_org_observations(tenant_id,
observed_at);
ALTER TABLE v4_cross_org_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_cross_org_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_cross_org_plans_tenant ON v4_cross_org_plans;
CREATE POLICY v4_cross_org_plans_tenant ON v4_cross_org_plans USING (tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_cross_org_observations_tenant ON v4_cross_org_observations;
CREATE POLICY v4_cross_org_observations_tenant ON v4_cross_org_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V126__v4_31_cross_organization_intelligence.sql

-- ============================================================================
-- BEGIN V127__v4_32_federated_threat_graph.sql
-- ============================================================================
-- Federated Threat Graph (4_32.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_federated_graph_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_federated_graph_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_federated_graph_plans_tenant_updated ON v4_federated_graph_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_federated_graph_observations_tenant_observed ON
v4_federated_graph_observations(tenant_id,
observed_at);
ALTER TABLE v4_federated_graph_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_federated_graph_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_federated_graph_plans_tenant ON v4_federated_graph_plans;
CREATE POLICY v4_federated_graph_plans_tenant ON v4_federated_graph_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_federated_graph_observations_tenant ON v4_federated_graph_observations;
CREATE POLICY v4_federated_graph_observations_tenant ON v4_federated_graph_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V127__v4_32_federated_threat_graph.sql

-- ============================================================================
-- BEGIN V128__v4_33_privacy_preserving_intelligence.sql
-- ============================================================================
-- Privacy-Preserving Intelligence (4_33.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_privacy_intel_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_privacy_intel_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_privacy_intel_plans_tenant_updated ON v4_privacy_intel_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_privacy_intel_observations_tenant_observed ON v4_privacy_intel_observations(tenant_id,
observed_at);
ALTER TABLE v4_privacy_intel_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_privacy_intel_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_privacy_intel_plans_tenant ON v4_privacy_intel_plans;
CREATE POLICY v4_privacy_intel_plans_tenant ON v4_privacy_intel_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_privacy_intel_observations_tenant ON v4_privacy_intel_observations;
CREATE POLICY v4_privacy_intel_observations_tenant ON v4_privacy_intel_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V128__v4_33_privacy_preserving_intelligence.sql

-- ============================================================================
-- BEGIN V129__v4_34_sovereign_intelligence.sql
-- ============================================================================
-- Sovereign Intelligence (4_34.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_sovereignty_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_sovereignty_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sovereignty_plans_tenant_updated ON v4_sovereignty_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_sovereignty_observations_tenant_observed ON v4_sovereignty_observations(tenant_id,
observed_at);
ALTER TABLE v4_sovereignty_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_sovereignty_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_sovereignty_plans_tenant ON v4_sovereignty_plans;
CREATE POLICY v4_sovereignty_plans_tenant ON v4_sovereignty_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_sovereignty_observations_tenant ON v4_sovereignty_observations;
CREATE POLICY v4_sovereignty_observations_tenant ON v4_sovereignty_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V129__v4_34_sovereign_intelligence.sql

-- ============================================================================
-- BEGIN V130__v4_35_cross_cloud_defense.sql
-- ============================================================================
-- Cross-Cloud Defense (4_35.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_cross_cloud_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_cross_cloud_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cross_cloud_plans_tenant_updated ON v4_cross_cloud_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_cross_cloud_observations_tenant_observed ON v4_cross_cloud_observations(tenant_id,
observed_at);
ALTER TABLE v4_cross_cloud_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_cross_cloud_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_cross_cloud_plans_tenant ON v4_cross_cloud_plans;
CREATE POLICY v4_cross_cloud_plans_tenant ON v4_cross_cloud_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_cross_cloud_observations_tenant ON v4_cross_cloud_observations;
CREATE POLICY v4_cross_cloud_observations_tenant ON v4_cross_cloud_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V130__v4_35_cross_cloud_defense.sql

-- ============================================================================
-- BEGIN V131__v4_36_cross_region_defense.sql
-- ============================================================================
-- Cross-Region Defense (4_36.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_cross_region_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_cross_region_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cross_region_plans_tenant_updated ON v4_cross_region_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_cross_region_observations_tenant_observed ON v4_cross_region_observations(tenant_id,
observed_at);
ALTER TABLE v4_cross_region_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_cross_region_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_cross_region_plans_tenant ON v4_cross_region_plans;
CREATE POLICY v4_cross_region_plans_tenant ON v4_cross_region_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_cross_region_observations_tenant ON v4_cross_region_observations;
CREATE POLICY v4_cross_region_observations_tenant ON v4_cross_region_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V131__v4_36_cross_region_defense.sql

-- ============================================================================
-- BEGIN V132__v4_37_cyber_defense_federation_protocol.sql
-- ============================================================================
-- Cyber Defense Federation Protocol (4_37.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_federation_protocol_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_federation_protocol_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_federation_protocol_plans_tenant_updated ON v4_federation_protocol_plans(tenant_id,
updated_at);
CREATE INDEX IF NOT EXISTS idx_federation_protocol_observations_tenant_observed ON
v4_federation_protocol_observations(tenant_id,
observed_at);
ALTER TABLE v4_federation_protocol_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_federation_protocol_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_federation_protocol_plans_tenant ON v4_federation_protocol_plans;
CREATE POLICY v4_federation_protocol_plans_tenant ON v4_federation_protocol_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_federation_protocol_observations_tenant ON v4_federation_protocol_observations;
CREATE POLICY v4_federation_protocol_observations_tenant ON v4_federation_protocol_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V132__v4_37_cyber_defense_federation_protocol.sql

-- ============================================================================
-- BEGIN V133__v4_38_global_threat_coordination.sql
-- ============================================================================
-- Global Threat Coordination (4_38.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_global_coordination_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_global_coordination_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_global_coordination_plans_tenant_updated ON v4_global_coordination_plans(tenant_id,
updated_at);
CREATE INDEX IF NOT EXISTS idx_global_coordination_observations_tenant_observed ON
v4_global_coordination_observations(tenant_id,
observed_at);
ALTER TABLE v4_global_coordination_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_global_coordination_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_global_coordination_plans_tenant ON v4_global_coordination_plans;
CREATE POLICY v4_global_coordination_plans_tenant ON v4_global_coordination_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_global_coordination_observations_tenant ON v4_global_coordination_observations;
CREATE POLICY v4_global_coordination_observations_tenant ON v4_global_coordination_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V133__v4_38_global_threat_coordination.sql

-- ============================================================================
-- BEGIN V134__v4_39_federated_autonomous_response.sql
-- ============================================================================
-- Federated Autonomous Response (4_39.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_federated_response_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_federated_response_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_federated_response_plans_tenant_updated ON v4_federated_response_plans(tenant_id,
updated_at);
CREATE INDEX IF NOT EXISTS idx_federated_response_observations_tenant_observed ON
v4_federated_response_observations(tenant_id,
observed_at);
ALTER TABLE v4_federated_response_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_federated_response_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_federated_response_plans_tenant ON v4_federated_response_plans;
CREATE POLICY v4_federated_response_plans_tenant ON v4_federated_response_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_federated_response_observations_tenant ON v4_federated_response_observations;
CREATE POLICY v4_federated_response_observations_tenant ON v4_federated_response_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V134__v4_39_federated_autonomous_response.sql

-- ============================================================================
-- BEGIN V135__v4_40_global_cyber_defense_intelligence_fabric.sql
-- ============================================================================
-- Global Cyber Defense Intelligence Fabric (4_40.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_global_fabric_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_global_fabric_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_global_fabric_plans_tenant_updated ON v4_global_fabric_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_global_fabric_observations_tenant_observed ON v4_global_fabric_observations(tenant_id,
observed_at);
ALTER TABLE v4_global_fabric_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_global_fabric_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_global_fabric_plans_tenant ON v4_global_fabric_plans;
CREATE POLICY v4_global_fabric_plans_tenant ON v4_global_fabric_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_global_fabric_observations_tenant ON v4_global_fabric_observations;
CREATE POLICY v4_global_fabric_observations_tenant ON v4_global_fabric_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V135__v4_40_global_cyber_defense_intelligence_fabric.sql

-- ============================================================================
-- BEGIN V136__v4_41_defense_digital_twin.sql
-- ============================================================================
-- Defense Digital Twin (4_41.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_digital_twin_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_digital_twin_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_digital_twin_plans_tenant_updated ON v4_digital_twin_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_digital_twin_observations_tenant_observed ON v4_digital_twin_observations(tenant_id,
observed_at);
ALTER TABLE v4_digital_twin_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_digital_twin_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_digital_twin_plans_tenant ON v4_digital_twin_plans;
CREATE POLICY v4_digital_twin_plans_tenant ON v4_digital_twin_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_digital_twin_observations_tenant ON v4_digital_twin_observations;
CREATE POLICY v4_digital_twin_observations_tenant ON v4_digital_twin_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V136__v4_41_defense_digital_twin.sql

-- ============================================================================
-- BEGIN V137__v4_42_attack_simulation_fabric.sql
-- ============================================================================
-- Attack Simulation Fabric (4_42.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_attack_sim_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_attack_sim_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_attack_sim_plans_tenant_updated ON v4_attack_sim_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_attack_sim_observations_tenant_observed ON v4_attack_sim_observations(tenant_id,
observed_at);
ALTER TABLE v4_attack_sim_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_attack_sim_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_attack_sim_plans_tenant ON v4_attack_sim_plans;
CREATE POLICY v4_attack_sim_plans_tenant ON v4_attack_sim_plans USING (tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_attack_sim_observations_tenant ON v4_attack_sim_observations;
CREATE POLICY v4_attack_sim_observations_tenant ON v4_attack_sim_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V137__v4_42_attack_simulation_fabric.sql

-- ============================================================================
-- BEGIN V138__v4_43_counterfactual_defense.sql
-- ============================================================================
-- Counterfactual Defense (4_43.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_counterfactual_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_counterfactual_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_counterfactual_plans_tenant_updated ON v4_counterfactual_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_counterfactual_observations_tenant_observed ON v4_counterfactual_observations(tenant_id,
observed_at);
ALTER TABLE v4_counterfactual_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_counterfactual_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_counterfactual_plans_tenant ON v4_counterfactual_plans;
CREATE POLICY v4_counterfactual_plans_tenant ON v4_counterfactual_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_counterfactual_observations_tenant ON v4_counterfactual_observations;
CREATE POLICY v4_counterfactual_observations_tenant ON v4_counterfactual_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V138__v4_43_counterfactual_defense.sql

-- ============================================================================
-- BEGIN V139__v4_44_autonomous_red_blue_intelligence.sql
-- ============================================================================
-- Autonomous Red/Blue Intelligence (4_44.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_red_blue_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_red_blue_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_red_blue_plans_tenant_updated ON v4_red_blue_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_red_blue_observations_tenant_observed ON v4_red_blue_observations(tenant_id,observed_at);
ALTER TABLE v4_red_blue_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_red_blue_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_red_blue_plans_tenant ON v4_red_blue_plans;
CREATE POLICY v4_red_blue_plans_tenant ON v4_red_blue_plans USING (tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_red_blue_observations_tenant ON v4_red_blue_observations;
CREATE POLICY v4_red_blue_observations_tenant ON v4_red_blue_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V139__v4_44_autonomous_red_blue_intelligence.sql

-- ============================================================================
-- BEGIN V140__v4_45_continuous_security_optimization.sql
-- ============================================================================
-- Continuous Security Optimization (4_45.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_optimization_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_optimization_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_optimization_plans_tenant_updated ON v4_optimization_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_optimization_observations_tenant_observed ON v4_optimization_observations(tenant_id,
observed_at);
ALTER TABLE v4_optimization_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_optimization_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_optimization_plans_tenant ON v4_optimization_plans;
CREATE POLICY v4_optimization_plans_tenant ON v4_optimization_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_optimization_observations_tenant ON v4_optimization_observations;
CREATE POLICY v4_optimization_observations_tenant ON v4_optimization_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V140__v4_45_continuous_security_optimization.sql

-- ============================================================================
-- BEGIN V141__v4_46_self_healing_defense_infrastructure.sql
-- ============================================================================
-- Self-Healing Defense Infrastructure (4_46.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_self_healing_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_self_healing_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_self_healing_plans_tenant_updated ON v4_self_healing_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_self_healing_observations_tenant_observed ON v4_self_healing_observations(tenant_id,
observed_at);
ALTER TABLE v4_self_healing_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_self_healing_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_self_healing_plans_tenant ON v4_self_healing_plans;
CREATE POLICY v4_self_healing_plans_tenant ON v4_self_healing_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_self_healing_observations_tenant ON v4_self_healing_observations;
CREATE POLICY v4_self_healing_observations_tenant ON v4_self_healing_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V141__v4_46_self_healing_defense_infrastructure.sql

-- ============================================================================
-- BEGIN V142__v4_47_self_validating_intelligence.sql
-- ============================================================================
-- Self-Validating Intelligence (4_47.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_validation_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_validation_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_validation_plans_tenant_updated ON v4_validation_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_validation_observations_tenant_observed ON v4_validation_observations(tenant_id,
observed_at);
ALTER TABLE v4_validation_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_validation_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_validation_plans_tenant ON v4_validation_plans;
CREATE POLICY v4_validation_plans_tenant ON v4_validation_plans USING (tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_validation_observations_tenant ON v4_validation_observations;
CREATE POLICY v4_validation_observations_tenant ON v4_validation_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V142__v4_47_self_validating_intelligence.sql

-- ============================================================================
-- BEGIN V143__v4_48_self_governing_agent_ecosystem.sql
-- ============================================================================
-- Self-Governing Agent Ecosystem (4_48.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_agent_ecosystem_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_agent_ecosystem_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agent_ecosystem_plans_tenant_updated ON v4_agent_ecosystem_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_agent_ecosystem_observations_tenant_observed ON
v4_agent_ecosystem_observations(tenant_id,
observed_at);
ALTER TABLE v4_agent_ecosystem_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_agent_ecosystem_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_agent_ecosystem_plans_tenant ON v4_agent_ecosystem_plans;
CREATE POLICY v4_agent_ecosystem_plans_tenant ON v4_agent_ecosystem_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_agent_ecosystem_observations_tenant ON v4_agent_ecosystem_observations;
CREATE POLICY v4_agent_ecosystem_observations_tenant ON v4_agent_ecosystem_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V143__v4_48_self_governing_agent_ecosystem.sql

-- ============================================================================
-- BEGIN V144__v4_49_continuous_autonomous_defense_loop.sql
-- ============================================================================
-- Continuous Autonomous Defense Loop (4_49.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_defense_loop_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_defense_loop_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_defense_loop_plans_tenant_updated ON v4_defense_loop_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_defense_loop_observations_tenant_observed ON v4_defense_loop_observations(tenant_id,
observed_at);
ALTER TABLE v4_defense_loop_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_defense_loop_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_defense_loop_plans_tenant ON v4_defense_loop_plans;
CREATE POLICY v4_defense_loop_plans_tenant ON v4_defense_loop_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_defense_loop_observations_tenant ON v4_defense_loop_observations;
CREATE POLICY v4_defense_loop_observations_tenant ON v4_defense_loop_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V144__v4_49_continuous_autonomous_defense_loop.sql

-- ============================================================================
-- BEGIN V145__v4_50_self_evolving_cyber_defense_intelligence_infrastructure.sql
-- ============================================================================
-- Self-Evolving Cyber Defense Intelligence Infrastructure (4_50.0)
-- ADDITIVE extension of 4.20;
-- no prior migration is altered or removed.
-- Governed autonomous operation remains mandatory: identity -> attestation -> capability -> policy ->
-- evidence -> approval -> execution contract -> execution -> verification -> audit.

CREATE TABLE IF NOT EXISTS v4_self_evolving_plans (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512) NOT NULL,
 scope_ref VARCHAR(512), policy_ref VARCHAR(512), owner_ref VARCHAR(512),
 state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED', risk_score NUMERIC(10,4),
 confidence_score NUMERIC(10,4), metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,plan_ref)
);
CREATE TABLE IF NOT EXISTS v4_self_evolving_observations (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, plan_ref VARCHAR(512), subject_ref VARCHAR(512),
 observation_type VARCHAR(128) NOT NULL, evidence_ref VARCHAR(512), provenance_ref VARCHAR(512),
 payload JSONB NOT NULL DEFAULT '{}'::jsonb, confidence_score NUMERIC(10,
 4), observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_self_evolving_plans_tenant_updated ON v4_self_evolving_plans(tenant_id,updated_at);
CREATE INDEX IF NOT EXISTS idx_self_evolving_observations_tenant_observed ON v4_self_evolving_observations(tenant_id,
observed_at);
ALTER TABLE v4_self_evolving_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE v4_self_evolving_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS v4_self_evolving_plans_tenant ON v4_self_evolving_plans;
CREATE POLICY v4_self_evolving_plans_tenant ON v4_self_evolving_plans USING
(tenant_id::text=current_setting('app.tenant_id',
true));
DROP POLICY IF EXISTS v4_self_evolving_observations_tenant ON v4_self_evolving_observations;
CREATE POLICY v4_self_evolving_observations_tenant ON v4_self_evolving_observations USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V145__v4_50_self_evolving_cyber_defense_intelligence_infrastructure.sql

-- ============================================================================
-- BEGIN V146__v4_51_enterprise_rbac_experience.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS ui_role_permission (id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
tenant_id UUID NOT NULL, role_code VARCHAR(64) NOT NULL, permission_code VARCHAR(128) NOT NULL,
UNIQUE(tenant_id,role_code,permission_code));
CREATE TABLE IF NOT EXISTS ui_menu_permission (id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
tenant_id UUID NOT NULL, menu_code VARCHAR(128) NOT NULL, permission_code VARCHAR(128) NOT NULL,
UNIQUE(tenant_id,menu_code,permission_code));
ALTER TABLE ui_role_permission ENABLE ROW LEVEL SECURITY;
ALTER TABLE ui_menu_permission ENABLE ROW LEVEL SECURITY;
CREATE POLICY ui_role_permission_tenant ON ui_role_permission USING (tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY ui_menu_permission_tenant ON ui_menu_permission USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V146__v4_51_enterprise_rbac_experience.sql

-- ============================================================================
-- BEGIN V147__v4_52_kubernetes_zerotrust_operator.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS k8s_zerotrust_policy (id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
tenant_id UUID NOT NULL, cluster_name VARCHAR(200) NOT NULL, namespace VARCHAR(200),
resource_name VARCHAR(200) NOT NULL, policy_hash VARCHAR(128) NOT NULL,
desired_state VARCHAR(32) NOT NULL, observed_state VARCHAR(32), last_reconcile_at TIMESTAMPTZ,
UNIQUE(tenant_id,cluster_name,namespace,resource_name));
ALTER TABLE k8s_zerotrust_policy ENABLE ROW LEVEL SECURITY;
CREATE POLICY k8s_zerotrust_policy_tenant ON
k8s_zerotrust_policy USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V147__v4_52_kubernetes_zerotrust_operator.sql

-- ============================================================================
-- BEGIN V148__v4_53_kubernetes_policy_federation.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS k8s_policy_federation (id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
tenant_id UUID NOT NULL, source_cluster VARCHAR(200) NOT NULL, target_cluster VARCHAR(200) NOT NULL,
policy_hash VARCHAR(128) NOT NULL, trust_level VARCHAR(32) NOT NULL, propagation_mode VARCHAR(32) NOT NULL,
status VARCHAR(32) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
ALTER TABLE k8s_policy_federation ENABLE ROW LEVEL SECURITY;
CREATE POLICY k8s_policy_federation_tenant
ON k8s_policy_federation USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V148__v4_53_kubernetes_policy_federation.sql

-- ============================================================================
-- BEGIN V149__v4_54_operator_attestation_evidence.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS k8s_operator_attestation (id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
tenant_id UUID NOT NULL, operator_id VARCHAR(200) NOT NULL, nonce VARCHAR(256) NOT NULL,
claims JSONB NOT NULL, attestation_hash VARCHAR(128) NOT NULL, verified BOOLEAN NOT NULL DEFAULT false,
verified_at TIMESTAMPTZ);
CREATE TABLE IF NOT EXISTS k8s_operator_evidence (id UUID PRIMARY KEY DEFAULT
gen_random_uuid(),
tenant_id UUID NOT NULL, policy_id UUID, attestation_id UUID, action VARCHAR(128) NOT NULL,
payload JSONB NOT NULL, content_hash VARCHAR(128) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
ALTER TABLE k8s_operator_attestation ENABLE ROW LEVEL SECURITY;
ALTER TABLE k8s_operator_evidence ENABLE
ROW LEVEL SECURITY;
CREATE POLICY k8s_operator_attestation_tenant ON k8s_operator_attestation USING
(tenant_id::text=current_setting('app.tenant_id',
true));
CREATE POLICY k8s_operator_evidence_tenant ON k8s_operator_evidence USING
(tenant_id::text=current_setting('app.tenant_id',
true));

-- END V149__v4_54_operator_attestation_evidence.sql

-- ============================================================================
-- BEGIN V150__v4_55_kubernetes_autonomous_defense_integration.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS k8s_autonomous_action (id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
tenant_id UUID NOT NULL, policy_id UUID, action_type VARCHAR(128) NOT NULL,
target_ref VARCHAR(512) NOT NULL, execution_contract_id UUID, approval_ref VARCHAR(200),
verification_status VARCHAR(32), evidence_id UUID, audit_ref VARCHAR(200),
status VARCHAR(32) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
completed_at TIMESTAMPTZ);
ALTER TABLE k8s_autonomous_action ENABLE ROW LEVEL SECURITY;
CREATE POLICY k8s_autonomous_action_tenant
ON k8s_autonomous_action USING (tenant_id::text=current_setting('app.tenant_id',
true));

-- END V150__v4_55_kubernetes_autonomous_defense_integration.sql

