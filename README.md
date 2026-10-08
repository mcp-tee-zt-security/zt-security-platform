# Zero Trust Security Platform 4.75.0 — Revision 2

> **Maintenance mode:** 4.75.0 R2 is the current baseline. From this point, the project is in **cleanup, stabilization, readability, test, and documentation mode**. New platform capabilities should not be added unless explicitly requested.

## 1. What this repository is

This repository contains the Zero Trust / Cyber Defense platform with the 4.75 SDK governance lifecycle integrated into apps/authorization-api.

The 4.x governance model is:

```text
Identity
  → Attestation
  → Capability
  → Policy Decision
  → Evidence
  → Approval
  → Execution Contract
  → Execution
  → Verification
  → Audit / Observability
```

The existing application and infrastructure remain. apps/authorization-api is the single executable backend for the dashboard and SDK. The independent in-memory reference backend has been retired.

## 2. One executable backend

`docker/docker-compose.yml` starts `apps/authorization-api` on port 8080, the dashboard on port 3000, PostgreSQL, Redis, and the existing data-plane/infrastructure services.

Dashboard and SDK requests share the existing policy engine, authentication, approvals and audit pipeline. SDK lifecycle records are persisted in PostgreSQL and emitted through the existing event outbox. There is no second backend process to start.

The `backend/` directory contains only a migration notice. See `docs/api/4.75_BACKEND_API.md` for request compatibility and unsupported external integrations.
# 3. Step-by-step: run the application

## Prerequisites

Recommended local tools:

- Docker + Docker Compose
- JDK 17 for the integrated authorization API
- Maven 3.9+
- Python 3.10+
- Node.js 20+
- npm
- Go 1.23+
- Git

You do **not** need every tool to run the Docker demo, but they are needed for the full repository quality checks.

## Step 1 — enter the project

```bash
cd zt40
```

## Step 2 — inspect configuration

Copy the example environment file if local overrides are required:

```bash
cp .env.example .env
```

Do not commit real credentials or production secrets.

## Step 3 — start the existing integration stack

```bash
docker compose -f docker/docker-compose.yml up --build -d
```

Or:

```bash
make start
```

Check the containers:

```bash
docker compose -f docker/docker-compose.yml ps
```

## Step 4 — check the control-plane health endpoint

```bash
curl http://localhost:8080/v1/health
```

The `authorization-api` service is the application currently exposed on port `8080` by Docker Compose.

## Step 5 — check the policy data plane

```bash
curl http://localhost:8091/ready
```

## Step 6 — open the dashboard

Open:

```text
http://localhost:3000
```

The dashboard is the existing web application under `dashboard/web/`.

## Step 7 — inspect service logs

```bash
docker compose -f docker/docker-compose.yml logs -f authorization-api
```

For all services:

```bash
docker compose -f docker/docker-compose.yml logs -f
```

## Step 8 — stop the environment

```bash
make stop
```

To remove the development database volume as well:

```bash
make reset
```

`make reset` is destructive to local Docker data.

---

# 4. SDK governance on the integrated backend

Use the same authorization-api at http://localhost:8080. SDK requests require a valid API key and UUID tenant header; include the workspace header for workspace policies.

The API accepts the dashboard's structured principal/action/resource request and the SDK's subject/action/resource/attributes request. SDK resources use `type/id`; SDK subjects must be registered identities. AI-agent SDK requests require `attributes.task_id` and `attributes.tool_id`.

Decisions use ALLOW, STEP_UP and DENY. STEP_UP uses the existing approval queue. A pending approval stops the high-level SDK workflow; approve through the existing endpoint and explicitly execute the saved contract afterward.

Evidence, contracts, execution attempts, verification and lifecycle events persist in PostgreSQL. No external executor is installed by default: an authorized execution attempt returns UNSUPPORTED, and verification returns false. Attestation/capability verification returns HTTP 501 until a real verifier is configured. Federation trust registration remains pending, and Kubernetes policy registration does not apply anything to a cluster.

For endpoint details and examples see `docs/api/4.75_BACKEND_API.md`.
# 5. Step-by-step: run tests and quality checks

## Repository quality baseline

From the repository root:

```bash
bash scripts/quality/check.sh
```

The script currently checks Python compilation, Python line length, TypeScript, Go formatting/tests, Java lexical safety, and Java/TypeScript line-length reports.

## Python SDK

```bash
python -m compileall -q sdk/python
python -m unittest discover -s sdk/python/tests
```

If Ruff and Black are installed:

```bash
ruff check sdk/python
black --check sdk/python
```

## TypeScript SDK

```bash
cd sdk/typescript
npm install
npm test
```

Then return to the repository root:

```bash
cd ../..
```

## Go SDK

```bash
cd sdk/go
gofmt -d ztsecurity
go test ./...
cd ../..
```

## Java SDK

```bash
cd sdk/java/zt-security-sdk
mvn test
cd ../../..
```

## Integrated authorization API

```bash
mvn -pl apps/authorization-api -am test
```

The integration package is part of the root Maven build. There is no separate backend Maven project.
## Dashboard

```bash
cd dashboard/web
npm install
npm run build
cd ../..
```

## Docker smoke test

After the Docker environment is running:

```bash
bash scripts/linux/observability-preflight.sh
```

and use the existing smoke-test script where supported by the local PowerShell environment:

```powershell
./scripts/windows/smoke-test.ps1
```

---

# 6. Directory guide

