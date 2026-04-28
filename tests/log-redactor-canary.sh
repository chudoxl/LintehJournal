#!/usr/bin/env bash
# tests/log-redactor-canary.sh
# CI lint: ensure HttpRequestRedactor + sanitizeHeader prevent password leak in test logs.
# Canary string from ROADMAP success #5 / Pitfall #5: kanareyka_PASSWORD_DO_NOT_LEAK_42.
# Plan 04 wires the canary into a parameterized HttpRequestRedactorTest case. This script
# runs the test and asserts the canary is NOT present in stdout.
set -euo pipefail

CANARY="kanareyka_PASSWORD_DO_NOT_LEAK_42"

echo "Step 1: run :core:network commonTest (debug-build per D-28 addendum)"
# --rerun-tasks ensures fresh stdout (no Gradle cache hit suppressing output).
LOGFILE=$(mktemp)
trap 'rm -f "$LOGFILE"' EXIT

if ! ./gradlew :core:network:test --rerun-tasks --info > "$LOGFILE" 2>&1; then
  # If :core:network has no tests yet (Plan 04 not run), skip canary check
  if grep -q 'No tests found' "$LOGFILE" || grep -q "Project ':core:network' is not a test project" "$LOGFILE"; then
    echo "Note: :core:network tests not yet created (Plan 04). Canary script ready, skipping content scan."
    exit 0
  fi
  echo "ERROR: gradle test failed:"
  tail -50 "$LOGFILE"
  exit 1
fi

echo "Step 2: scan stdout for canary string"
if grep -q "$CANARY" "$LOGFILE"; then
  echo "FAIL: canary string '$CANARY' leaked into test output."
  echo "Affected lines:"
  grep -n "$CANARY" "$LOGFILE" | head -10
  echo "Fix: extend HttpRequestRedactor sensitive keys (currently password|pwd|pass|cookie|authorization|set-cookie|token)."
  exit 1
fi

echo "PASS: canary '$CANARY' absent from test logs (redactor working as designed)."
