#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MIG="$ROOT/apps/authorization-api/src/main/resources/db/migration"
versions=$(find "$MIG" -maxdepth 1 -name 'V*.sql' -printf '%f\n' | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n)
max=$(printf '%s\n' "$versions" | tail -1)
if [ "$max" != "39" ]; then echo "FAIL: expected migration head V39, got V$max"; exit 1; fi
if [ "$(printf '%s\n' "$versions" | sort | uniq -d | wc -l)" -ne 0 ]; then echo 'FAIL: duplicate migration versions'; exit 1; fi
echo "PASS: migration chain reaches V$max with no duplicate versions"