| Directory | What it contains | Current status |
|---|---|---|
| `backend/` | Migration notice; independent application retired | Retired |
| `apps/` | Existing authorization/control-plane application and integrations | Active |
| `sdk/` | Python, TypeScript, Java and Go SDKs, contracts, examples and CLI | Active |
| `dashboard/` | Web security dashboard | Active |
| `docs/policy/` | Policy DSL grammar reference and examples; runtime policies are stored in the database | Reference |
| `k8s/` | Kubernetes deployment manifests and policy draft synchronization operator | Active |
| `infra/` | Current infrastructure, including CDK and Envoy; unused historical snapshots removed | Active |
| `docker/` | Local/demo Compose stack and supporting configuration | Active |
| `observability/` | Current observability assets/contracts | Active |
| `ops/observability/` | Prometheus/Grafana operational deployment configuration and dashboards | Active |
| `docs/` | Current architecture, API, security, operations, SDK and platform documentation | Active |
| `scripts/` | Canonical operational and smoke-test scripts | Active |
| `scripts/quality/` | Static quality, formatting and audit tooling | Active |
| `runbooks/` | Operational procedures; keep only procedures still used | Review |
| `benchmarks/` | Performance test assets | Review |
| `scripts/windows/chaos/` | Windows chaos/resilience test scripts | Review |
| `ops/private-llm/` | Private LLM configuration examples | Active |

Historical `validation/` suites and unused versioned Kubernetes/infrastructure snapshots have been removed from the active development tree. Duplicate demo scripts have also been removed; only the canonical `start-demo.ps1`, `stop-demo.ps1` and `reset-demo.ps1` remain.

# 7. Cleanup decisions already applied

The repository is now in cleanup mode. The following changes have already been applied rather than left as recommendations:

1. Removed the historical `validation/` tree.
2. Removed unused `k8s/3.2`, `k8s/3.3`, `k8s/3.4` and `k8s/3.8` trees.
3. Removed unused legacy infrastructure snapshots under `infra/attestation/3.5`, `infra/monitoring/3.7`, `infra/monitoring/3.8`, `infra/k8s/3.5` and `infra/dr/3.8`.
4. Removed duplicate `scripts/scripts-*-demo.ps1` files.
5. Removed root/dashboard package `version` metadata because the project is not treating those values as release identifiers during active development. Dependency/library versions remain where package managers require them.
6. Retained `infra/envoy/3.3/envoy.yaml` because `docker/docker-compose.yml` references it directly.
7. Reorganized Kubernetes deployment manifests under `k8s/deploy/`; operator resources remain under `k8s/operator/`.

No new platform capability is introduced by these changes.

# 8. Maintenance rules

- Do not add new product features during cleanup mode.
- Prefer deleting duplicate or unreachable artifacts over creating compatibility copies.
- Before deleting a directory, search the repository for active references.
- Keep runtime behavior unchanged while formatting or reorganizing code.
- Keep one canonical script for each operational action.
- Keep `docs/` synchronized with the actual tree.
- Do not create release notes or release-validation documents during this development phase.
- Do not introduce version directories unless they represent an actively deployed configuration.

# 9. Documentation map

- `docs/INDEX.md` — documentation entry point
- `docs/architecture/4.75_ARCHITECTURE.md` — platform architecture
- `docs/api/4.75_BACKEND_API.md` — backend API
- `docs/security/4.75_SECURITY_MODEL.md` — security and governance model
- `docs/operations/4.75_OPERATIONS.md` — operations
- `docs/operations/4.75_OBSERVABILITY.md` — observability
- `docs/development/4.75_DEVELOPER_GUIDE.md` — development workflow
- `docs/development/4.75_SDK.md` — SDK usage
- `docs/platform/4.75_KUBERNETES.md` — Kubernetes integration
- `docs/platform/4.75_FEDERATION.md` — federation
- `docs/platform/4.75_SIMULATION.md` — simulation
- `docs/migration/3.X_TO_4.0_MIGRATION.md` — legacy migration context

## Maintenance / Cleanup Policy

This repository is currently in maintenance and cleanup mode. No new platform features are being added.

- Keep the current runtime paths and active deployment configuration.
- Remove obsolete versioned artifacts when they have no active references.
- Do not keep duplicate helper scripts.
- Keep `infra/envoy/3.3` because the current Docker Compose stack references it directly.
- Historical validation suites and unused legacy Kubernetes/infra snapshots are not part of the active development tree.
- Project version numbers are not treated as release identifiers during this development phase.

## Directory Status

| Directory | Status | Purpose |
|---|---|---|
| `backend/` | Retired | Migration notice; executable code moved to authorization-api |
| `apps/` | Active | Existing authorization/control-plane application |
| `sdk/` | Active | Python, TypeScript, Java and Go SDKs |
| `dashboard/` | Active | Web UI |
| `docs/policy/` | Reference | Policy DSL grammar reference and examples; runtime policies are stored in the database |
| `k8s/` | Active | Kubernetes deployment manifests and policy draft synchronization operator |
| `infra/` | Active | Current infrastructure; unused legacy snapshots removed |
| `docker/` | Active | Local/demo container stack |
| `observability/` | Active | Current observability contracts/assets |
| `ops/observability/` | Active | Prometheus/Grafana operational configuration |
| `docs/` | Active | Current project documentation |
| `scripts/` | Active | Canonical operational scripts; duplicates removed |
| `scripts/quality/` | Active | Formatting/lint/static quality checks |
| `runbooks/` | Review | Operational procedures; remove obsolete entries as discovered |
| `benchmarks/` | Review | Performance artifacts; retain only if still used |
| `scripts/windows/chaos/` | Review | Chaos test scripts; retain only if still used |
| `ops/private-llm/` | Active | Private LLM configuration examples |



## Scripts

OS-specific operational scripts are separated to keep execution paths explicit:

- Windows: `scripts/windows/` (`.ps1`)
- Linux: `scripts/linux/` (`.sh`)
- The policy signing-key generator has a single Python implementation at `scripts/linux/generate-policy-signing-key.py`; Windows invokes that implementation through PowerShell.
