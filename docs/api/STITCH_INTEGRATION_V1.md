# Stitch integration contract v1

This is a standard integration implementation, not a connection to Stitch's private systems. It is separate from `/v1/poc/stitch`. The customer must implement the source contract, provision its issuer and claims, and deploy network/DB controls. No build, test, migration, live request or deployment was performed while creating this change.

## Implemented boundaries

| Area | Implementation | External requirement |
|---|---|---|
| Authentication | Verified OIDC issuer/signature/expiry, audience/scope/JTI, source revocation feed; registered AI credentials | Trusted IdP and claim mapping |
| Delegation | Human-issued, AI-bound session, maximum 15 minutes, source membership rechecked | Trusted app transports the session privately; AI JWT includes stitch_session_id |
| ACL | Owner, user, group and AI subject; READ/SEARCH; inherited DENY; parent AI restriction | Authoritative folder/channel/file/attachment ACL sync |
| Retrieval | Text read, child/message listing, binary download up to 1 MiB | Source supplies file bytes and extracted text |
| RAG | PostgreSQL GIN full-text index, precomputed embeddings and bounded cosine ranking | Source/query use the same embedding model and dimension; embedding generation stays with the customer |
| Cache | Result IDs only, scope/caller/groups/session key, 60-second TTL; version invalidation and ACL recheck | Source permission changes must reach the adapter |
| Lifecycle | Subtree purge, index/blob/cache deletion, disclosure TTL, pending recipient erasure events and acknowledgments | Recipient must erase its own copies, memories, caches and provider stores |
| Deployment | Private DB/API NetworkPolicy and restricted DB-role artifacts | Apply/adapt templates with an enforcing CNI, TLS, credentials and real labels/IPs |

This adapter enforces source ACLs and delegation directly. It does not automatically reuse payment policies, TEE attestation, MCP approval workflows, or the full ActionService/risk pipeline. Existing PoC and order demos remain separate.

## Enable

```properties
ZT_STITCH_INTEGRATION_ENABLED=true
ZT_OIDC_ENABLED=true
ZT_OIDC_ISSUER_URI=https://your-issuer.example/realms/your-realm
ZT_OIDC_JWK_SET_URI=https://your-issuer.example/realms/your-realm/protocol/openid-connect/certs
ZT_STITCH_INTEGRATION_AUDIENCE=zt-stitch-integration
ZT_STITCH_ALLOW_DEVELOPMENT_SYNC_KEY=false
ZT_STITCH_SESSION_TTL_SECONDS=300
```

Only set JWK_SET_URI when a separate reachable JWK endpoint is needed; issuer validation remains enabled. Configure HTTPS and trusted certificate roots for real deployments. The integration is disabled by default. Flyway V7 adds its isolated tables even when runtime endpoints are disabled.

All scoped requests use `X-Tenant-Id` and `X-Workspace-Id`. JWTs require matching tenant_id/workspace_id, sub, jti, exp, aud and actor_type. HUMAN, AI_AGENT and CONNECTOR are separate. CONNECTOR additionally requires STITCH_SYNC role. A platform/admin API key cannot impersonate HUMAN. The development sync-key option is ingestion-only and must remain false in real deployments.

JWT group claims are intersected with the live source membership record. Unknown/disabled users fail closed. Existing delegated groups cannot gain a new group until a new human delegation is issued. Membership removals take effect after successful source sync without waiting for cached results to expire. IdP logout/revocation must appear in `token.revoked` or the corresponding session/user revocation feed; offline JWT validation alone does not discover remote logout.

## REST contract

Source state starts incomplete. Resource/directory mutations mark it incomplete; a connector commits ready=true with expectedAclVersion only after the complete change feed is synchronized. HUMAN delegation and content retrieval fail closed while incomplete or stale. The default maximum snapshot age is 120 seconds (`ZT_STITCH_MAX_SOURCE_AGE_SECONDS`, bounded 30–3600); the local fixture uses 600 seconds. Use one source writer per workspace. Recipient erasure acknowledgment is tracked separately from source freshness.

