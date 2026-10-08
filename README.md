# Zero Trust Security Platform 4.75.0 — Revision 2

> **Maintenance mode:** 4.75.0 R2 is the current baseline. From this point, the project is in **cleanup, stabilization, readability, test, and documentation mode**. New platform capabilities should not be added unless explicitly requested.

## 1. What this repository is

This repository contains the existing Zero Trust / Cyber Defense platform together with the 4.75 R2 governance and developer-platform reference layer.

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

The original 3.x/4.0 application and infrastructure code remains in the repository. The 4.75 R2 backend and SDK are **not a replacement for every legacy service**; they provide a concrete governance/execution reference layer and common developer contracts.

## 2. Important: there are two backend paths

Do not confuse these two parts of the repository:

### A. Existing runnable platform stack

`docker/docker-compose.yml` starts the existing application stack:

```text
PostgreSQL
Redis
Keycloak
authorization-api       :8080
policy-data-plane       :8091
policy-data-plane-1
policy-data-plane-2
OTel Collector
Envoy
Dashboard               :3000
```

This is the primary **demo/integration environment** currently wired into Docker Compose.

### B. 4.75 R2 reference backend

`backend/` is a separate Spring Boot reference execution layer.

It exposes the 4.75 governance/execution APIs directly and is intentionally kept separate from the existing `authorization-api` application. It is useful for SDK/API development, unit tests, and validating the 4.75 execution model.

This separation is currently a cleanup item: future maintenance should make the relationship between the existing control-plane services and the 4.75 reference backend clearer without duplicating business logic.

---

# 3. Step-by-step: run the application

## Prerequisites

Recommended local tools:

- Docker + Docker Compose
- JDK 21 for `backend/`
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

# 4. Step-by-step: run the 4.75 R2 backend

The reference backend is separate from the Docker Compose `authorization-api` service.

## Start

```bash
cd backend
mvn spring-boot:run
```

It uses Spring Boot's default HTTP port unless overridden.

## Test governance evaluation

```bash
curl -X POST http://localhost:8080/v1/actions/evaluate \
  -H 'Content-Type: application/json' \
  -d '{
    "identity":"developer",
    "action":"read",
    "resource":"policy/example",
    "context":{"environment":"local"}
  }'
```

If the Docker `authorization-api` is already using port 8080, run the reference backend on another port, for example:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=18080"
```

Then use `http://localhost:18080` for the examples above.

## 4.1 Reference backend API groups

| Group | Purpose |
|---|---|
| `/v1/actions/evaluate` | Policy/governance decision |
| `/v1/identity/*` | Identity resolution |
| `/v1/attestation/*` | Attestation verification |
| `/v1/capabilities/*` | Capability checks |
| `/v1/evidence/*` | Evidence creation/verification |
| `/v1/approvals/*` | Approval lifecycle |
| `/v1/execution/*` | Execution contract, execution and verification |
| `/v1/governance/policies/*` | Policy lint/blast-radius/as-code operations |
| `/v1/federation/*` | Federation reference operations |
| `/v1/agents/*` | Agent and mission reference operations |
| `/v1/simulations/*` | Simulation reference operations |
| `/v1/kubernetes/*` | Kubernetes policy reference operation |
| `/v1/runtime/*` | Runtime session operations |
| `/v1/observability/*` | Metrics and security event inspection |

---

# 5. Step-by-step: run tests and quality checks

## Repository quality baseline

From the repository root:

```bash
bash quality/check.sh
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

## 4.75 reference backend

```bash
cd backend
mvn test
mvn verify
cd ..
```

`mvn verify` is the intended formatting/checkstyle quality gate for the backend.

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
| `backend/` | 4.75 reference backend execution layer, governance, observability and tests | Active |
| `apps/` | Existing authorization/control-plane application and integrations | Active |
| `sdk/` | Python, TypeScript, Java and Go SDKs, contracts, examples and CLI | Active |
| `dashboard/` | Web security dashboard | Active |
| `policy/` | Policy assets and policy configuration | Active |
| `k8s/` | Current Kubernetes manifests; `3.24` is retained because it is an active deployment tree | Active |
| `infra/` | Current infrastructure, including CDK and Envoy; unused historical snapshots removed | Active |
| `docker/` | Local/demo Compose stack and supporting configuration | Active |
| `observability/` | Current observability assets/contracts | Active |
| `ops/observability/` | Prometheus/Grafana operational deployment configuration and dashboards | Active |
| `docs/` | Current architecture, API, security, operations, SDK and platform documentation | Active |
| `scripts/` | Canonical operational and smoke-test scripts | Active |
| `quality/` | Static quality, formatting and audit tooling | Active |
| `runbooks/` | Operational procedures; keep only procedures still used | Review |
| `benchmarks/` | Performance test assets | Review |
| `chaos/` | Chaos/resilience test assets | Review |
| `config/` | Shared configuration | Active |

Historical `validation/` suites and unused versioned Kubernetes/infrastructure snapshots have been removed from the active development tree. Duplicate demo scripts have also been removed; only the canonical `start-demo.ps1`, `stop-demo.ps1` and `reset-demo.ps1` remain.

# 7. Cleanup decisions already applied

The repository is now in cleanup mode. The following changes have already been applied rather than left as recommendations:

1. Removed the historical `validation/` tree.
2. Removed unused `k8s/3.2`, `k8s/3.3`, `k8s/3.4` and `k8s/3.8` trees.
3. Removed unused legacy infrastructure snapshots under `infra/attestation/3.5`, `infra/monitoring/3.7`, `infra/monitoring/3.8`, `infra/k8s/3.5` and `infra/dr/3.8`.
4. Removed duplicate `scripts/scripts-*-demo.ps1` files.
5. Removed root/dashboard package `version` metadata because the project is not treating those values as release identifiers during active development. Dependency/library versions remain where package managers require them.
6. Retained `infra/envoy/3.3/envoy.yaml` because `docker/docker-compose.yml` references it directly.
7. Retained `k8s/3.24` because it remains an active Kubernetes deployment tree and has not been proven obsolete.

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
| `backend/` | Active | 4.75 reference backend execution layer |
| `apps/` | Active | Existing authorization/control-plane application |
| `sdk/` | Active | Python, TypeScript, Java and Go SDKs |
| `dashboard/` | Active | Web UI |
| `policy/` | Active | Policy definitions and policy assets |
| `k8s/` | Active | Current Kubernetes manifests only |
| `infra/` | Active | Current infrastructure; unused legacy snapshots removed |
| `docker/` | Active | Local/demo container stack |
| `observability/` | Active | Current observability contracts/assets |
| `ops/observability/` | Active | Prometheus/Grafana operational configuration |
| `docs/` | Active | Current project documentation |
| `scripts/` | Active | Canonical operational scripts; duplicates removed |
| `quality/` | Active | Formatting/lint/static quality checks |
| `runbooks/` | Review | Operational procedures; remove obsolete entries as discovered |
| `benchmarks/` | Review | Performance artifacts; retain only if still used |
| `chaos/` | Review | Chaos test artifacts; retain only if still used |
| `config/` | Active | Shared configuration |



## Scripts

OS-specific operational scripts are separated to keep execution paths explicit:

- Windows: `scripts/windows/` (`.ps1`)
- Linux: `scripts/linux/` (`.sh`)
- The policy signing-key generator has a single Python implementation at `scripts/linux/generate-policy-signing-key.py`; Windows invokes that implementation through PowerShell.
