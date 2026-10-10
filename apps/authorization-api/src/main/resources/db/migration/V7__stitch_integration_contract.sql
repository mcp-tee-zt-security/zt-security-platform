CREATE TABLE stitch_integration_scopes (
 tenant_id uuid NOT NULL, workspace_id uuid NOT NULL, acl_version bigint NOT NULL DEFAULT 1,
 source_ready boolean NOT NULL DEFAULT false, source_refreshed_at timestamptz,
 receipt_ttl_days integer NOT NULL DEFAULT 7 CHECK(receipt_ttl_days BETWEEN 1 AND 365),
 PRIMARY KEY(tenant_id,workspace_id)
);
CREATE TABLE stitch_resources (
 tenant_id uuid NOT NULL, workspace_id uuid NOT NULL, resource_id varchar(128) NOT NULL,
 parent_id varchar(128), kind varchar(16) NOT NULL CHECK(kind IN ('FOLDER','CHANNEL','FILE','ATTACHMENT','MESSAGE')),
 title varchar(256) NOT NULL, owner_subject varchar(512) NOT NULL,
 ai_access varchar(8) NOT NULL CHECK(ai_access IN ('ALLOW','DENY')),
 deleted boolean NOT NULL DEFAULT false, source_version bigint NOT NULL,
 purge_after timestamptz, content text NOT NULL DEFAULT '',
 PRIMARY KEY(tenant_id,workspace_id,resource_id),
 FOREIGN KEY(tenant_id,workspace_id,parent_id) REFERENCES stitch_resources(tenant_id,workspace_id,resource_id),
 CHECK(parent_id IS NULL OR parent_id<>resource_id)
);
CREATE TABLE stitch_resource_acl (
 tenant_id uuid NOT NULL, workspace_id uuid NOT NULL, resource_id varchar(128) NOT NULL,
 principal_kind varchar(16) NOT NULL CHECK(principal_kind IN ('SUBJECT','GROUP','AI_SUBJECT')),
 principal_id varchar(512) NOT NULL, permission varchar(16) NOT NULL CHECK(permission IN ('READ','SEARCH')),
 effect varchar(8) NOT NULL CHECK(effect IN ('ALLOW','DENY')),
 PRIMARY KEY(tenant_id,workspace_id,resource_id,principal_kind,principal_id,permission),
 FOREIGN KEY(tenant_id,workspace_id,resource_id) REFERENCES stitch_resources(tenant_id,workspace_id,resource_id)
);
CREATE TABLE stitch_rag_chunks (
 tenant_id uuid NOT NULL, workspace_id uuid NOT NULL, resource_id varchar(128) NOT NULL,
 chunk_id integer NOT NULL, content text NOT NULL, embedding jsonb,
 search_vector tsvector GENERATED ALWAYS AS(to_tsvector('simple',content)) STORED,
 PRIMARY KEY(tenant_id,workspace_id,resource_id,chunk_id),
 FOREIGN KEY(tenant_id,workspace_id,resource_id) REFERENCES stitch_resources(tenant_id,workspace_id,resource_id)
);
CREATE INDEX stitch_rag_text_index ON stitch_rag_chunks USING gin(search_vector);
CREATE TABLE stitch_file_blobs (
 tenant_id uuid NOT NULL, workspace_id uuid NOT NULL, resource_id varchar(128) NOT NULL,
 data bytea NOT NULL,
 PRIMARY KEY(tenant_id,workspace_id,resource_id),
 FOREIGN KEY(tenant_id,workspace_id,resource_id) REFERENCES stitch_resources(tenant_id,workspace_id,resource_id)
);
CREATE TABLE stitch_ai_sessions (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, workspace_id uuid NOT NULL,
 human_subject varchar(512) NOT NULL, human_jti varchar(512) NOT NULL, groups_json jsonb NOT NULL,
 ai_subject varchar(512) NOT NULL, expires_at timestamptz NOT NULL, revoked boolean NOT NULL DEFAULT false
);
CREATE TABLE stitch_subjects (
 tenant_id uuid NOT NULL, workspace_id uuid NOT NULL, subject varchar(512) NOT NULL,
 groups_json jsonb NOT NULL, active boolean NOT NULL, source_version bigint NOT NULL,
 PRIMARY KEY(tenant_id,workspace_id,subject)
);
CREATE TABLE stitch_revoked_tokens (
 tenant_id uuid NOT NULL, workspace_id uuid NOT NULL, jti varchar(512) NOT NULL,
 expires_at timestamptz NOT NULL,
 PRIMARY KEY(tenant_id,workspace_id,jti)
);
CREATE TABLE stitch_retrieval_cache (
 tenant_id uuid NOT NULL, workspace_id uuid NOT NULL, cache_key varchar(64) NOT NULL,
 acl_version bigint NOT NULL, result_ids jsonb NOT NULL, expires_at timestamptz NOT NULL,
 PRIMARY KEY(tenant_id,workspace_id,cache_key)
);
CREATE TABLE stitch_disclosures (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, workspace_id uuid NOT NULL,
 resource_id varchar(128) NOT NULL, ai_subject varchar(512) NOT NULL, session_id uuid NOT NULL,
 acl_version bigint NOT NULL, expires_at timestamptz NOT NULL, revoked boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE stitch_deletion_events (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, workspace_id uuid NOT NULL,
 resource_id varchar(128) NOT NULL, target_subject varchar(512) NOT NULL,
 reason varchar(64) NOT NULL, status varchar(32) NOT NULL DEFAULT 'PENDING',
 created_at timestamptz NOT NULL DEFAULT now(), acknowledged_at timestamptz, receipt_id varchar(256)
);
CREATE TABLE stitch_integration_audit (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, workspace_id uuid NOT NULL,
 subject varchar(512) NOT NULL, operation varchar(64) NOT NULL, resource_id varchar(128),
 decision varchar(16) NOT NULL, acl_version bigint NOT NULL, returned_count integer NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
-- No source content, query text or secret is stored in disclosure/audit/cache records.
DO $$ DECLARE table_name text; BEGIN
 FOREACH table_name IN ARRAY ARRAY['stitch_integration_scopes','stitch_resources','stitch_resource_acl','stitch_rag_chunks','stitch_file_blobs','stitch_ai_sessions','stitch_subjects','stitch_revoked_tokens','stitch_retrieval_cache','stitch_disclosures','stitch_deletion_events','stitch_integration_audit'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',table_name);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',table_name);
  EXECUTE format('CREATE POLICY integration_scope ON %I USING (tenant_id=nullif(current_setting(''app.tenant_id'',true),'''')::uuid AND workspace_id=nullif(current_setting(''app.workspace_id'',true),'''')::uuid) WITH CHECK (tenant_id=nullif(current_setting(''app.tenant_id'',true),'''')::uuid AND workspace_id=nullif(current_setting(''app.workspace_id'',true),'''')::uuid)',table_name);
 END LOOP;
END $$;
