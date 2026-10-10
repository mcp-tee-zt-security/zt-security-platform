# ZT Security Python SDK

Python SDK 2.1.0 targets the integrated 4.75 API in `apps/authorization-api`.
Python 3.10+ is required. This SDK is synchronous; use one client per thread.
The SDK package version is independent of the server version.

## Install from this repository

```bash
python -m pip install ./sdk/python
```

No package has been published as part of this work. The package name is the
repository's existing `zt-security-sdk`; availability on a public registry has
not been established. Distribution license terms are not specified in this
repository and are not invented by the package metadata.

## Evaluate with typed models

```python
import os
from zt_security import ActionContext, Decision, ZtSecurityClient

with ZtSecurityClient(
    "http://localhost:8080",
    api_key=os.environ["ZT_API_KEY"],
    tenant_id="11111111-1111-1111-1111-111111111111",
    workspace_id="88888888-8888-8888-8888-888888888801",
    timeout=(3, 10),  # connection and socket-read timeouts, not a total deadline
) as client:
    context = ActionContext(
        subject="payment-agent",
        action="payment.transfer",
        resource="bank_account/ACC-1001",
        tenant_id=client.tenant_id,
        attributes={
            "amount": 100000,
            "task_id": "payment-demo-task",
            "tool_id": "33333333-3333-3333-3333-333333333301",
        },
    )
    decision = client.evaluate_typed(context)
    print(decision.decision, decision.reason, decision.risk)
    if decision.decision == Decision.DENY:
        print("The authoritative server rejected this action.")
```

Existing methods continue returning dictionaries, for example
`client.evaluate(context)["decision"]`. Structured dictionary requests and
`EvaluationRequest(Principal(...), Action(...), Resource(...), context=...)`
remain supported. Use the `*_typed` methods for named response fields.
Models preserve additive server fields through `to_dict()`. Unknown decision
or execution states and invalid response shapes raise `ZtSecurityProtocolError`.

Client tenant/workspace IDs and resource IDs must be UUIDs where required by
the server. SDK resources use `type/id`. Subjects must be registered identities;
AI-agent requests require delegated task/tool identifiers in attributes.

## Authentication

Choose exactly one authentication mode:

- API key: `api_key="..."`. For a service-client secret also supply `client_id="..."`.
- OIDC bearer: `bearer_token="..."`, or a callable returning the current token.
  The callable runs before every attempt. Token acquisition/refresh is managed
  by the application. The server must have OIDC enabled.

Service-client roles do not automatically grant execution or approval rights.
The server remains authoritative. A workspace-bound credential must use its
matching workspace header. Authentication and scope headers cannot be
overridden through per-request headers. Redirects are refused rather than
forwarding credentials to another endpoint. Errors redact configured API keys
and the bearer token used for the failing attempt.

Use HTTPS for remote deployments. An explicitly injected `requests.Session`
is caller-owned: closing the SDK does not close that session. An SDK-created
session is closed by `close()` or the context manager. A closed client cannot
be reused. Requests uses configured/environment proxies; explicit SDK header
authentication takes precedence over session authentication and `.netrc`.

## Governance: prepare, approve, resume

Run the following inside an open client context, or create a new client first.

```python
prepared = client.governance.prepare(context)
if prepared.pending_approval:
    approval_id = prepared.approval["id"]
    contract_id = prepared.contract["id"]
    # Persist contract_id and complete approval using an authorized approver.
    # approver_client.approve(approval_id)
    # Later, using an authorized execution client:
    # result = execution_client.governance.resume(contract_id)
```

`prepare()` evaluates and creates evidence/approval/contract records but never
executes an external action. `execute()` adds execution for an allowed or already
approved request; a pending approval returns immediately. `resume(contract_id)`
loads and executes the existing contract without a new evaluation request or
approval request from the SDK. The server still rechecks current authorization,
expiry, evidence, and approval before execution. A still-pending approval raises
a permission/state error; `resume()` does not wait or approve automatically.

Do not pass an approval for an older decision into a new high-level execution.
Resume the corresponding saved contract. Contracts currently expire after
15 minutes. `result.succeeded` requires both a successful execution and positive
connector verification. Absent execution connectors return `UNSUPPORTED` and
are never reported as successful. Failed/unsupported executions are not sent
to the verification endpoint by the high-level facade.

## Retry and uncertain writes

Automatic retries are limited to GET/HEAD and POST `/v1/actions/evaluate` with
an idempotency key. `evaluate()` generates one key per call and reuses that key
and the request snapshot across attempts; supply `idempotency_key=` to reconcile
an uncertain evaluation across separate calls. Use a new key for each logical
operation. The integrated evaluator scopes its key by tenant/workspace.

No other POST, PUT, PATCH or DELETE is automatically replayed, even if an
Idempotency-Key header is supplied. This includes evidence/contract creation,
approval, runtime checks and execution. Some of these endpoints have additional
server-side safeguards, but the SDK does not assume distributed exactly-once
execution or general replay safety.

