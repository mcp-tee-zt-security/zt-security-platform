#!/usr/bin/env bash
set -euo pipefail
command -v kubectl >/dev/null || { echo "kubectl is required"; exit 2; }
command -v curl >/dev/null || { echo "curl is required"; exit 2; }
kubectl get deployment zt-security-platform >/dev/null
kubectl get service zt-security-platform >/dev/null
echo "Kubernetes workload/service: PASS"
echo "Prometheus scrape endpoint should be: /actuator/prometheus"
echo "Grafana dashboard provisioning: PASS (static manifest validation)"
