# tools/manual-smoke.sh — usage guide

Local-only end-to-end smoke test against the live `journal.school28-kirov.ru` АВЕРС
endpoint using developer credentials from `.env.local`. Companion to
`docs/aversApiV4_23813.md` §Manual smoke section.

## When to run

- After bumping Ktor / kotlinx.serialization versions to verify the live АВЕРС contract
  still matches `docs/aversApiV4_23813.md` shape-for-shape.
- After АВЕРС announces an upgrade (build 23813 → 23901+) — quickly check whether the
  endpoints still respond before triggering a full Chrome DevTools / programmatic
  re-capture session via `tools/capture-avers-fixtures.py`.
- Before opening a PR that modifies `core/network/` or `core/api-avers-v4/` — sanity-check
  on a single live login + grades fetch.

## When NOT to run

- **Never in CI.** D-25 forbids live АВЕРС traffic from automated workflows. The script
  detects `$CI` / `$GITHUB_ACTIONS` / `$GITLAB_CI` / `$JENKINS_URL` and exits 1 immediately.
- **Never as a recurring cron** — would constitute a bot-pattern, violates D-05.
- **Never for performance benchmarking.** Phase 2 has no SLA on latency (deferred to v2).
- **Never for fixture capture.** Use `tools/capture-avers-fixtures.py` for that — it
  produces sanitizer-ready HAR files; manual-smoke is a tripwire, not a recorder.

## Prerequisites

Required runtime tools on dev-host (Linux Mint by default — all bundled, no extra install):

- `bash` (≥4.0)
- `curl` (≥7.0)
- `python3` (≥3.6 — for SHA-1 password hashing + ExtJS preprocessor sanity)
- `file` (mime-type detection — `coreutils` package on macOS if missing)
- `awk`, `mktemp` (POSIX standard)

## Setup

1. **Create `.env.local`** at repo root (gitignored, never commit):

   ```sh
   AVERS_LOGIN=your_test_account_login
   AVERS_PASSWORD=your_test_account_password

   # Optional overrides if HAR-derived paths differ in a new АВЕРС build:
   # AVERS_HOST=https://journal.school28-kirov.ru
   # AVERS_LOGIN_PATH=/login
   # AVERS_GRADES_PATH=/act/GET_STUDENT_JOURNAL_DATA
   ```

   `.env.local` is already in `.gitignore` (Phase 1 — confirm with `git check-ignore .env.local`).

2. **Confirm `tools/manual-smoke.sh` is executable:**
   ```sh
   chmod +x tools/manual-smoke.sh
   ```

3. **Verify CI-prohibition guard works** (sanity check):
   ```sh
   CI=1 bash tools/manual-smoke.sh --dry  # should exit 1 with refusal message
   echo $?                                 # → 1
   ```

## Usage

```sh
# Default — live request to journal.school28-kirov.ru
./tools/manual-smoke.sh

# Dry-run — print resolved endpoints, source .env.local, do NO HTTP
./tools/manual-smoke.sh --dry
```

## Expected output (success)

```
manual-smoke against: https://journal.school28-kirov.ru
login user: Ива*** (redacted)
login path: /login
grades path: /act/GET_STUDENT_JOURNAL_DATA
mode: live
Step 1: POST /login
  Got cookies: (none — server did not Set-Cookie; expected for АВЕРС: auth cookies are client-set)
  Login response: 53 bytes
Step 2: POST /act/GET_STUDENT_JOURNAL_DATA (best-effort — full ys-* cookie handshake is Phase 4 scope)
  Grades response: 4 bytes
  Shape sanity: ExtJS-array parses OK after new Date(...) preprocessing
PASS: manual-smoke succeeded
      login=200 (53 b) + grades=200 (4 b)

NOTE: this is a manual sanity check, NOT a fixture replacement.
      Update fixtures/sanitized/ via tools/capture-avers-fixtures.py if contract drifted.
      Full ys-* cookie handshake is Phase 4 wiring scope (see docs/aversApiV4_23813.md §Login flow).
```

