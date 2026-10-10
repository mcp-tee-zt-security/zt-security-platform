-- Run as the database owner AFTER Flyway using a distinct migration credential.
-- Provision zt_stitch_runtime LOGIN credentials through the deployment secret manager.
-- This script creates a role with no password and grants only existing application objects.
DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='zt_stitch_runtime') THEN
 CREATE ROLE zt_stitch_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
END IF; END $$;
GRANT USAGE ON SCHEMA zt_security_475 TO zt_stitch_runtime;
GRANT SELECT ON zt_security_475.workspaces,zt_security_475.api_clients TO zt_stitch_runtime;
GRANT SELECT ON zt_security_475.policies,zt_security_475.retrieval_connections TO zt_stitch_runtime;
GRANT UPDATE(last_used_at) ON zt_security_475.api_clients TO zt_stitch_runtime;
DO $$ DECLARE table_name text; BEGIN
 FOREACH table_name IN ARRAY ARRAY['stitch_integration_scopes','stitch_resources','stitch_resource_acl','stitch_rag_chunks','stitch_file_blobs','stitch_ai_sessions','stitch_subjects','stitch_revoked_tokens','stitch_retrieval_cache','stitch_disclosures','stitch_deletion_events','stitch_integration_audit'] LOOP
  EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON zt_security_475.%I TO zt_stitch_runtime',table_name);
 END LOOP;
END $$;
-- The monolith's other endpoints need separate grants or must stay disabled/unrouted.
-- Network isolation is still essential: RLS settings can be set by a DB credential holder.
