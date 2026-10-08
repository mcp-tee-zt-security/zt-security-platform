# Kubernetes deployment

`deploy/` contains the authorization API deployment base. `operator/` watches
namespaced ZeroTrustPolicy resources and synchronizes policy DSL drafts to the
authorization API database. It does not apply workload changes or verify workload
identity, attestation, or capabilities.

## Authorization API

1. Provide PostgreSQL and Redis; they are external prerequisites, not deployed by
   this base. Configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` in a Secret named
   `zt-security-secrets`. Configure a random `ZT_API_KEY` and an Ed25519 signing
   key/key ID there as well. `deploy/secrets.example.yaml` is a template and is
   intentionally excluded from the deployment.
2. Configure `REDIS_HOST`, `REDIS_PORT`, `ZT_OIDC_ISSUER_URI` in an environment
   overlay. The OIDC issuer must serve HTTPS and the expected tenant/workspace
   claims and roles. The default URL is a placeholder. Database/Redis authentication
   and TLS must be configured for the deployment environment; the current base
   uses the application's existing Redis connection settings.
3. Build `docker build -t YOUR_REGISTRY/authorization-api:YOUR_TAG -f apps/authorization-api/Dockerfile .`
   from the repository root and push it to your registry. Set the image in your
   overlay to that tag or an immutable digest and configure imagePullSecrets when
   needed. `:local` is a build placeholder, not a production release.
4. Adapt NetworkPolicy peers/CIDRs for the actual database, Redis, OIDC and ingress
   locations. The base permits same-namespace TCP 8080 ingress, same-namespace
   PostgreSQL/Redis egress, cluster DNS UDP/TCP 53, and HTTPS egress. Narrow HTTPS
   CIDRs in production. External databases and Redis require explicit egress rules.
   NetworkPolicy requires a supporting CNI. HPA requires Metrics Server.
5. Render with `kubectl kustomize k8s/deploy` and validate against the target
   cluster with `kubectl apply --dry-run=server -k YOUR_OVERLAY`. After reviewing
   the render, deploy with `kubectl apply -k YOUR_OVERLAY` and check
   `kubectl -n zt-security rollout status deployment/authorization-api`.

The container runs as UID/GID 10001, has a read-only root filesystem and a writable
bounded `/tmp`. Startup/liveness probes check process health; readiness checks
database and Redis health. Keep `/actuator` off public ingress; metrics/health are
unauthenticated in the current backend. TLS ingress, secrets, datastore backups,
resource sizing, OIDC role mapping, and restore drills remain environment work.

## Policy draft operator

Build and test from the repository root:

```sh
docker build --target test -t zt-policy-operator-tests k8s/operator
docker build -t YOUR_REGISTRY/policy-operator:YOUR_TAG k8s/operator
```

Install the CRD as a cluster administrator:

```sh
kubectl apply -f k8s/operator/config/crd/bases/security.zt-platform.io_zerotrustpolicies.yaml
```

Configure an overlay for `operator/config/deploy/` with the target namespace,
image and HTTPS `PLATFORM_URL`. Create `zt-policy-operator-platform` in that
namespace through your secret manager, containing `tenant-id`, `bearer-token`
and optionally `workspace-id`. The bearer token must have the platform permissions
needed by the policy API and matching tenant/workspace claims. Refresh it before
expiry. The operator rereads the projected token file on each request. It does not
mint or refresh OIDC tokens itself. Do not use the backend's master API key here.

```sh
kubectl kustomize k8s/operator/config/deploy
kubectl apply --dry-run=server -k YOUR_OPERATOR_OVERLAY
kubectl apply -k YOUR_OPERATOR_OVERLAY
kubectl -n YOUR_NAMESPACE apply -f k8s/operator/config/samples/security_v1alpha1_zerotrustpolicy.yaml
kubectl -n YOUR_NAMESPACE get zerotrustpolicies
kubectl -n YOUR_NAMESPACE get zerotrustpolicy production-api -o yaml
```

RBAC grants namespaced resource reads/status writes, leader-election leases and
event recording. Each operator deployment watches one namespace and uses one configured
tenant/workspace; use separate deployments and credentials for different scopes.
Grant CR creation/update rights only to the policy authors for that tenant.
Operator egress must allow the Kubernetes API, cluster DNS and HTTPS platform URL
when the target namespace has default-deny network policies. A private CA can be
mounted and selected with `SSL_CERT_FILE`; TLS verification is never disabled.

`spec.policyText` contains one complete DSL policy. The API validates it while
storing it as `DRAFT`. The platform record name is `k8s-<resource UID>` and its
version is the Kubernetes generation. An unchanged version is reused on retries;
different content at the same version is rejected. Edits create new drafts.
`status.phase: Synced` means the API acknowledged storage, not activation or
workload enforcement. Validate, simulate and activate the saved version in Policy
Studio. Dashboard activation is preserved by reconciliation. Backend modifications
at the same version are reported as a conflict.

API failures are retried after 30 seconds, and acknowledged versions are rechecked
every five minutes. Readiness reports the controller's availability, not backend
reachability; inspect CR status for synchronization health. Deleting a CR retains
its backend versions, including active policies, to avoid silently removing an
authorization rule. Deactivate/archive those versions through platform governance.

The former `workload`, `identityRequired`, `attestationRequired`, `capabilities`,
`decision`, and `approvalRequired` fields are unsupported and rejected by the
controller. Migrate existing CRs to `policyText` before upgrading the CRD. This
implementation does not call `/v1/kubernetes/policies`, whose backend currently
records `NOT_APPLIED` because no workload apply connector exists.

## Verification boundary

Local build/tests and Kustomize rendering do not establish production readiness.
Before using a cluster, validate schema/RBAC against its Kubernetes version and
test token expiry, API outages, two-replica leader failover, Secret rotation,
database migrations, policy activation/evaluation, backup restore and network
isolation. No target cluster is selected or changed by repository checks.