Base: `/v1/integrations/stitch`.

| Method/path | Caller | Function |
|---|---|---|
| GET /context, /capabilities | Authenticated actor | Identity/contract information |
| POST /subjects | CONNECTOR/STITCH_SYNC | Live groups, active status, monotonic sourceVersion |
| GET/POST /source-state | CONNECTOR/STITCH_SYNC | Compare-and-set completion checkpoint of source ACL snapshot |
| POST /revoked-tokens | CONNECTOR/STITCH_SYNC | Revoke jti until its expiry; revoke related human delegations |
| POST /resources | CONNECTOR/STITCH_SYNC | Replace one resource, its complete ACL, chunks and optional blob |
| DELETE /resources/{id} | CONNECTOR/STITCH_SYNC | Tombstone subtree and remove stored content/index/blob/ACL |
| POST /sessions | HUMAN | Delegate to exact aiSubject; session groups come from verified claims/source intersection |
| DELETE /sessions/{id} | HUMAN owner or connector | Revoke session |
| POST /retrieve, /children, /download | HUMAN or delegated AI | Live ACL-authorized retrieval |
| POST /search | HUMAN or delegated AI | Lexical / optional queryEmbedding ranking |
| POST /mcp | HUMAN or delegated AI | Stateless MCP JSON-RPC 2025-06-18 |
| GET /calls | Own actor; connector sees workspace | Retrieval audit metadata |
| GET /deletion-events | Recipient AI or connector | First 100 pending erasure requests |
| POST /deletion-events/{id}/ack | Recipient AI or connector | Record recipient receipt, not physical-erasure proof |
| PUT /retention | CONNECTOR/STITCH_SYNC | Disclosure TTL ceiling (1–365 days, default 7) |
| POST /maintenance | CONNECTOR/STITCH_SYNC | Enforce purgeAfter, expiry and metadata cleanup |

Use issuer `sub` values as ownerSubject and SUBJECT grants; do not assume usernames equal JWT subjects. Group names must match the issuer and source directory. Resource IDs accept letters, digits, underscore, dot, colon and hyphen, up to 128 characters. Changes must increase sourceVersion; 409 rejects old or duplicate versions. Sync parent resources before children.

```json
{
  "resourceId": "payroll-october",
  "parentId": "payroll",
  "kind": "FILE",
  "title": "October payroll",
  "ownerSubject": "issuer-user-sub",
  "aiAccess": "ALLOW",
  "sourceVersion": 1,
  "purgeAfter": null,
  "content": "Source-extracted text",
  "acl": [
    {"principalKind":"GROUP","principalId":"finance","permission":"READ","effect":"ALLOW"},
    {"principalKind":"GROUP","principalId":"finance","permission":"SEARCH","effect":"ALLOW"}
  ],
  "chunks": [{"content":"Source-extracted text","embedding":null}],
  "fileBase64": null
}
```

Kinds: FOLDER, CHANNEL, FILE, MESSAGE, ATTACHMENT. Attachments require a FILE/MESSAGE parent, messages require a CHANNEL parent. Root folders/channels/files are allowed. Cycles and depth over 64 are rejected. An ancestor DENY or expiry blocks descendants. Any matching SUBJECT/GROUP/AI_SUBJECT DENY wins. Owner grants READ/SEARCH unless denied. AI permissions are the intersection of the delegated human's permissions and the AI restrictions; AI ALLOW entries on a level make that level an AI-subject whitelist.

```json
{"subject":"issuer-user-sub","groups":["employees","finance"],"active":true,"sourceVersion":1}
```

