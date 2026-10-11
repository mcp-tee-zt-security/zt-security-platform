CREATE TABLE security_activities (
 id uuid PRIMARY KEY, schema_version integer NOT NULL DEFAULT 1 CHECK(schema_version=1),
 tenant_id uuid NOT NULL, workspace_id uuid, source varchar(32) NOT NULL,
 event_type varchar(120) NOT NULL, stage varchar(32) NOT NULL, origin_id varchar(256) NOT NULL,
 event_key varchar(512) NOT NULL, occurred_at timestamptz NOT NULL,
 correlation_id varchar(512), request_id uuid, caller_subject varchar(512), human_subject varchar(512), ai_subject varchar(512),
 action varchar(255) NOT NULL, resource_type varchar(128), resource_id varchar(512),
 decision varchar(16) CHECK(decision IS NULL OR decision IN ('ALLOW','DENY','STEP_UP')),
 status varchar(64), risk_score double precision CHECK(risk_score IS NULL OR risk_score BETWEEN 0 AND 100),
 analysis_eligible boolean NOT NULL DEFAULT false, metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
 FOREIGN KEY(tenant_id,workspace_id) REFERENCES workspaces(tenant_id,id)
);
CREATE UNIQUE INDEX security_activity_identity ON security_activities(tenant_id,coalesce(workspace_id,'00000000-0000-0000-0000-000000000000'::uuid),event_key);
CREATE INDEX security_activity_scope_time ON security_activities(tenant_id,workspace_id,occurred_at DESC,id DESC);
CREATE INDEX security_activity_agent_time ON security_activities(tenant_id,workspace_id,ai_subject,occurred_at DESC) WHERE analysis_eligible;
-- Set each original scope explicitly so historical import also respects forced RLS.
DO $import$
DECLARE tenant_row uuid; workspace_row uuid;
BEGIN
 FOR tenant_row IN SELECT id FROM tenants LOOP
  PERFORM set_config('app.tenant_id',tenant_row::text,true);
  FOR workspace_row IN SELECT id FROM (SELECT id FROM workspaces WHERE tenant_id=tenant_row UNION ALL SELECT NULL::uuid) spaces ORDER BY id NULLS LAST LOOP
   PERFORM set_config('app.workspace_id',coalesce(workspace_row::text,''),true);
-- Project actual historic evidence; never update or pretend to have emitted old outbox messages.
INSERT INTO security_activities(id,tenant_id,workspace_id,source,event_type,stage,origin_id,event_key,occurred_at,correlation_id,request_id,caller_subject,human_subject,ai_subject,action,resource_type,resource_id,decision,status,risk_score,analysis_eligible,metadata)
SELECT md5('audit:'||a.id)::uuid,a.tenant_id,coalesce(a.workspace_id,i.workspace_id,g.workspace_id),
 CASE WHEN a.action='mcp.tool.call' THEN 'MCP' WHEN a.action IN ('chat.respond','simulation.transfer') THEN 'WORKFLOW' ELSE 'DECISION' END,
 'AUTHORIZATION_DECIDED','AUTHORIZATION',a.id::text,'audit:'||a.id,a.created_at,a.request_id::text,a.request_id,
 coalesce(i.actor_subject,a.metadata->'context'->>'requestedBy',a.metadata->>'principal'),a.metadata->'context'->>'requestedBy',CASE WHEN a.identity_type='AI_AGENT' THEN a.metadata->>'principal' END,
 a.action,a.resource_type,a.resource_id,a.decision,'EVALUATED',a.risk_score,
 coalesce(a.identity_type='AI_AGENT',false) AND a.metadata->>'principal' IS NOT NULL AND coalesce(a.metadata->'context'->>'operationType','')<>'RETRIEVAL',
 jsonb_build_object('auditEventId',a.id,'reason',a.reason,'matchedPolicies',coalesce(a.metadata->'matchedPolicies','[]'::jsonb),'imported',true)
FROM audit_logs a LEFT JOIN LATERAL(SELECT * FROM mcp_invocations i WHERE i.tenant_id=a.tenant_id AND i.decision_request_id=a.request_id LIMIT 1)i ON true
 LEFT JOIN LATERAL(SELECT workspace_id FROM governance_records g WHERE g.tenant_id=a.tenant_id AND g.kind='DECISION' AND g.payload->>'requestId'=a.request_id::text LIMIT 1)g ON true WHERE a.tenant_id=tenant_row AND coalesce(a.workspace_id,i.workspace_id,g.workspace_id) IS NOT DISTINCT FROM workspace_row ON CONFLICT DO NOTHING;

