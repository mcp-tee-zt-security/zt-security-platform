# Bundle Stale Runbook

1. Check Control Plane health and policy distribution endpoint.
2. Compare bundle version/hash across replicas.
3. Verify Ed25519 signature and configured pin.
4. Do not manually replace bundle files in a running pod.
5. Restore distribution, then perform canary rollout.
6. Confirm readiness and SLO recovery.
