# Data Plane Failure Runbook

1. Confirm replica health and Envoy upstream health.
2. Verify Kubernetes PDB and HPA state.
3. Confirm the last-known-good signed bundle is present.
4. Confirm readiness reflects signature, pin, freshness and attestation state.
5. Keep FAIL_CLOSED for critical/high-risk workloads.
6. Restore capacity before changing policy behavior.