INSERT INTO security_activities(id,tenant_id,workspace_id,source,event_type,stage,origin_id,event_key,occurred_at,correlation_id,caller_subject,human_subject,ai_subject,action,resource_type,resource_id,decision,status,analysis_eligible,metadata)
SELECT md5('file:'||f.id)::uuid,f.tenant_id,f.workspace_id,
 CASE WHEN f.operation IN ('delegate','revokeSession','revokeToken') THEN 'AUTH' WHEN f.operation IN ('sync','syncSubject','delete') THEN 'SYNC' ELSE 'FILE' END,
 CASE WHEN f.operation IN ('retrieve','children','search','download','workflow.retrieve','workflow.list') THEN 'FILE_ACCESS_DECIDED' ELSE upper(f.operation) END,
 CASE WHEN f.operation IN ('retrieve','children','search','download','workflow.retrieve','workflow.list') THEN 'AUTHORIZATION' ELSE 'LIFECYCLE' END,
 f.id::text,'file:'||f.id,f.created_at,coalesce(f.session_id::text,f.id::text),f.subject,f.human_subject,
 coalesce(f.ai_subject,CASE WHEN f.subject LIKE 'client:%' OR f.subject LIKE 'workflow-chat-%' THEN f.subject END),
 f.operation,'retrieval_resource',f.resource_id,f.decision,CASE WHEN f.decision='ALLOW' THEN 'SUCCEEDED' WHEN f.decision='STEP_UP' THEN 'PENDING_APPROVAL' ELSE 'DENIED' END,
 f.operation IN ('retrieve','children','search','download','workflow.retrieve','workflow.list') AND (f.ai_subject IS NOT NULL OR f.subject LIKE 'client:%' OR f.subject LIKE 'workflow-chat-%'),
 jsonb_build_object('auditEventId',f.id,'returnedCount',f.returned_count,'aclVersion',f.acl_version,'reason',f.policy_reason,'matchedPolicies',f.matched_policies,'sessionId',f.session_id,'riskAssessment','NOT_MEASURED','imported',true)
FROM stitch_integration_audit f WHERE f.tenant_id=tenant_row AND f.workspace_id IS NOT DISTINCT FROM workspace_row ON CONFLICT DO NOTHING;

INSERT INTO security_activities(id,tenant_id,workspace_id,source,event_type,stage,origin_id,event_key,occurred_at,correlation_id,request_id,caller_subject,ai_subject,action,resource_type,resource_id,decision,status,metadata)
SELECT md5('mcp-state:'||i.id||':'||i.status)::uuid,i.tenant_id,i.workspace_id,'MCP','MCP_'||i.status,'EXECUTION',i.id::text,'mcp-state:'||i.id||':'||i.status,i.updated_at,i.decision_request_id::text,i.decision_request_id,i.actor_subject,coalesce(i.policy_subject,i.actor_subject),'mcp.tool.call','mcp_tool',i.tool_id::text,
 CASE WHEN i.status='DENIED' THEN 'DENY' WHEN i.status='PENDING_APPROVAL' THEN 'STEP_UP' WHEN i.status IN ('SUCCEEDED','EXECUTING','TOOL_ERROR') THEN 'ALLOW' END,i.status,
 jsonb_build_object('callId',i.id,'approvalId',i.approval_id,'errorCode',i.error_code,'resultHash',i.result_hash,'imported',true)
FROM mcp_invocations i WHERE i.tenant_id=tenant_row AND i.workspace_id IS NOT DISTINCT FROM workspace_row ON CONFLICT DO NOTHING;

INSERT INTO security_activities(id,tenant_id,workspace_id,source,event_type,stage,origin_id,event_key,occurred_at,correlation_id,request_id,caller_subject,human_subject,ai_subject,action,resource_type,resource_id,decision,status,risk_score,analysis_eligible,metadata)
SELECT md5('runtime:'||r.id)::uuid,r.tenant_id,r.workspace_id,'RUNTIME','RUNTIME_AUTHORIZATION_DECIDED','AUTHORIZATION',r.id::text,'RUNTIME:'||r.id||':RUNTIME_AUTHORIZATION_DECIDED',r.created_at,coalesce(r.request_id::text,r.id::text),r.request_id,r.agent_external_id,null,r.agent_external_id,r.action,r.resource_type,r.resource_id,r.decision,'EVALUATED',r.risk_score,true,
 jsonb_build_object('sessionId',r.session_id,'toolId',r.tool_id,'latencyMs',r.latency_ms,'imported',true)
FROM agent_runtime_events r WHERE r.tenant_id=tenant_row AND r.workspace_id IS NOT DISTINCT FROM workspace_row ON CONFLICT DO NOTHING;

INSERT INTO security_activities(id,tenant_id,workspace_id,source,event_type,stage,origin_id,event_key,occurred_at,correlation_id,caller_subject,human_subject,ai_subject,action,resource_type,resource_id,status,metadata)
SELECT md5('workflow:'||g.id)::uuid,g.tenant_id,g.workspace_id,'WORKFLOW','WORKFLOW_'||g.kind,CASE WHEN g.kind='EXECUTION' THEN 'EXECUTION' ELSE 'LIFECYCLE' END,g.id::text,'workflow:'||g.id,g.created_at,coalesce(g.payload->>'traceId',g.id::text),coalesce(g.payload->>'executedBy',g.payload->>'requestedBy'),g.payload->>'requestedBy',coalesce(g.payload->>'aiSubject',CASE WHEN g.payload->'request'->'principal'->>'type'='AI_AGENT' THEN g.payload->'request'->'principal'->>'id' END),
 lower(g.kind),'workflow_record',g.id::text,coalesce(g.payload->>'status','RECORDED'),jsonb_build_object('contractId',g.payload->>'contractId','executionId',CASE WHEN g.kind='EXECUTION' THEN g.id::text END,'approvalId',g.payload->>'approvalId','imported',true)
