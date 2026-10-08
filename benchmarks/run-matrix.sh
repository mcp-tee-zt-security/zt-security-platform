#!/usr/bin/env bash
set +e
for r in 1000 10000 50000 100000; do
  echo "=== ${r} RPS ==="
  RPS=$r DURATION=${DURATION:-30s} k6 run benchmarks/k6-fast-path.js
  echo
 done