> **Note on cookies:** АВЕРС server NEVER sets `Set-Cookie` (Plan 02-02 SUMMARY). Auth state
> lives in three client-set cookies (`ys-user`, `ys-password`, `ys-userId`) installed by the
> SPA via `Ext.state.CookieProvider` after the login response is parsed. The smoke script
> deliberately does NOT replicate this handshake (it requires JS-escape() polyfill — Phase 4
> scope). Grades response without those cookies will typically be `[]` (empty array) — that
> still validates HTTP plumbing, Content-Type, and ExtJS-preprocessor compatibility.

## Exit codes

| Code | Meaning |
|------|---------|
| 0 | login + grades returned valid bodies; ExtJS-array parses; contract probably still intact |
| 1 | missing `.env.local`, missing creds, missing runtime tools, OR running in CI |
| 2 | login HTTP non-2xx OR body contains `[error_symbol]` (bad creds / endpoint changed / rate-limited?) |
| 3 | grades HTTP non-2xx OR response not parseable as ExtJS-array (envelope / shape changed?) |
| 4 | anti-bot HTML detected — STOP and wait before retrying (D-05) |

## Security & D-28 compliance

- The script **never** prints raw cookie values, password, or `Authorization` headers.
  Only cookie names are echoed (e.g. `JSESSIONID` if it ever appeared — the value is masked).
- The cookie jar is a `mktemp` file deleted on exit (`trap` on `EXIT INT TERM`).
- Response bodies are also `mktemp`-temporary and `rm -f`-cleaned at end of run.
- Username is partially masked (`foo***` — first 3 chars + asterisks) to avoid copy-paste
  accidents in screenshots / shared screens.
- Password is SHA-1-hashed via `python3 -c` reading `os.environ` — never appears in
  shell history, `ps -ef` argv, or curl positional args.
- `curl --data-urlencode "p=${SHA1_PASSWORD}"` — the **hashed** value goes into curl argv,
  never the plain-text password.

## Relationship to CI

| Layer | Where | What |
|-------|-------|------|
| Unit / integration tests | CI Android job + macos-15 job | Ktor MockEngine + sanitized HAR fixtures (D-25) |
| Canary scripts | CI Android job | `tests/log-redactor-canary.sh`, `tests/sanitize-har-canary.sh` |
| **manual-smoke** | **dev-host only** | Live `journal.school28-kirov.ru` request — **NEVER in CI** |

If `tools/manual-smoke.sh` ever appears in `.github/workflows/*.yml` — that's a bug.
The script's first action is to detect CI env-vars and exit 1.

## Updating endpoint paths

If a Chrome DevTools / programmatic re-capture (`tools/capture-avers-fixtures.py`) shows a
different login or grades path:

1. Update `docs/aversApiV4_23813.md` §Endpoint map (or open a new build doc per D-07).
2. Update the `LOGIN_PATH` / `GRADES_PATH` defaults in `tools/manual-smoke.sh`, OR set them
   per-run via env-vars in `.env.local` (`AVERS_HOST`, `AVERS_LOGIN_PATH`,
   `AVERS_GRADES_PATH`).
3. Bump `docs/aversApiV4_23813-CHANGELOG.md` with the diff entry (or create
   `docs/aversApiV4_NNNNN.md` if the build number changed).

## Why bash + curl (not Kotlin)

The smoke test is intentionally Gradle-free: a fresh dev-host clone can run it without
booting JVM, KSP, Kotlin/Native compile, etc. This makes it the cheapest possible
"is the live service responding?" check. The typed `:core:api-avers-v4` contract still
covers everything else; manual-smoke is just a tripwire.

## Troubleshooting

- **`ERROR: .env.local not found`** — create `.env.local` per §Setup; check working
  directory is repo root (script auto-`cd`-s but only if invoked via path).
- **`FAIL: login HTTP=403`** — possibly anti-bot; wait 30+ minutes before retrying. D-05
  passive-observation applies even to dev-host.
- **`FAIL: login body contains [error_symbol]`** — credentials wrong; verify `.env.local`
  values against current test account.
- **`FAIL: grades returned HTML`** — full ys-* cookie handshake required; this is expected
  behavior in the smoke-only path. Use `tools/capture-avers-fixtures.py` for a complete
  flow that handles client-side cookie injection.
- **`required tool 'X' not found on PATH`** — install `curl` / `python3` / `file` /
  `coreutils` via your package manager.
