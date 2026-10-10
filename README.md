# Zero Trust Security Platform

**English** · [한국어](README.ko.md)

Policy-based authorization and governed tool execution for AI agents and MCP applications.

The platform evaluates who can perform an action, on which resource, and under what conditions. It can require independent approval before forwarding a registered MCP tool call, recheck current policy before execution, and retain linked decision and execution records.

**Current baseline:** 4.75.0 Revision 2. This repository contains an evolving implementation and a local development stack. Production readiness and security certification are not established. TEE remote attestation and the SaaS/customer-hosted deployment split are planned capabilities.

## Contents

- [Capabilities](#capabilities)
- [Current architecture](#current-architecture)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [MCP tool execution](#mcp-tool-execution)
- [Python SDK](#python-sdk)
- [Implementation boundaries](#implementation-boundaries)
- [Target architecture and roadmap](#target-architecture-and-roadmap)
- [Repository structure](#repository-structure)
- [Development](#development)
- [Documentation](#documentation)

## Capabilities

| Area | Current implementation |
| --- | --- |
| Authorization | Policy DSL evaluation with agent boundaries, behavior and risk checks |
| Identity and scope | API key/service-client or configured OIDC authentication, with tenant and workspace controls |
| Human approval | Approval queues, request-bound MCP approvals and policy reevaluation before resumed execution |
| MCP execution | Calls to administrator-configured upstream tools, argument constraints and result filtering |
| Execution records | Persistent decisions, approvals, contracts, MCP invocation states and lifecycle events |
| Policy distribution | Versioned bundles with optional Ed25519 signing and verification |
| Rust evaluation | A conservative fast path for supported policies, freshness checks and caller-managed full-evaluation fallback |
| Developer integration | Python, TypeScript, Java and Go SDK source, shared contracts and examples |
| Administration | Dashboard for policy, approval, agent and governance workflows |

## Current architecture

The dashboard and SDKs use one Spring Boot backend: `apps/authorization-api`. The Rust policy data plane is a separate service for supported policy evaluation.

![Current architecture: Java handles full authorization and MCP execution; Rust provides policy-only evaluation with no real attestation verifier.](docs/images/architecture-current.png)

[Open the current architecture diagram at full resolution](docs/images/architecture-current.png).

MCP execution currently uses the Java evaluation pipeline. It does not pass through the Rust fast path. A Rust `ALLOW` is a policy-only result, not proof that the full governance pipeline has authorized an external action.

The dashboard's nginx forwards `/api/` requests to the Java API. PostgreSQL stores application and governance records; Redis supports backend caching. The local Compose stack also includes Keycloak, an OpenTelemetry collector, two Rust replicas behind nginx, and an Envoy service-mesh configuration.

## Quick start

### Requirements

Use Docker with Docker Compose and Git for the container-based setup. Host installations of language toolchains are needed only for their respective development workflows.

### Start the local stack

```bash
git clone https://github.com/mcp-tee-zt-security/zt-security-platform.git
cd zt-security-platform
docker compose -f docker/docker-compose.yml up -d --build
```

Run subsequent commands from the repository root.

| Service | Local URL | Purpose |
| --- | --- | --- |
| Dashboard | http://localhost:3000 | Administration and workflow UI |
| Authorization API | http://localhost:8080 | Full evaluation, governance and MCP API |
| API reference | http://localhost:8080/swagger-ui.html | Generated API documentation |
| Rust data plane | http://localhost:8091 | Policy fast path through nginx |
| Keycloak | http://localhost:8089 | Local identity-provider service |

The default Compose stack uses API key `dev-master-key`, tenant `11111111-1111-1111-1111-111111111111`, and workspace `88888888-8888-8888-8888-888888888801`. OIDC is disabled by default. These are development settings, not deployment credentials.

### Inspect and stop

```bash
docker compose -f docker/docker-compose.yml ps
docker compose -f docker/docker-compose.yml logs -f authorization-api
curl http://localhost:8080/v1/health
curl http://localhost:8091/ready
docker compose -f docker/docker-compose.yml down
```

`/ready` returns HTTP 503 with a reason when the Rust service lacks a usable policy bundle or another readiness requirement is unmet. Stopping with `down` preserves named volumes. Adding `-v`, or using `make reset`, deletes the local database, cache and audit archive volumes.

## Configuration

[`.env.example`](.env.example) consolidates base settings, policy signing, data-plane resilience and attestation state. Copy it to `.env` when you need overrides, then pass the file explicitly:

```bash
docker compose --env-file .env -f docker/docker-compose.yml up -d --build
```

Compose passes only variables declared in each service's `environment` section. Several development values, including the API key and database settings, are fixed in the Compose file. Editing `.env` alone does not override those values. Attestation settings currently need to be supplied directly to the Rust process or through an explicit Compose override.

| Setting | Purpose |
| --- | --- |
| `ZT_API_KEY` | Backend credential, distinct from the data plane's incoming key |
| `ZT_OIDC_ENABLED`, `ZT_OIDC_ISSUER_URI` | Backend OIDC authentication |
| `ZT_POLICY_SIGNING_PRIVATE_KEY_B64` | Java policy-bundle signing key |
| `ZT_POLICY_PUBLIC_KEY_B64` | Rust policy-bundle verification key |
| `ZT_REQUIRE_SIGNED_BUNDLE` | Require signed bundles in Rust |
| `ZT_FAIL_MODE` | `FAIL_CLOSED` by default; optional degraded handling for availability failures |
| `ZT_DP_API_KEY` | Authenticate incoming data-plane requests when configured |
| `ZT_MCP_AUDIENCE` | Required JWT audience for MCP requests |
| `ZT_MCP_ALLOWED_ORIGINS` | Exact allowlist for browser-origin MCP requests |

Policy signing is optional in the development stack. Deployment configurations should supply the Java signing key and corresponding Rust verification key and require signed bundles. The [signing-key generator](scripts/linux/generate-policy-signing-key.py) requires Python's `cryptography` package.

See the [Rust configuration reference](apps/policy-data-plane/README.md) and [MCP setup guide](docs/api/MCP_GATEWAY_EXECUTION.md) for supported settings and defaults.

## MCP tool execution

The gateway executes registered tools after authenticating the caller, validating arguments, and applying the full Java policy pipeline.

### Setup

1. Configure an upstream MCP endpoint and its dedicated credential on the server.
2. In **MCP Gateway > Upstream servers**, register the connection and load its tool definitions. Use **Tool registration** to create or select a tool and save its workspace-scoped binding. The existing binding API remains available.
3. Define allowed caller subjects and argument constraints, and publish the required authorization policy.
4. Call `POST /v1/mcp/json-rpc` with an authenticated identity and matching tenant/workspace headers.

Upstream endpoints and bindings are not configured automatically. Dashboard-managed registrations persist in PostgreSQL with tenant/workspace isolation. Local HTTP requires explicit opt-in; other registration hosts and credential environment references require deployment allowlists. The [dashboard registration guide](docs/api/MCP_DASHBOARD_REGISTRATION.md) covers the order-server example, MCP approvals and call history. The [example Compose override](docker/docker-compose.mcp.example.yml) remains available for deployment-managed connections.

### Decision and execution behavior

| Result | Behavior |
| --- | --- |
| `DENY` | No upstream tool execution |
| `ALLOW` | Execute after gateway checks, unless the binding requires approval |
| `STEP_UP` or required approval | Persist the original call and return `PENDING_APPROVAL` |
| Approved call | Original caller resumes through `POST /v1/mcp/calls/{callId}/resume`; the server rechecks the binding and current policy |
| Uncertain outcome | Report `UNKNOWN` and do not automatically retry the business action |

Approvals bind the original caller, tool and arguments. Requesters cannot approve their own MCP calls. Each new operation needs a new JSON-RPC ID. Repeating an ID for the same scoped caller returns the existing call without another dispatch. This prevents duplicate gateway dispatch and does not guarantee exactly-once execution by the upstream system.

The upstream adapter supports Streamable HTTP tool calls, JSON responses and bounded, completed POST SSE responses. Result handling supports text content and object `structuredContent`. The inbound endpoint is an authenticated stateless JSON-RPC adapter. It is not a complete MCP OAuth discovery server and does not support every MCP transport or capability.

See [MCP Gateway execution](docs/api/MCP_GATEWAY_EXECUTION.md) for authentication, binding examples, approval handling, transport limits and recovery behavior.

## Python SDK

Install from this repository:

```bash
python -m pip install ./sdk/python
```

Python 3.10+ is required. The SDK is synchronous and supports typed models, structured errors, scoped authentication, governance workflows and lifecycle-event replay. Its package version is independent of the server baseline.

```python
import os
from zt_security import ActionContext, ZtSecurityClient

with ZtSecurityClient(
    "http://localhost:8080",
    api_key=os.environ["ZT_API_KEY"],
    tenant_id="11111111-1111-1111-1111-111111111111",
    workspace_id="88888888-8888-8888-8888-888888888801",
) as client:
    request = ActionContext(
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
    decision = client.evaluate_typed(request)
    print(decision.decision, decision.reason)
```

Set `ZT_API_KEY` in the Python process environment to a credential accepted by the server. A Compose `.env` file does not automatically configure a separate Python process. The example requires a registered identity, active task/tool delegation, matching scope and applicable policy. Evaluation alone does not execute a payment.

The general SDK governance lifecycle creates linked evidence, approvals and execution contracts. External execution requires an installed `GovernedActionExecutor` connector; none is supplied by default. This is separate from the configured MCP upstream execution path.

See the [Python SDK guide](sdk/python/README.md), [shared contract](sdk/contracts/sdk-api.yaml), [compatibility rules](sdk/contracts/SDK_CONTRACT.md) and [examples](sdk/examples/).

## Implementation boundaries

| Area | Current boundary |
| --- | --- |
| TEE remote attestation | No real verifier or automated TEE deployment. `/v1/attestation/verify` returns HTTP 501 |
| Rust attestation state | A document hash is not verification. Every mode except `DISABLED` currently blocks readiness |
| Workload identity | Forwarded certificate headers are reported as unverified claims |
| Audit integrity | Stored hashes, lifecycle records and optional Rust evidence signatures do not constitute TEE-sealed or tamper-proof audit storage |
| General SDK execution | Requires an external connector; absent connectors return `UNSUPPORTED` |
| Capability verification | `/v1/capabilities/check` returns HTTP 501 until a verifier is installed |
| Result filtering | Exact literal redaction and JSON Pointer removal, without semantic DLP guarantees |
| Federation and Kubernetes | Registration persists intent; it does not establish verified trust or apply policies to a cluster |
| SaaS deployment | No completed SaaS Control Plane/customer-hosted Data Plane separation |

MCP arguments are stored to support exact approval and resume behavior, and the full evaluation audit path may also retain context. Set retention and access policies before sending sensitive business data. Upstream credentials are separate from caller credentials; the gateway does not forward the caller's API key or bearer token.

## Target architecture and roadmap

The target deployment separates a centrally managed **Control Plane** from a **Data Plane installed in the customer's VPC or on-premises environment**. The Control Plane manages identity integration, policy distribution, approvals and audit visibility. The customer Data Plane evaluates policy and controls tool execution close to business systems, with configurable sharing of approval and audit metadata.

This is the planned architecture. The current Java service combines management, full evaluation and MCP execution.

![Planned target architecture: a managed Control Plane and customer-local Data Plane, with initial Rust TEE attestation and later protected MCP execution. Not yet implemented.](docs/images/architecture-target.png)

[Open the target architecture diagram at full resolution](docs/images/architecture-target.png).

| Workstream | Planned outcome |
| --- | --- |
| TEE remote attestation | Initial Nitro support with workload enrollment, one-time challenges, cryptographic verification, enclave-generated keys, automated renewal and Python SDK integration |
| Context-aware tool authorization | Broader session and tool controls, with a shared policy contract before optional OPA/Cedar integration |
| Cryptographic audit protection | Signed complete event payloads, key lifecycle controls and externally verifiable retention |
| Hybrid deployment | Signed policy distribution, customer-local enforcement and explicit handling of connectivity loss |

The first TEE milestone protects the Rust policy engine. Extending protection to MCP execution requires an attested execution component, credentials inside the protected boundary and enforcement against bypass. Attesting Rust alone does not protect the Java gateway.

## Repository structure

| Path | Responsibility |
| --- | --- |
| `apps/authorization-api/` | Spring Boot backend, authorization, governance and MCP execution |
| `apps/policy-data-plane/` | Rust evaluation, bundle validation and evidence |
| `dashboard/web/` | Administration UI and nginx API proxy |
| `sdk/` | Language SDKs, common contracts, examples and CLI |
| `docker/` | Local Compose stack and supporting configuration |
| `infra/` | Infrastructure code and data-plane proxy configuration |
| `k8s/` | Deployment manifests and operator resources |
| `ops/` | Operational configurations and dashboards |
| `scripts/` | Windows/Linux operations, database and quality tools |
| `benchmarks/` | Performance test assets |
| `docs/` | Architecture, API, security, development and operations documentation |

The root `pom.xml` is the parent and aggregator for the Java backend. The root `package.json` and `Makefile` provide development shortcuts. Environment examples are consolidated in `.env.example`. Historical build notes are under `docs/history/`.

## Development

Use JDK 17 and Maven 3.9+ for the backend, Python 3.10+ for the Python SDK, and the toolchains declared by each remaining component. The following commands are provided for developers. Their presence is not evidence that the current checkout has passed them.

```bash
# Java backend
mvn -pl apps/authorization-api -am test

# Python SDK
python -m pip install -e "./sdk/python[dev]"
python -m pytest sdk/python/tests

# Dashboard
npm --prefix dashboard/web install
npm run dashboard:build

# Rust data plane
cargo test --manifest-path apps/policy-data-plane/Cargo.toml
```

Additional workflows are documented in the [developer guide](docs/development/4.75_DEVELOPER_GUIDE.md). The aggregate quality script is `scripts/quality/check.sh`; it includes Go formatting and can modify Go source files.

Development emphasizes stabilization, readable code and documentation. Add capabilities through an explicit scope decision. Preserve installed Flyway migrations and add schema changes as new migrations. Keep SDK contracts aligned with server behavior and distinguish implemented features from deployment plans.

## Documentation

- [Documentation index](docs/INDEX.md)
- [Platform architecture](docs/architecture/4.75_ARCHITECTURE.md)
- [Security model](docs/security/4.75_SECURITY_MODEL.md)
- [Backend API and governance](docs/api/4.75_BACKEND_API.md)
- [MCP Gateway execution](docs/api/MCP_GATEWAY_EXECUTION.md)
- [MCP dashboard registration](docs/api/MCP_DASHBOARD_REGISTRATION.md)
- [Rust data plane](apps/policy-data-plane/README.md)
- [Python SDK](sdk/python/README.md)
- [Operations](docs/operations/4.75_OPERATIONS.md)
- [Observability](docs/operations/4.75_OBSERVABILITY.md)
- [Historical build and startup notes](docs/history/BUILD_FIX_4.75.0.md)

Repository-wide distribution license terms are not currently specified. No public SDK package publication or production certification is asserted by this README.

## Stitch AI access PoC

A separate synthetic demo provides server-authorized document reads, folder inheritance, channel messages and permission-filtered search. Open **Agents & Connections → Stitch Access PoC** in the dashboard. Initialize sample data explicitly as an administrator; authenticate a registered service client to demonstrate AI restrictions.

The local Compose deployment enables `ZT_STITCH_POC_ENABLED` by default. Disable it outside demo deployments. This PoC does not integrate actual Stitch data, end-user ACLs, LLM retrieval, external MCP, vector indexes or TEE. See the [demo guide](docs/api/STITCH_ACCESS_POC.ko.md) and [Postman collection](docs/postman/Stitch-Access-PoC.postman_collection.json).

## Stitch integration contract

The separate [standard integration contract](docs/api/STITCH_INTEGRATION_V1.md) adds OIDC human/AI delegation, live source ACL and membership sync, file/attachment retrieval, PostgreSQL RAG indexes, permission-aware ID caching and recipient deletion workflows. It includes SDK/connector code and unapplied network/DB deployment templates. It is disabled by default; actual Stitch endpoints, provider storage and deployment controls still require customer configuration. See the [local OIDC walkthrough](docs/api/STITCH_INTEGRATION_LOCAL.ko.md).

### Virtual Stitch and Stitchy simulation

An optional local simulator connects a synthetic Teams-style content/ACL server and a Stitchy MCP client to the standard ZT contract. Its UI compares human and AI payroll access, filtered retrieval, permission changes, session revocation, temporary storage and recipient erasure acknowledgments. Answers quote freshly authorized tool results; no external Stitch service or LLM is required.

Follow the [local simulation guide](docs/demo/STITCH_SIMULATION.ko.md) using the `docker/stitch-simulation.compose.yml` overlay, then open **http://localhost:8766**, also linked from **Stitch Integration** in the dashboard. The runner uses an isolated Docker internal network and a read-only gateway, with no source/DB credentials. This is local simulation code; builds, runtime behavior and container isolation have not been validated.
