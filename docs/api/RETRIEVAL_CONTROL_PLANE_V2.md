# Retrieval control plane v2

ZT owns AI access policies. A source client owns its existing user ACL and data. Generic base: `/v1/integrations/retrieval`; `/v1/integrations/stitch` is a compatibility alias to the same enforcement service. Enable with `ZT_RETRIEVAL_ENABLED=true` (legacy flag remains supported).

## Onboarding

1. Create/select a tenant workspace (`/v1/workspaces`), one source connection per workspace.
2. Connector obtains a verified CONNECTOR JWT with RETRIEVAL_SYNC role (legacy STITCH_SYNC accepted), matching tenant/workspace/audience/JTI/expiry. GET /context exposes its verified subject for registration, without content access.
3. ZT administrator registers POST `/v1/retrieval/connections`: connectionId, displayName, exact connectorSubject, enabled. Existing ownership cannot be changed. IDs are unique per tenant. Registration never fetches a user-provided URL.
4. Bound connector syncs subjects/resources and commits a complete source snapshot. Only SUBJECT/GROUP ACLs are accepted. AI_SUBJECT grants are rejected. Legacy aiAccess is optional and ignored. The connector cannot author/publish ZT policies.
5. Administrator publishes connection-scoped AI policies. Default deny without matching ALLOW.
6. Human authenticates and delegates to the exact AI subject. AI authenticates separately and uses the delegation. JWT AI binds retrieval_session_id (legacy stitch_session_id accepted).

The source feed `/zt/changes`, deletion contract `/zt/deletions`, limits and lifecycle methods otherwise follow the [existing retrieval contract](STITCH_INTEGRATION_V1.md). A new provider maps its own ACL/data/change feed to this standard. A URL alone cannot establish source identity or permissions.

## Server-derived policy context

| Field | Value |
|---|---|
| principal.type / id | AI_AGENT / authenticated AI subject |
| action | retrieval.read / retrieval.search / retrieval.list / retrieval.download |
| resource.type / id / kind | retrieval_resource / scoped ID / registered kind |
| resource.ancestors | Resource itself and registered parent IDs |
| context.connectionId / workspaceId | ZT registration / verified scope |
| context.humanSubject / groups / sessionId | Current source/claims/delegation intersection |

Workspace and tenant-wide ACTIVE policies are read fresh, bounded to 200 policies. Invalid active DSL fails closed. Existing PolicyEvaluator resolves deny > step_up > allow > default deny. Human reads use source ACL. AI policy cannot expand human permissions. This path does not include Canary/risk/TEE or approval-resume. STEP_UP releases no content.

```go
policy "allow_client_retrieval" {
  effect allow
  principal.type == "AI_AGENT"
  action in ["retrieval.read", "retrieval.search", "retrieval.list", "retrieval.download"]
  resource.type == "retrieval_resource"
  condition { context.connectionId == "your-client" }
}
```

A subtree DENY adds `resource.ancestors contains "restricted-folder-id"`. ZT calculates ancestors; request arguments cannot inject them. Dashboard-generated DSL is stored in the existing policies table/Policy Studio. Validate, DRAFT and Publish are separate. To lift DENY, deactivate that policy. Other ACTIVE versions and tenant-wide rules still apply.

## Enforcement and changes

AI checks constrain reads, downloads, folder/channel listing and child contents, and the candidate resource set before search. Search candidates need both read and search permission. Source ACL is checked first; content is rechecked before return. Current caller/session/source ACL comes from trusted server state.

PolicyService Publish/Deactivate/Rollback invalidate the relevant cache/disclosures and queue recipient deletion in the same transaction, separately from source readiness. A policy fingerprint is also checked on AI requests. This invalidation conservatively covers workspace disclosures, not only the selected folder.

Disabling a registered connection blocks content and ingestion. Its bound connector/recipient can still read pending deletion events and acknowledge erasure; these cleanup endpoints never return document content. Connector erasure dispatch runs even if its source-sync cycle fails.

Admin endpoints: `/v1/retrieval/connections`, `/v1/retrieval/connections/resources`, `/v1/retrieval/audit`. Audit includes policy_reason and matched_policies. Search can return ALLOW with zero results due to filtering; a reason can describe excluded candidates. Authentication/session failure may precede retrieval audit.

V8 adds registration/metadata and no sample connection or ALLOW policy. Existing V7 data remains. Historical stitch_ table/class names are internal compatibility details. Generic public calls have no hardcoded Stitch-specific authorization rules.

Use `client.retrieval.for_session(session_id)` in Python or generic REST/MCP for the next client. It needs its own workspace-bound source/IdP/AI setup; existing fixed Stitch tokens cannot authorize another workspace. The local Stitch fixture remains one dedicated client. Builds, tests, runtime and external deployment have not been performed for this change.

The ZT Retrieval Access page includes a client contract console. Its explicit actor credentials and selected workspace are separate from the admin policy controls. Verify `context` before sync/delegation/AI reads. Credentials clear on workspace change and are never persisted to browser storage.
