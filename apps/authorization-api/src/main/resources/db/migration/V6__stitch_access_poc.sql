-- Isolated synthetic PoC data. No customer content is seeded automatically.
CREATE TABLE stitch_poc_areas (
 tenant_id uuid NOT NULL, workspace_id uuid NOT NULL, area_id varchar(80) NOT NULL,
 parent_id varchar(80), name varchar(120) NOT NULL, kind varchar(16) NOT NULL,
 agent_access varchar(8) NOT NULL CHECK(agent_access IN ('ALLOW','DENY')),
 human_access boolean NOT NULL DEFAULT true,
 PRIMARY KEY(tenant_id,workspace_id,area_id),
 FOREIGN KEY(tenant_id,workspace_id,parent_id) REFERENCES stitch_poc_areas(tenant_id,workspace_id,area_id)
);
CREATE TABLE stitch_poc_documents (
 tenant_id uuid NOT NULL, workspace_id uuid NOT NULL, document_id varchar(80) NOT NULL,
 area_id varchar(80) NOT NULL, title varchar(200) NOT NULL, content text NOT NULL,
 PRIMARY KEY(tenant_id,workspace_id,document_id),
 FOREIGN KEY(tenant_id,workspace_id,area_id) REFERENCES stitch_poc_areas(tenant_id,workspace_id,area_id)
);
CREATE TABLE stitch_poc_calls (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, workspace_id uuid NOT NULL,
 subject varchar(512) NOT NULL, actor_type varchar(32) NOT NULL, operation varchar(64) NOT NULL,
 resource_id varchar(80), decision varchar(16) NOT NULL, reason varchar(1000) NOT NULL,
 returned_count integer NOT NULL, content_reads integer NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX stitch_poc_calls_scope ON stitch_poc_calls(tenant_id,workspace_id,created_at DESC);
ALTER TABLE stitch_poc_areas ENABLE ROW LEVEL SECURITY;
ALTER TABLE stitch_poc_areas FORCE ROW LEVEL SECURITY;
ALTER TABLE stitch_poc_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE stitch_poc_documents FORCE ROW LEVEL SECURITY;
ALTER TABLE stitch_poc_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE stitch_poc_calls FORCE ROW LEVEL SECURITY;
CREATE POLICY stitch_poc_areas_scope ON stitch_poc_areas USING (tenant_id=nullif(current_setting('app.tenant_id',true),'')::uuid AND workspace_id=nullif(current_setting('app.workspace_id',true),'')::uuid) WITH CHECK (tenant_id=nullif(current_setting('app.tenant_id',true),'')::uuid AND workspace_id=nullif(current_setting('app.workspace_id',true),'')::uuid);
CREATE POLICY stitch_poc_documents_scope ON stitch_poc_documents USING (tenant_id=nullif(current_setting('app.tenant_id',true),'')::uuid AND workspace_id=nullif(current_setting('app.workspace_id',true),'')::uuid) WITH CHECK (tenant_id=nullif(current_setting('app.tenant_id',true),'')::uuid AND workspace_id=nullif(current_setting('app.workspace_id',true),'')::uuid);
CREATE POLICY stitch_poc_calls_scope ON stitch_poc_calls USING (tenant_id=nullif(current_setting('app.tenant_id',true),'')::uuid AND workspace_id=nullif(current_setting('app.workspace_id',true),'')::uuid) WITH CHECK (tenant_id=nullif(current_setting('app.tenant_id',true),'')::uuid AND workspace_id=nullif(current_setting('app.workspace_id',true),'')::uuid);
