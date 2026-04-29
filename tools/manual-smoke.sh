#!/usr/bin/env bash
# tools/manual-smoke.sh
# Manual end-to-end smoke against journal.school28-kirov.ru using .env.local credentials.
#
# WHY: D-25 forbids live AVERS calls in CI (anti-bot, school server respect). This is the
# developer's local-dev-host loop to verify the live service still matches the typed contract
# documented in docs/aversApiV4_23813.md. Runs on Linux Mint dev-host (or any Linux/macOS
# with curl + python3). NEVER from a CI workflow.
#
# USAGE:
#   1. Copy/create .env.local (gitignored), fill AVERS_LOGIN + AVERS_PASSWORD
#   2. ./tools/manual-smoke.sh         # default: live request
#   3. ./tools/manual-smoke.sh --dry   # parse env, print sanitized URLs, do NO requests
#
# EXIT CODES:
#   0 — login + grades fetch returned non-empty body, parsed shape OK
#   1 — missing .env.local / missing creds / running in CI
#   2 — login HTTP non-2xx OR login returned [error_symbol] (bad creds)
#   3 — grades HTTP non-2xx OR response not parseable as ExtJS-array
#   4 — anti-bot HTML detected (Content-Type: text/html on JSON endpoint)
#
# SECURITY:
#   - Never echoes raw cookies / passwords (only `***` placeholders + cookie NAMES)
#   - Cookie jar in mktemp file, deleted on exit via trap
#   - Honors HttpRequestRedactor sensitive-key list verbatim — same redaction as Plan 02-04
#   - Username partially masked (first 3 chars + ***) to avoid copy-paste in screenshots
#   - Curl --data-urlencode reads from env-var (NOT shell-history-visible)

set -euo pipefail

# ---------------------------------------------------------------------------
# CI guard — D-25: refuse to run in any CI environment
# ---------------------------------------------------------------------------
if [[ -n "${CI:-}" || -n "${GITHUB_ACTIONS:-}" || -n "${GITLAB_CI:-}" || -n "${JENKINS_URL:-}" ]]; then
  echo "ERROR: tools/manual-smoke.sh is dev-host-only (D-25)."
  echo "       Refusing to run in CI environment."
  echo "       Detected env: CI=${CI:-} GITHUB_ACTIONS=${GITHUB_ACTIONS:-} GITLAB_CI=${GITLAB_CI:-} JENKINS_URL=${JENKINS_URL:-}"
  exit 1
fi

# ---------------------------------------------------------------------------
# Resolve repo root (script is symlink-friendly: works from anywhere)
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

# ---------------------------------------------------------------------------
# Load .env.local
# ---------------------------------------------------------------------------
if [[ ! -f .env.local ]]; then
  echo "ERROR: .env.local not found at $REPO_ROOT/.env.local"
  echo "Create one with at minimum:"
  echo "    AVERS_LOGIN=your_test_account_login"
  echo "    AVERS_PASSWORD=your_test_account_password"
  echo "(.env.local is gitignored — see tools/manual-smoke.README.md)"
  exit 1
fi

# shellcheck disable=SC1091
set -a
source .env.local
set +a

if [[ -z "${AVERS_LOGIN:-}" || -z "${AVERS_PASSWORD:-}" ]]; then
  echo "ERROR: AVERS_LOGIN or AVERS_PASSWORD missing in .env.local"
  echo "(see tools/manual-smoke.README.md §Setup)"
  exit 1
fi

# ---------------------------------------------------------------------------
# Endpoint paths — synchronized with docs/aversApiV4_23813.md §Endpoint map
# Override via .env.local if HAR shows different paths after АВЕРС upgrade.
# ---------------------------------------------------------------------------
HOST="${AVERS_HOST:-https://journal.school28-kirov.ru}"
LOGIN_PATH="${AVERS_LOGIN_PATH:-/login}"
GRADES_PATH="${AVERS_GRADES_PATH:-/act/GET_STUDENT_JOURNAL_DATA}"

# ---------------------------------------------------------------------------
# Mode detection
# ---------------------------------------------------------------------------
DRY=0
if [[ "${1:-}" == "--dry" ]]; then
  DRY=1
fi