Retrieve/list/download body: `{"resourceId":"...","sessionId":"AI-delegation-UUID"}`. Human requests omit sessionId. Search body: `{"query":"payroll","sessionId":"...","queryEmbedding":null}`. Embeddings are finite numeric arrays up to 1536 dimensions; generation is not provided by this adapter. AI service credentials plus the random session handle are a scoped capability: keep the handle in trusted backend state, never share it between users or put it in prompts. AI JWTs must additionally be issued with matching stitch_session_id claim.

## MCP / Stitchy integration

Connect the trusted Stitchy backend directly to the adapter's `/mcp`, preserving authenticated identity. It supports initialize, initialized notification (202), ping, tools/list and tools/call. GET streaming is not implemented. After initialize use `MCP-Protocol-Version: 2025-06-18`. Inject `X-ZT-Delegation` at the trusted transport layer. The tool schema has no session or actor argument. Tools: readDocument, searchDocuments and listChannelMessages (authorized children).

When an Origin header is present it must match `ZT_STITCH_ALLOWED_ORIGINS` exactly. Configure the actual application origins; wildcard origins are not supported.

Do not register this endpoint as a generic upstream with a shared connector credential: that would lose the original actor/delegation and CONNECTOR is forbidden from retrieving human content. Existing `/v1/mcp/json-rpc` gateway sessions/bindings/approval history are not automatically reused.

Python SDK:

```python
from zt_security import ZtSecurityClient, StitchAccessDenied

# obtain_human_token / embedding_provider are implemented by the trusted application.
with ZtSecurityClient(api_url, tenant_id=tenant, workspace_id=workspace,
                      bearer_token=obtain_human_token, retries=0) as human:
    delegation = human.stitch.delegate("client:order-ai-client")

with ZtSecurityClient(api_url, tenant_id=tenant, workspace_id=workspace,
                      client_id="order-ai-client", api_key=agent_secret, retries=0) as ai:
    retrieval = ai.stitch.for_session(delegation["sessionId"])
    try:
        approved_chunks = retrieval.search("payroll")
        # Only approved_chunks may become LLM context. Do not log/cache full content here.
    except StitchAccessDenied:
        # Stop this workflow; do not fall back to direct source/DB retrieval.
        raise
```

## Source connector contract

`apps/stitch-connector/connector.py` uses OAuth client_credentials for a scoped CONNECTOR JWT, TLS/hostname verification, an explicit host allowlist, no redirects and bounded payloads. No source URLs come from AI input. Configure `apps/stitch-connector/config.example.env` and supply actual secrets via your secret manager. The worker is not started by the normal Compose stack.

Source implements:

1. `GET /zt/changes?tenantId=...&workspaceId=...&cursor=...&limit=100` with bearer source credential.
2. Response: `{"changes":[{"type":"subject.upsert","payload":{...}}],"nextCursor":"opaque","hasMore":false}`. Types: subject.upsert, resource.upsert, resource.deleted, session.revoked, token.revoked. Parent-before-child order and durable replayable cursors are required. Deleted resource payload uses resourceId; session revocation uses sessionId; token revocation uses jti/expiresAt.
3. `POST /zt/deletions`: eventId, tenantId, workspaceId, resourceId, targetSubject, reason.
4. Recipient deletes its caches/chats/memory/vector entries/provider references and returns `{"eventId":"same-id","status":"DELETED","receiptId":"recipient-receipt"}` only after completing its deletion contract.

Recipient deletion must be idempotent by eventId. A lost acknowledgment can cause the same event to be delivered again. Return the same successful receipt for an already completed event.

Cursor is advanced only after a processed page. Version-conflict upserts and already-missing deletion/session records are safe replay cases. Requests carry no arbitrary destination from a source change. Never put the current credential in the cursor. Worker source metadata is trusted only through the configured source credential and sync JWT; this is not a source signature/attestation verifier.

Standalone optional worker: `docker compose --env-file YOUR_CONFIG -f docker/stitch-connector.compose.yml up -d --build`. Configure its external network name to match your stack. For Kubernetes, the CronJob template runs maintenance and deletion synchronization. A paused/missing worker means time-based physical cleanup and external requests may remain pending; retrieval still blocks expired resources/sessions immediately.

