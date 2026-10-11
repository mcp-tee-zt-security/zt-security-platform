-- New events retain delegation context. Historical audit events are never updated.
ALTER TABLE stitch_integration_audit ADD COLUMN human_subject text;
ALTER TABLE stitch_integration_audit ADD COLUMN ai_subject text;
ALTER TABLE stitch_integration_audit ADD COLUMN session_id uuid;
CREATE INDEX retrieval_audit_workspace_time ON stitch_integration_audit(tenant_id,workspace_id,created_at DESC,id DESC);
