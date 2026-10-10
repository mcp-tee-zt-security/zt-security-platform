# MCP dashboard registration and execution

The **Agents & Connections > MCP Gateway** page manages upstream registrations, tool bindings, policy-governed calls and MCP approvals. Management actions require an authenticated PLATFORM or ADMIN role. The navigation role selector does not grant server permissions.

## Apply this change

From the repository root:

```powershell
docker compose -f docker/docker-compose.yml up -d --build authorization-api dashboard
```

Flyway V5 adds `mcp_upstream_servers`. Existing migrations and data remain intact. Registered connections are isolated by tenant and exact workspace, persist across restarts and are shared between backend replicas. Deployment-configured connections remain supported. A database registration with the same ID takes precedence in that scope, including when disabled.

## Connect the order MCP server

Start the order MCP server on the host at port 9998 with Streamable HTTP and endpoint `/mcp`. Its order data must use the intended test Redis instance. The existing `ai-agent-client` and Ollama are not required for dashboard tests.

In **Upstream servers**:

1. Use server ID `orders` and display name `Order MCP Server`.
2. Enter `http://host.docker.internal:9998/mcp` when ZT runs in Docker, or `http://localhost:9998/mcp` when ZT runs directly on the host.
3. Explicitly select **Allow local development HTTP for this connection**.
4. For the unauthenticated development server, select **No upstream authentication**.
5. Save the upstream, select it, then click **Connect & load tools**.

Discovery performs MCP initialization and `tools/list`, not `tools/call`. It uses the existing size/time limits and no redirects. Only the first discovery page is returned; the UI reports when the upstream has more pages. Discovery does not prove that every advertised tool can execute successfully.

## Register a tool

Choose **Register this tool** next to `cancelOrder`. Review the imported description, risk classification, input schema and caller permissions before saving.

- Registered name: `cancelOrder`.
- Upstream: `orders`, tool `cancelOrder`.
- Allowed subject: `api-key` for the default shared development credential, `client:<client-id>` for a service client, or the JWT `sub` for OIDC.
- Restrict the test schema to an order reserved for testing, for example `TEST-ZT-001`.
- Enable **Require independent approval** when testing approval behavior.

```json
{
  "type": "object",
  "properties": {
    "orderId": {"type": "string", "enum": ["TEST-ZT-001"]}
  },
  "required": ["orderId"],
  "additionalProperties": false
}
```

The order example is a form template. It does not create an order, create an authorization policy or execute a tool. Tool creation and binding registration commit in one transaction. Existing tool metadata is reused rather than edited across other workspaces. A removed binding retains the tenant's tool definition and previous invocation records.

Upstream schemas must fit the gateway's restricted JSON Schema subset. Import removes the `$schema` annotation and sets the root object's `additionalProperties` to false; unsupported validation keywords must be reviewed explicitly before registration. Registration does not make those constraints silently disappear.

## Activate policy and execute

Use **Policy Studio** to validate and activate the required policy for action `mcp.tool.call`, principal type `AI_AGENT`, resource type `mcp_tool` and the registered tool name in `context.mcp.tool`.

In **Execute & inspect**, choose the tool and enter:

```json
{"orderId":"TEST-ZT-001"}
```

**Evaluate only** never executes upstream. **Execute tool** creates a new operation ID and may change business data. **Replay same request** resends the saved operation ID to exercise replay protection, not to retry a new business operation. **Inspect saved call** retrieves state for the original caller. After `UNKNOWN` or an unfinished `EXECUTING` state, reconcile against upstream records before starting a new operation.

## Approve and resume

The **MCP approvals** tab reads approvals linked to MCP invocation records. It is separate from the existing SDK contract workflow queries.

Use **Caller identity** to apply a registered service-client credential or OIDC token for this page only. Credentials remain in component memory and are not stored in localStorage or sent to the registration database. Refreshing or closing the page loses them. The tenant/workspace stays configured by the dashboard deployment.

A practical development flow is:

1. Register a service client through the existing client administration workflow.
2. Include `client:<client-id>` in the tool's allowed subjects.
3. Apply that client's ID and secret, then execute the approval-required tool.
4. Save the returned call ID.
5. Switch to the independent configured administrator credential, open **MCP approvals**, review the saved arguments and approve.
6. Switch back to the original service client, enter the saved call ID and click **Resume approved call**.

Service clients do not acquire approval rights. OIDC requires a server-configured issuer, valid roles, MCP audience and matching tenant/workspace claims. The server rejects self-approval even if the requester has an administrator role. Approval does not itself execute anything. Resume rechecks the original binding, expiry, arguments and current policy; relevant policy changes may require another approval.

Authorized approvers can inspect original tool arguments in the request detail. Queue and call-history summaries omit raw arguments. Consider this access when defining data retention and sensitive-input policies.

## Registration controls

| Variable | Default / meaning |
| --- | --- |
| `ZT_MCP_REGISTRATION_HOSTS` | Exact allowed registration hosts: `host.docker.internal,localhost,127.0.0.1` |
| `ZT_MCP_ALLOW_DEVELOPMENT_HTTP_REGISTRATION` | `true`; explicit per-connection HTTP option for the three local development hosts |
| `ZT_MCP_ALLOW_HTTP` | Existing global HTTP setting; default `false` |
| `ZT_MCP_CREDENTIAL_ENV_ALLOWLIST` | Empty; allowed names of deployment-managed bearer-token environment variables |

HTTPS is required unless HTTP is explicitly permitted. URLs cannot contain credentials, query strings or fragments. For another upstream, deployment administrators first add its exact hostname to the registration allowlist. Registration rules are rechecked when a database connection is resolved. Disabling a connection blocks new resolution and approval resume; it does not cancel a network call already in flight.

The UI never accepts or persists upstream token values. To use authentication, deployment administrators provision the token environment variable and allow its name. The UI shows only the reference and whether a value is present. An allowed reference grants access to that upstream credential, so trusted hosts and credential references must be configured together. Network egress and upstream authentication still need to enforce the intended trust boundary. A hostname allowlist alone is not comprehensive SSRF or DNS-rebinding protection.

## Execution evidence

**Call history** shows the latest 100 scoped gateway records. Administrators can see workspace calls; other callers see their own. It does not measure calls received by the upstream.

For a real enforcement test, instrument `cancelOrder` on the MCP server and compare its invocation count before and after the request. Also inspect the actual order status. `SUCCEEDED` indicates an upstream result was received and recorded; it does not interpret strings such as `ORDER_NOT_FOUND` as a completed cancellation.

| Scenario | Expected cancellation-call increase |
| --- | --- |
| DENY | 0 |
| ALLOW with all other checks passed and no extra approval | 1 |
| Approval required, before approval | 0 |
| Approved request resumed successfully | 1 |
| Same operation replayed | 0 additional calls |

The existing external order repository is not modified by this change. No upstream method counter is supplied here, and the existing AI client is not automatically rerouted through ZT. Direct access to the order MCP server remains possible unless network and upstream credential controls prevent it.

Builds, tests and live execution were not run as part of this implementation. Registration-control test sources were added but not executed.
