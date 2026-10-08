# Security SLO Runbook

## SLOs
- Authorization availability >= 99.99%
- P99 decision latency < 5 ms
- Unauthorized ALLOW = 0
- Unsigned/stale bundles accepted = 0
- Unverified workload allowed = 0
- Evidence loss = 0

## Incident priority
1. Any unauthorized ALLOW or unverified workload ALLOW: SEV-0 security incident.
2. Stale or unsigned bundle accepted: SEV-0.
3. Availability below 99.99%: SEV-1 unless fail-closed is working as designed.
4. P99 > 5 ms for 15 minutes: SEV-2.

## First checks
- `/ready`
- `/v1/fast/stats`
- `/v1/fast/attestation`
- Prometheus alerts
- current bundle version/hash/pin
- Envoy mTLS health

## Safety rule
Do not switch to FAIL_OPEN during a security incident unless the incident commander explicitly approves the risk and records the exception.
