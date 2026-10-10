# Service accounts and AI Agent execution

A service authenticates with its existing client ID and secret. A registered AI Agent becomes the policy principal only after an administrator connects it to that service in the current tenant/workspace. One service may connect to multiple Agents, and an Agent may connect to multiple services. MCP upstream registration still describes the destination and tools; it does not identify the calling AI.

## Configure and use

1. In **Settings → Operational Settings**, register the service account and AI-agent identities.
2. In **Service ↔ Agent connections**, select the service and each Agent and save the connection. Connections are also managed from the selected Agent's detail screen.
3. Optionally set one default Agent per service/workspace. Saving a new default atomically clears the previous default.
4. The MCP tool binding's allowed subjects must still include the authenticated service, for example `client:commerce-service`.
5. Create and publish policies with an Agent condition such as `condition { principal.id == "order-agent" }`, `refund-agent`, etc. A connection alone does not authorize an action. `principal.id` belongs inside `condition`; the existing DSL's top-level clauses are principal type, action and resource type.
6. The client sends `X-ZT-Agent-Id: refund-agent` on `/v1/mcp/authorize`, `/v1/mcp/json-rpc` and the approval resume request. This header is a selection, not an identity proof. ZT verifies it against persisted service/Agent connections.
7. **MCP Gateway → Execution Agent** lets a service caller select its linked Agent and displays the authenticated service separately from the effective policy Agent. The credential and selection stay in page memory.

Example requests use the same credentials, changing only the selected Agent:

```http
POST /v1/mcp/json-rpc
X-Tenant-Id: <tenant UUID>
X-Workspace-Id: <workspace UUID>
X-Client-Id: commerce-service
X-API-Key: <service secret>
X-ZT-Agent-Id: refund-agent
Content-Type: application/json

{"jsonrpc":"2.0","id":"unique-request-id","method":"tools/call","params":{"name":"cancelOrder","arguments":{"orderId":"TEST-ZT-001"}}}
```

Service `commerce-service` authenticates the connection. Policy principal `refund-agent` determines Agent policy, risk and behavior processing. The MCP server receives the registered upstream tool invocation, not a credential for the Agent. Service identity remains the owner of the call and controls tool-binding access.

## Selection and compatibility

| State | Header omitted | Header supplied |
|---|---|---|
| No connections | Existing `client:<id>` behavior | Reject unknown Agent selection |
| Default Agent configured | Use default | Use the explicitly linked, active Agent |
| Exactly one enabled connection, no default | Use that Agent | Verify explicit selection |
| Multiple enabled connections, no default | Reject; require Agent selection | Verify explicit selection |
| All connections disabled | Reject; never restore legacy privileges | Reject |

Connections are exact-workspace records; there is no cross-workspace fallback. Agents may be tenant-wide, but services bound to another workspace cannot be connected here. Inactive Agents, revoked/expired clients and disabled connections are rejected. OIDC and administrator callers retain their existing identity and cannot impersonate a registered service by supplying the Agent selection header.

## Approval, execution and audit

- Invocation storage includes authenticated service, effective policy Agent and connection revision.
- Approval payloads bind those values to the existing saved tool, arguments, caller and policy constraints.
- Resume requires the original authenticated caller, effective Agent and unchanged connection revision. Changing/re-enabling the selected connection expires its pending approvals and marks their saved calls DENIED with AGENT_BINDING_CHANGED. Create a new call and obtain approval again.
- Reusing an RPC ID for a different Agent is rejected. Calls are never dispatched again by replaying a saved RPC ID.
- Approval remains independent human approval. Connections do not grant approval rights.
- Policy audit metadata and MCP lifecycle events retain both identities. Agent Details displays scoped MCP approval/execution records; MCP history and approval review show service and Agent separately.
- Configuration changes enter the durable security-event outbox with the administrator identity.
- A configuration change cannot retract an external operation already claimed and dispatched. Disable prevents subsequent authorization/resume, not rollback of a completed business operation.

## API

| Endpoint | Access and behavior |
|---|---|
| `GET /v1/mcp/agent-bindings` | PLATFORM/ADMIN/AUDITOR/SOC_ANALYST/AGENT_MANAGER; current scope's connections |
| `POST /v1/mcp/agent-bindings` | PLATFORM/ADMIN; `{clientId: UUID, agentId: UUID, enabled: boolean, isDefault: boolean}`; upsert one pair |
| `GET /v1/mcp/agent-options` | Authenticated service sees only its own current-workspace Agents |
| `GET /v1/mcp/identity` | Authenticated caller; returns `authenticatedSubject`, `policySubject`, `agentLinked`; accepts the selection header |
| `GET /v1/mcp/agent-calls?subject=refund-agent` | Authorized operators; latest 100 Agent calls in current workspace |

Disabling is retained as a persisted connection rather than deleting the record and accidentally returning the service to legacy mode. No credential values appear in connection/query responses.

## Deployment and verification

Flyway V9 adds `service_agent_bindings` with forced tenant/workspace RLS, pair uniqueness and a unique default per service/scope. It adds effective-principal/revision columns to MCP calls without rewriting old records. Apply using the usual authorization API and dashboard build/deployment workflow.

```bash
mvn -pl apps/authorization-api -am -Dtest=Mcp*Test -Dsurefire.failIfNoSpecifiedTests=false test
```

`McpAgentBindingPostgresTest` additionally tests the complete migration chain and connection queries under a non-superuser PostgreSQL role. It runs only with `ZT_TEST_DB_URL` pointing to a fresh disposable test database using the fixture credentials in that test. Never point it at an application database.
