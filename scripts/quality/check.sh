#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "[1/6] Python compile"
python -m compileall -q sdk/python

echo "[2/6] Python line-length audit"
python scripts/quality/line_audit.py --language python --max-length 100

echo "[3/6] TypeScript"
if command -v npm >/dev/null 2>&1; then
  npm --prefix sdk/typescript test
else
  echo "npm not installed; TypeScript check skipped"
fi

echo "[4/6] Go"
if command -v go >/dev/null 2>&1; then
  (cd sdk/go && gofmt -w ztsecurity/*.go && go test ./...)
else
  echo "go not installed; Go check skipped"
fi

echo "[5/6] Java lexical audit"
python scripts/quality/java_audit.py

echo "[6/6] Line-length report"
python scripts/quality/line_audit.py --language java --max-length 140
python scripts/quality/line_audit.py --language typescript --max-length 120

echo "Quality baseline checks completed."
