#!/usr/bin/env bash
# tests/sanitize-har-canary.sh
# CI lint: detect real PII leak in fixtures/sanitized/.
# D-03 + D-04: only fake names from NAME_POOL should appear in sanitized/.
# If real surnames slipped through (sanitization regex missed a case), fail PR.
set -euo pipefail

echo "Step 1: ensure fixtures/sanitized/ exists"
test -d fixtures/sanitized || { echo "fixtures/sanitized/ missing — create or run sanitizer first"; exit 1; }

echo "Step 2: list of approved fake surnames (must appear after sanitization, no real names)"
APPROVED_FAKES="Иванов|Петров|Сидоров|Кузнецов|Новиков"

echo "Step 3: scan for any Russian surname pattern, exclude approved fakes"
# Find any 'Capital + 2+ lowercase Cyrillic + space + Capital + 2+ lowercase' pattern
# Exclude lines containing only approved fakes.
LEAK_COUNT=0
if find fixtures/sanitized -type f -name '*.har' | grep -q .; then
  while IFS= read -r FILE; do
    # Extract every Russian fullname-pattern; remove approved fakes; count remainder.
    HITS=$(grep -oE '[А-ЯЁ][а-яё]{2,}\s+[А-ЯЁ][а-яё]{2,}' "$FILE" 2>/dev/null \
      | grep -vE "^($APPROVED_FAKES)" || true)
    if [ -n "$HITS" ]; then
      echo "ERROR: potential real PII in $FILE:"
      echo "$HITS" | head -5
      LEAK_COUNT=$((LEAK_COUNT + 1))
    fi
  done < <(find fixtures/sanitized -type f -name '*.har')
else
  echo "Note: fixtures/sanitized/*.har not yet present (Plan 02 will populate). Skipping content scan."
fi

if [ "$LEAK_COUNT" -gt 0 ]; then
  echo "FAIL: $LEAK_COUNT files contain non-approved Russian-name patterns."
  echo "Action: re-run tools/sanitize-har.py with extended sanitize-rules.yaml."
  exit 1
fi

echo "PASS: fixtures/sanitized/ contains only approved fake names (or is empty)."
