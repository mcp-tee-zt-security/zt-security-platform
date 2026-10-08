#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
echo "[ZT Security] Starting Docker stack..."
docker compose -f docker/docker-compose.yml up --build -d
echo "Waiting for API..."
for _ in $(seq 1 30); do
  if curl -fsS http://localhost:8080/v1/health | grep -q '"UP"'; then
    break
  fi
  sleep 3
done
echo "Dashboard: http://localhost:3000"
echo "Swagger:   http://localhost:8080/swagger-ui.html"
echo "Health:    http://localhost:8080/v1/health"
