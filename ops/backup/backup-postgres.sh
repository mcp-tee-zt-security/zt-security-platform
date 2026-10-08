#!/usr/bin/env bash
set -euo pipefail
: "${PGHOST:?PGHOST required}"; : "${PGUSER:?PGUSER required}"; : "${PGDATABASE:?PGDATABASE required}"
OUT_DIR="${BACKUP_DIR:-./backups}"
mkdir -p "$OUT_DIR"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
pg_dump --format=custom --no-owner --no-acl "$PGDATABASE" > "$OUT_DIR/${PGDATABASE}-${TS}.dump"
sha256sum "$OUT_DIR/${PGDATABASE}-${TS}.dump" > "$OUT_DIR/${PGDATABASE}-${TS}.dump.sha256"
echo "Backup written to $OUT_DIR/${PGDATABASE}-${TS}.dump"