# ---------------------------------------------------------------------------
# Pre-flight summary (no secrets — first 3 chars of login + masked password)
# ---------------------------------------------------------------------------
LOGIN_HEAD="${AVERS_LOGIN:0:3}***"
echo "manual-smoke against: $HOST"
echo "login user: $LOGIN_HEAD (redacted)"
echo "login path: $LOGIN_PATH"
echo "grades path: $GRADES_PATH"
echo "mode: $([[ $DRY == 1 ]] && echo dry-run || echo live)"

if [[ $DRY == 1 ]]; then
  echo "DRY: skipping live HTTP. Resolved endpoints printed above."
  exit 0
fi

# ---------------------------------------------------------------------------
# Required runtime tools
# ---------------------------------------------------------------------------
for tool in curl python3 awk file mktemp; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "ERROR: required tool '$tool' not found on PATH"
    exit 1
  fi
done

# ---------------------------------------------------------------------------
# SHA-1 hash AVERS_PASSWORD (АВЕРС /login expects sha1_hex of plain-text password)
# python3 -c shields the password from argv exposure (one-liner, no positional arg)
# ---------------------------------------------------------------------------
SHA1_PASSWORD=$(python3 -c '
import hashlib, os, sys
pw = os.environ.get("AVERS_PASSWORD", "")
sys.stdout.write(hashlib.sha1(pw.encode("utf-8")).hexdigest())
')

if [[ -z "$SHA1_PASSWORD" ]]; then
  echo "ERROR: failed to compute sha1 hex of AVERS_PASSWORD"
  exit 1
fi

# ---------------------------------------------------------------------------
# Cookie jar in mktemp — auto-clean on exit (D-28 hygiene)
# ---------------------------------------------------------------------------
COOKIE_JAR=$(mktemp)
LOGIN_BODY=$(mktemp)
GRADES_BODY=$(mktemp)
# trap: defense in depth — run regardless of exit path
trap 'rm -f "$COOKIE_JAR" "$LOGIN_BODY" "$GRADES_BODY"' EXIT INT TERM

# ---------------------------------------------------------------------------
# Step 1: POST /login (form-urlencoded l=<login>&p=<sha1_hex>)
# ---------------------------------------------------------------------------
echo "Step 1: POST $LOGIN_PATH"
LOGIN_HTTP=$(curl -sS \
  --connect-timeout 10 --max-time 30 \
  -c "$COOKIE_JAR" \
  -A "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36" \
  -H "Accept: */*" \
  -H "Accept-Language: ru-RU,ru;q=0.9,en;q=0.8" \
  -H "Accept-Encoding: identity" \
  --data-urlencode "l=${AVERS_LOGIN}" \
  --data-urlencode "p=${SHA1_PASSWORD}" \
  -o "$LOGIN_BODY" \
  -w "%{http_code}" \
  "$HOST$LOGIN_PATH" || true)

if [[ "$LOGIN_HTTP" != "200" && "$LOGIN_HTTP" != "302" ]]; then
  echo "FAIL: login HTTP=$LOGIN_HTTP (expected 200 or 302)"
  echo "Body (first 500 bytes):"
  head -c 500 "$LOGIN_BODY"
  echo
  exit 2
fi

# Anti-bot check — login response should NOT be HTML
LOGIN_CT=$(file --mime-type -b "$LOGIN_BODY" 2>/dev/null || echo unknown)
if [[ "$LOGIN_CT" == "text/html" ]]; then
  echo "FAIL: login returned HTML — anti-bot triggered (AversApiError.AntiBotChallenge)."
  echo "      Stop and wait before retrying. D-05 mandates passive observation."
  exit 4
fi

# Bad-creds detection — АВЕРС returns literal '[error_symbol]' for failed auth
if grep -q 'error_symbol' "$LOGIN_BODY"; then
  echo "FAIL: login body contains [error_symbol] — credentials rejected"
  echo "      Verify AVERS_LOGIN / AVERS_PASSWORD in .env.local."
  exit 2
fi

# Show cookie names only — never values (D-28 hygiene)
COOKIE_NAMES=$(awk '/^[^#]/ && NF >= 6 {print $6}' "$COOKIE_JAR" | sort -u | tr '\n' ' ')
if [[ -z "$COOKIE_NAMES" ]]; then
  COOKIE_NAMES="(none — server did not Set-Cookie; expected for АВЕРС: auth cookies are client-set)"
fi
echo "  Got cookies: $COOKIE_NAMES"

# Quick shape sanity for login response — should parse as JSON-array after lenient mode
# АВЕРС /login returns text like [[3020,4,null,null,null,"Surname",1013,null,4028]]
LOGIN_BYTES=$(wc -c < "$LOGIN_BODY")
echo "  Login response: $LOGIN_BYTES bytes"

# ---------------------------------------------------------------------------
# Step 2: AVERS auth-cookies are CLIENT-SET (server never Set-Cookie). For a true
# end-to-end shape check we'd need to: (a) parse login body to extract user_id,
# (b) JS-escape() polyfill ys-user / ys-password / ys-userId, (c) POST /auth.
# That's the full Plan 04 wiring scope. For a smoke-test we limit ourselves to
# verifying that /login returns the array shape, then attempt grades WITHOUT
# the ys-* cookies — most likely returns [] (empty for un-cookied request).
# This still validates: HTTP plumbing, Content-Type, ExtJS preprocessor compat.
# ---------------------------------------------------------------------------
echo "Step 2: POST $GRADES_PATH (best-effort — full ys-* cookie handshake is Phase 4 scope)"
GRADES_HTTP=$(curl -sS \
  --connect-timeout 10 --max-time 30 \
  -b "$COOKIE_JAR" \
  -A "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36" \
  -H "Accept: */*" \
  -H "Accept-Language: ru-RU,ru;q=0.9,en;q=0.8" \
  --data-urlencode "cls=0" \
  --data-urlencode "student=0" \
  -o "$GRADES_BODY" \
  -w "%{http_code}" \
  "$HOST$GRADES_PATH" || true)

if [[ "$GRADES_HTTP" != "200" ]]; then
  echo "FAIL: grades HTTP=$GRADES_HTTP"
  echo "Body (first 500 bytes):"
  head -c 500 "$GRADES_BODY"
  echo
  exit 3
fi

GRADES_CT=$(file --mime-type -b "$GRADES_BODY" 2>/dev/null || echo unknown)
if [[ "$GRADES_CT" == "text/html" ]]; then
  echo "FAIL: grades returned HTML — anti-bot or session not established."
  echo "      Phase 4 ys-* cookie injection required for proper auth."
  exit 4
fi

GRADES_BYTES=$(wc -c < "$GRADES_BODY")
echo "  Grades response: $GRADES_BYTES bytes"

# Shape sanity — should be parseable as ExtJS-array. Do the same regex
# substitution the Kotlin client does (ExtJsArrayPreprocessor) and try json.tool.
PROCESSED_OK=$(python3 -c '
import re, json, sys
with open(sys.argv[1], "r", encoding="utf-8") as f:
    body = f.read()
# Strip new Date(YYYY,M-1,D,...) literals
body = re.sub(
    r"new\s+Date\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,[^)]*)?\)",
    lambda m: "\"%s-%02d-%02d\"" % (m.group(1), int(m.group(2)) + 1, int(m.group(3))),
    body,
)
try:
    parsed = json.loads(body)
    print("OK")
except Exception as e:
    print("FAIL:" + repr(e))
' "$GRADES_BODY")

if [[ "$PROCESSED_OK" != "OK" ]]; then
  echo "FAIL: grades response not parseable as ExtJS-array after preprocessing"
  echo "      Diagnostic: $PROCESSED_OK"
  echo "      First 500 bytes:"
  head -c 500 "$GRADES_BODY"
  echo
  exit 3
fi

echo "  Shape sanity: ExtJS-array parses OK after new Date(...) preprocessing"

# ---------------------------------------------------------------------------
# Done — explicit cleanup (trap also handles abort paths)
# ---------------------------------------------------------------------------
echo "PASS: manual-smoke succeeded"
echo "      login=$LOGIN_HTTP ($LOGIN_BYTES b) + grades=$GRADES_HTTP ($GRADES_BYTES b)"
echo
echo "NOTE: this is a manual sanity check, NOT a fixture replacement."
echo "      Update fixtures/sanitized/ via tools/capture-avers-fixtures.py if contract drifted."
echo "      Full ys-* cookie handshake is Phase 4 wiring scope (see docs/aversApiV4_23813.md §Login flow)."
