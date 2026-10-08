#!/usr/bin/env bash
set -euo pipefail
DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/zt}"; DB_USERNAME="${DB_USERNAME:-zt}"; DB_PASSWORD="${DB_PASSWORD:-zt}"
if [[ "$DB_URL" =~ ^jdbc:postgresql://([^:/]+)(:([0-9]+))?/([^?]+) ]]; then HOST="${BASH_REMATCH[1]}"; PORT="${BASH_REMATCH[3]:-5432}"; DB="${BASH_REMATCH[4]}"; else echo "Unsupported DB_URL: $DB_URL" >&2; exit 1; fi
command -v psql >/dev/null || { echo "psql not found" >&2; exit 1; }; export PGPASSWORD="$DB_PASSWORD"
echo "Resetting PostgreSQL database '$DB' on $HOST:$PORT..."
psql -h "$HOST" -p "$PORT" -U "$DB_USERNAME" -d postgres -v ON_ERROR_STOP=1 -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB' AND pid <> pg_backend_pid();" >/dev/null
psql -h "$HOST" -p "$PORT" -U "$DB_USERNAME" -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"$DB\";" >/dev/null
psql -h "$HOST" -p "$PORT" -U "$DB_USERNAME" -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$DB\";" >/dev/null
echo "Database recreated. Start authorization-api; Flyway will apply V1__initial_schema.sql."
