# Common SDK contract, revision 1.0.0

This contract describes the integrated 4.75 authorization API. Python SDK 2.1.0
is the first SDK updated against this revision. Other language SDK code has not
been changed during the Python productization work. Contract revision, server
version and SDK package version are independent identifiers.

## Request and response rules

- Authenticate with a platform API key, a service-client secret plus X-Client-Id,
  or an OIDC bearer token when the server has OIDC enabled.
- X-Tenant-Id is a UUID. X-Workspace-Id is required for workspace-owned resources
  and workspace-bound credentials. A body tenantId must match the header.
- SDK evaluate requests use subject/action/resource/attributes. Resource is
  type/id; the subject must be registered. AI_AGENT subjects supply delegated
  task_id and tool_id. Structured principal/action/resource/context remains
  supported, and runtime checks specifically require that structured shape.
- A structured decision retains requestId. An SDK-shaped decision also includes
  a durable id and traceId for contracts. Response models preserve additive
  fields. Unknown enum states do not authorize execution.
- Optional diagnostic error/message fields vary between existing server handlers.
  SDK exception classes are selected by HTTP status, not text matching. The
  wire error field is exposed as code when present; it is not promised to be a
  uniform machine-readable identifier across all existing endpoints.
- Rate-limit failures may be plain text. Request IDs and Retry-After are optional
  headers. Missing correlation metadata stays absent rather than fabricated.
- Java timestamps can have up to nine fractional digits. Python datetime models
  retain microsecond precision; their wire representation retains the original
  timestamp and timezone. Naive/malformed timestamps are rejected.

## State meanings

| State or result | Meaning |
|---|---|
| ALLOW | Authorization permits the evaluated request; no claim of external execution |
| STEP_UP | Existing approval gate applies |
| DENY | Stop without creating an execution contract |
| PENDING_APPROVAL | Contract awaits approval; its stored status may be a snapshot |
| SUCCEEDED execution | Connector returned an execution result; verification is still separate |
| FAILED execution | Connector invocation failed; external reconciliation may be needed |
| UNSUPPORTED execution | No external action connector is installed |
| verified=true | Connector verified a real successful external execution |
| NOT_APPLIED / applied=false | Kubernetes intent registered without applying it |
| PENDING_VERIFICATION / trusted=false | Federation metadata registered without establishing trust |
| HTTP 501 | Required server verifier/feature is unavailable |

The server emits STEP_UP, not REQUIRE_APPROVAL. The Python SDK accepts the old
REQUIRE_APPROVAL spelling for decoding compatibility but does not advertise it
as a new server response. A completed SDK workflow is successful only when
execution is SUCCEEDED and its matching verification is true.

## Retry policy

The OpenAPI operation extension x-sdk-retry-policy is normative for automatic
SDK retries:

| Operations | Policy |
|---|---|
| GET / HEAD | Safe to retry bounded network/transient failures |
| POST /v1/actions/evaluate with Idempotency-Key | Same key, same request snapshot across attempts |
| All other POST / PUT / PATCH / DELETE | No automatic replay, regardless of supplied key |

Retryable statuses are 429, 502, 503 and 504. 400/401/403/404/409/422/500/501 are
not automatically retried. Connect/read timeouts and connection failures are
retried only for permitted operations. TLS verification failures, redirects,
invalid successful responses and unknown states are not retryable.

Use one idempotency key for one logical evaluation. The current evaluator
namespaces its key by tenant/workspace. Runtime checks accept an idempotency key
but have different server behavior and are not included in automatic replay.
No general idempotency support is implied by a header on another endpoint.

Retry-After seconds or HTTP-date values must not be shortened. A delay above the
client's configured maximum returns the error to the application. Connection
and read inactivity timeouts are per attempt, not a total workflow deadline.
An uncertain write must be reconciled; a timeout is not proof that nothing was
persisted or executed. External connectors remain responsible for external
idempotency and crash/network reconciliation.

## Approval and execution lifecycle

Prepare evaluates and records the original decision, evidence, approval and
contract. It never executes an external action. Approval belongs to the original
decision requestId. A pending approval returns control to the application.

After an authorized actor approves, resume the saved contract ID. Do not create
a new decision/contract while passing an old approval. The server validates
scope, expiry, evidence integrity, matching approval and current policy/risk.
Contracts currently expire after 15 minutes. SDK preparation is a sequence of
requests, not one distributed transaction; preserve each returned resource ID
and reconcile uncertain writes instead of restarting the sequence blindly.
Python workflow exceptions preserve their HTTP/network classification and
include an IDs-only workflow checkpoint for resources whose responses were
received. An unknown ID after a lost response does not imply the write failed.

## Event replay

GET /v1/observability/events returns a JSON array, newest first, with limit 1–500
and optional traceId. It provides neither SSE nor cursor pagination. The old
Python EventStream name is a JSON replay compatibility wrapper. Query values
must be encoded by the HTTP library. Returned tenant/workspace scope must match
the client; tenant-wide events may have workspaceId=null.

## Compatibility and release policy

- Additive fields/methods use a minor SDK release; patch releases correct behavior
  within the published contract. Incompatible public API/model changes require
  a major SDK release and migration notes.
- Python 2.1 retains dictionary-returning methods, positional configuration and
  exported facades. *_typed methods expose named models and py.typed marks the
  package for type-checking tools.
- Invalid legacy tenant/resource strings, unsafe write retries, unknown states
  and incompatible SSE parsing are deliberately tightened and documented.
- Python 3.10+ is targeted. This revision is synchronous; one client per thread.
  Caller-owned sessions remain caller-owned. SDK-owned sessions must be closed.
- Package publishing and production readiness require packaging, supported-Python
  tests and real-server contract/workflow checks. Those checks have not been
  executed during this implementation. No package has been published.