FROM governance_records g WHERE g.tenant_id=tenant_row AND g.workspace_id IS NOT DISTINCT FROM workspace_row AND g.kind IN ('WORKFLOW_PROPOSAL','CONTRACT','EXECUTION','VERIFICATION') ON CONFLICT DO NOTHING;


-- Retained approval states are lifecycle evidence, never execution grants.
INSERT INTO security_activities(id,tenant_id,workspace_id,source,event_type,stage,origin_id,event_key,occurred_at,correlation_id,request_id,caller_subject,human_subject,ai_subject,action,resource_type,resource_id,status,metadata)
SELECT md5('approval:'||p.id||':'||p.status)::uuid,p.tenant_id,coalesce(i.workspace_id,a.workspace_id,g.workspace_id),'AUTH','APPROVAL_'||p.status,'LIFECYCLE',p.id||':'||p.status,'file:'||p.id||':'||p.status,
 coalesce(p.decided_at,p.created_at),p.request_id::text,p.request_id,p.decided_by,a.metadata->'context'->>'requestedBy',CASE WHEN a.identity_type='AI_AGENT' THEN a.metadata->>'principal' END,
 'approval.decide','approval',p.id::text,p.status,jsonb_build_object('approvalId',p.id,'callId',i.id,'approvedBy',p.decided_by,'reason',p.reason,'imported',true)
FROM approvals p
 LEFT JOIN LATERAL(SELECT * FROM mcp_invocations i WHERE i.tenant_id=p.tenant_id AND i.approval_id=p.id LIMIT 1)i ON true
 LEFT JOIN LATERAL(SELECT * FROM audit_logs a WHERE a.tenant_id=p.tenant_id AND a.request_id=p.request_id ORDER BY a.created_at DESC LIMIT 1)a ON true
 LEFT JOIN LATERAL(SELECT workspace_id FROM governance_records g WHERE g.tenant_id=p.tenant_id AND g.kind='DECISION' AND g.payload->>'requestId'=p.request_id::text LIMIT 1)g ON true
WHERE p.tenant_id=tenant_row AND coalesce(i.workspace_id,a.workspace_id,g.workspace_id) IS NOT DISTINCT FROM workspace_row ON CONFLICT DO NOTHING;

-- Legacy module delivery records are observations, not additional authorization samples.
INSERT INTO security_activities(id,tenant_id,workspace_id,source,event_type,stage,origin_id,event_key,occurred_at,correlation_id,caller_subject,action,resource_type,resource_id,status,metadata)
SELECT md5('outbox:'||e.id)::uuid,e.tenant_id,e.workspace_id,
 CASE WHEN e.aggregate_type IN ('SECURITY_RESPONSE','CONTROL') THEN 'CONTROL' WHEN e.aggregate_type LIKE 'MCP%' THEN 'MCP' WHEN e.aggregate_type='GOVERNANCE' THEN 'WORKFLOW' ELSE 'OTHER' END,
 e.event_type,'OBSERVATION',e.id::text,'outbox:'||e.id,e.created_at,coalesce(e.payload->>'traceId',e.aggregate_id),e.payload->>'subject',e.event_type,e.aggregate_type,e.aggregate_id,'RECORDED',
 jsonb_build_object('imported',true,'observationState','LEGACY_OUTBOX','approvalId',e.payload->>'approvalId','callId',e.payload->>'callId','errorCode',e.payload->>'errorCode')
FROM security_event_outbox e WHERE e.tenant_id=tenant_row AND e.workspace_id IS NOT DISTINCT FROM workspace_row AND e.event_type NOT IN ('AI_AGENT_ACTION','AI_AGENT_THREAT','GOVERNANCE_POLICY_DECISION') ON CONFLICT DO NOTHING;
  END LOOP;
 END LOOP;
 PERFORM set_config('app.tenant_id','',true);
 PERFORM set_config('app.workspace_id','',true);
END $import$;

ALTER TABLE security_activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_activities FORCE ROW LEVEL SECURITY;
CREATE POLICY security_activity_scope ON security_activities USING(tenant_id=nullif(current_setting('app.tenant_id',true),'')::uuid AND workspace_id IS NOT DISTINCT FROM nullif(current_setting('app.workspace_id',true),'')::uuid)
 WITH CHECK(tenant_id=nullif(current_setting('app.tenant_id',true),'')::uuid AND workspace_id IS NOT DISTINCT FROM nullif(current_setting('app.workspace_id',true),'')::uuid);
CREATE TRIGGER security_activity_immutable BEFORE UPDATE OR DELETE ON security_activities FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();

