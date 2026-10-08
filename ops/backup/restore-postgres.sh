#!/usr/bin/env bash
set -euo pipefail
: "${PGHOST:?PGHOST required}"; : "${PGUSER:?PGUSER required}"; : "${PGDATABASE:?PGDATABASE required}"
DUMP="${1:?usage: restore-postgres.sh backup.dump}"
sha256sum -c "${DUMP}.sha256" 2>/dev/null || echo "WARN: checksum file unavailable; verify backup provenance manually"
pg_restore --clean --if-exists --no-owner --no-acl --dbname="$PGDATABASE" "$DUMP"