Retryable HTTP statuses are 429/502/503/504. Read/connect timeouts and connection
failures are retried only for the allowed operations. Authentication, permission,
conflict, invalid responses, redirects, TLS failures and unsupported-feature
errors are not retried. Retries use exponential backoff with jitter. Retry-After
seconds/HTTP-date values are honored; a delay above `max_retry_delay` is returned
to the caller as an error instead of retrying early or waiting unboundedly.

`timeout` controls connect/read inactivity per HTTP attempt, not a hard deadline
for the entire workflow. A network failure on a write can mean the server
already processed it: inspect `error.ambiguous` and `error.idempotency_key` and
reconcile persisted state before manually repeating a mutation. If contract
creation's response was lost, do not automatically create another contract.

High-level workflow failures preserve the original exception class and expose
`error.workflow` with the failed stage and known decision/evidence/approval/
contract/execution IDs. It contains no request payloads or authentication data.
These checkpoints identify resources already returned by the server; an
operation whose response was lost may still have completed without its ID being
known. Reconcile that stage instead of restarting the entire workflow.

## Errors

All public exceptions inherit `ZtSecurityError`.

| HTTP status | Exception |
|---|---|
| 400 / 422 | `ZtSecurityValidationError` |
| 401 | `ZtSecurityAuthenticationError` |
| 403 | `ZtSecurityPermissionError` |
| 404 | `ZtSecurityNotFoundError` |
| 409 | `ZtSecurityConflictError` |
| 429 | `ZtSecurityRateLimitError` |
| 501 | `ZtSecurityUnsupportedError` |
| Other 5xx | `ZtSecurityServerError` |

HTTP exceptions expose `status_code`, `message`, `code`, `body`, `request_id`,
`retry_after`, and `idempotency_key`. JSON and plain-text server failures are
supported. A missing request ID/code remains `None`. Invalid local configuration
raises `ZtSecurityConfigurationError`; invalid successful responses raise
`ZtSecurityProtocolError`. Network failures use `ZtSecurityTransportError` and
socket/connect timeouts use its subclass `ZtSecurityTimeout`.

## Events

```python
for event in client.events.replay(trace_id="request-trace-id", limit=100):
    print(event.event_id, event.event_type, event.occurred_at)
```

This is one JSON snapshot, newest first, with a maximum of 500 records. It is
not SSE, a subscription, or cursor pagination. The old `EventStream` import is
retained as a compatibility wrapper for JSON replay at
`/v1/observability/events`; arbitrary SSE paths are not supported. Query values
are encoded by the HTTP transport. Cross-tenant/workspace events are rejected.

## Scope and release status

The common contract is `sdk/contracts/sdk-api.yaml`; additional compatibility
rules are in `sdk/contracts/SDK_CONTRACT.md`. The low-level client exposes the
existing policy/runtime/agent/federation/simulation endpoints; typed response
models cover the governed lifecycle, mission and event APIs. Attestation and
capability checks currently raise HTTP-501 unsupported errors. Federation
registration does not establish trust, and Kubernetes registration does not
apply a policy to a cluster.

This implementation has not been built or tested in this session. It is not
declared production-ready. The mock transport/workflow/contract tests require
no running backend; actual server integration and packaging checks remain a
release prerequisite. These commands are documented, not executed here:

```bash
python -m pip install -e "./sdk/python[dev]"
python -m pytest sdk/python/tests
python -m build sdk/python
```

The 2.1 release preserves client positional configuration, dictionary-returning
methods, exported facades and `EventStream`. It deliberately tightens invalid
UUID/resource inputs, unsafe automatic retries, unknown response states and
the previously incompatible SSE replay behavior. Future incompatible API/model
changes require an SDK major version; additive models/methods use a minor
version. Supported server behavior is the integrated 4.75 contract, not the
retired independent reference application.

Implementation references: [Requests authentication](https://requests.readthedocs.io/en/latest/user/authentication/)
and [Python 3.10 datetime parsing](https://docs.python.org/3.10/library/datetime.html#datetime.datetime.fromisoformat).
Java timestamps with nanosecond fractions are preserved in `to_dict()`; typed
Python datetime fields retain microsecond precision.
## Stitch retrieval integration

The vendor-independent facade is now `client.retrieval` (`/v1/integrations/retrieval`). `client.stitch` remains a compatibility alias. Register the connector/workspace and publish AI retrieval policies in ZT first; source ingestion does not author AI policy. Use `client.retrieval.for_session(session_id)` for any client. See [Retrieval control plane v2](../../docs/api/RETRIEVAL_CONTROL_PLANE_V2.md) and [the client simulation walkthrough](../../docs/demo/STITCH_CLIENT_SIMULATION.ko.md).

`client.stitch` exposes the optional `/v1/integrations/stitch` contract. A real HUMAN OIDC token creates a delegation; a separately authenticated AI client uses `client.stitch.for_session(session_id)`. `read_document`, `list_children` and `search` return only ALLOW results and raise `StitchAccessDenied` on DENY. Keep the session handle in trusted application state, outside model arguments. Source sync and deletion methods require the connector role. This facade does not connect an LLM or source provider automatically.

See [the integration contract](../../docs/api/STITCH_INTEGRATION_V1.md). Local OIDC fixtures and Postman requests are available for development; no live verification was performed for this integration change.
