#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
echo "This deletes the local demo PostgreSQL volume."
read -r -p "Type RESET to continue: " confirmation
if [[ "$confirmation" != "RESET" ]]; then
  exit 1
fi
docker compose -f docker/docker-compose.yml down -v
docker compose -f docker/docker-compose.yml up --build -d
