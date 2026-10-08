#!/usr/bin/env bash
set -euo pipefail
base="http://localhost:8080"
tenant="11111111-1111-1111-1111-111111111111"
api_key="dev-master-key"

curl -fsS "$base/v1/health"
printf '\nPolicies: '
curl -fsS -H "X-API-Key: $api_key" -H "X-Tenant-Id: $tenant" "$base/v1/policies/all" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))'
printf 'Decisions: '
curl -fsS -H "X-API-Key: $api_key" -H "X-Tenant-Id: $tenant" "$base/v1/security/decisions" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))'
printf 'Incidents: '
curl -fsS -H "X-API-Key: $api_key" -H "X-Tenant-Id: $tenant" "$base/v1/security/incidents" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))'
printf 'Graph metrics: '
curl -fsS -H "X-API-Key: $api_key" -H "X-Tenant-Id: $tenant" "$base/v1/security-graph?windowMinutes=120" | python3 -c 'import json,sys; print(json.load(sys.stdin)["metrics"])'
echo "Smoke test passed."