The worker marks source state incomplete before processing, then commits the latest ACL version after feed catch-up and local maintenance. It processes at most ten 100-change pages per cycle; backlog keeps retrieval blocked until caught up. External deletion failures remain pending and are retried separately. The source must provide a consistent ordered change stream. Unreported source changes are not detectable, and missing heartbeat eventually blocks retrieval.

## Retention and deletion meaning

- The adapter stores source content, optional binary blobs and extracted chunks; resource purgeAfter controls their retention. Null means source-owned retention until a source deletion, not an automatic fixed content TTL.
- Result caches contain IDs only and expire after 60 seconds. Cache hits are re-authorized against current ACL/version/session. Semantic ranking is not cached.
- Disclosure records contain IDs/subject/session/version, never document bodies. They default to 7 days; decreasing the ceiling shortens existing expiry. Receipt TTL and credential/session TTL are different concepts.
- ACL/content/membership/session/token changes conservatively revoke workspace disclosures and enqueue recipient erasure requests. This may invalidate more content than strictly necessary.
- Maintenance removes expired disclosure/session/token/cache rows, old audit metadata (30 days), and acknowledged deletion metadata (30 days). Pending erasure requests are retained until acknowledged.
- Subtree deletion removes local blobs/index/ACL and replaces title/body with a tombstone. Minimal resource identity/ancestry/owner metadata remains for versioning and scope integrity.
- DB row deletion does not erase WAL, backups or prior model outputs. Database/object-store backups and provider retention require separate customer controls. Acknowledgment is a recipient statement, not independently verified physical erasure. No legal-compliance guarantee is made.

## Deployment and capacity

`infra/stitch-integration/` contains default-deny NetworkPolicy, controlled gateway routes/TLS configuration, private DB grants and connector CronJob templates. These are unapplied artifacts, not evidence that the current Docker DB/API is isolated. Existing development Compose ports remain unchanged.

Adapt namespaces/labels, actual images, TLS certificate/CA, secrets, storage, IdP/source IPs and database schema. Use a CNI enforcing NetworkPolicy; policies are additive and other broad allow rules can defeat intended isolation. Standard NetworkPolicy cannot restrict HTTPS by domain/path. Constrain DNS, IAM/secrets, source API audience, and use an egress proxy where fixed IP policy is insufficient. Kubernetes privileged node/admin access and DB administrators remain trusted boundaries.

Run migrations with a separate privileged credential, then use the NOBYPASSRLS runtime role and disable Flyway for that runtime. The supplied role targets only integration routes; other monolith endpoints must remain unrouted or receive explicit grants. DB credentials must never be given to AI/clients: a DB credential holder can set tenant session variables, so RLS does not replace network/credential isolation.

Correctness uses workspace transaction locks; requests within one workspace serialize. Limits: 5000 resources, 50000 ACL grants, depth 64, 128 source chunks/resource, 4 MiB request body, 1 MiB binary blob, and bounded semantic candidate retrieval. These bounds intentionally fail closed; this implementation does not claim production-scale ANN/vector performance. Query/source embeddings must come from the same model/dimension. Larger deployments need a vendor index/connector implementation preserving the same prefilter and revision contracts.

Official references: [Spring Security JWT validation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Kubernetes NetworkPolicy](https://kubernetes.io/docs/concepts/services-networking/network-policies/), [MCP 2025-11-25 transport reference](https://github.com/modelcontextprotocol/modelcontextprotocol/blob/main/docs/specification/2025-11-25/basic/transports.mdx). This adapter advertises the compatible 2025-06-18 protocol rather than claiming support for newer protocol revisions.

The local realm follows [Keycloak's protocol-mapper examples](https://github.com/keycloak/keycloak/blob/main/testsuite/model/src/test/resources/exportimport/dir/test-realm.json). Local users and credentials are synthetic fixtures, not a customer identity integration.
