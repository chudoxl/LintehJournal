---
phase: 02-api-reverse-engineering-network-layer
plan: 08
subsystem: docs-and-tools
tags: [documentation, api-contract, manual-smoke, dev-host, github-pages, reverse-engineering, d-05, d-25, d-27, d-28]

# Dependency graph
requires:
  - phase: 02-api-reverse-engineering-network-layer
    provides: "Plan 02-02 — 12 sanitized HAR fixtures + reverse-engineered auth/endpoint contract observations (ys-* cookies, JS escape() polyfill, IncompleteRead, ExtJS new Date() literals); Plan 02-06 — typed contract module :core:api-avers-v4 (AversEndpoints constants, AversApiError sealed including AntiBotChallenge data class with rawHtml, ApiResult sealed); Plan 02-07 — kill-switch wire-format (docs/api-config.json schema deployed via pages.yml)"

provides:
  - "docs/aversApiV4_23813.md — canonical 558-line API-contract narrative for АВЕРС v4.1 build 23813 (ROADMAP Phase 2 success #2 deliverable). 9 H2 sections: Login flow / Response format / Endpoint map / Anti-bot signals / Pagination / Coverage gaps / Error model / Logging hygiene / Kill-switch."
  - "docs/aversApiV4_23813-CHANGELOG.md — versioned-API diff journal seeded with 2026-04-28 build 23813 baseline entry + Format template + workflow checklist for future АВЕРС upgrades."
  - "tools/manual-smoke.sh — bash + curl dev-host live smoke against journal.school28-kirov.ru. Refuses CI ($CI / $GITHUB_ACTIONS / $GITLAB_CI / $JENKINS_URL detection). Reads .env.local, SHA-1-hashes password via python3 -c (env-read, never argv-exposed), POSTs /login, validates ExtJS-array shape via Python preprocessor port. mktemp + trap cleanup."
  - "tools/manual-smoke.README.md — 167-line usage guide: when-to-run, prerequisites, setup, expected output, exit codes (0/1/2/3/4), security & D-28 compliance, CI-relationship matrix, troubleshooting."

affects:
  - "Phase 4 :core:data: developers consult docs/aversApiV4_23813.md §Endpoint map + §Bootstrap chain (B1-B5) instead of grepping HAR fixtures. Phase 4 must implement JS-escape() polyfill in :core:platform, SHA-1 password hashing, /auth two-step completion, mapper DTO→domain — all referenced from the doc."
  - "Phase 3 Auth UI: §Login flow + §Anti-bot signals + §Error model give the WebView CAPTCHA fallback (D-06) implementation context. AversApiError.AntiBotChallenge.rawHtml is the WebView payload."
  - "Future АВЕРС upgrades (build 23901+): the changelog Format template + workflow checklist define the parallel-module + parallel-doc invariant. New build NNNNN → new core/api-avers-v4-NNNNN/ module + new docs/aversApiV4_NNNNN.md + changelog entry + api-config.json bump after TestFlight rollout."
  - "GitHub Pages: docs/aversApiV4_23813.md + docs/aversApiV4_23813-CHANGELOG.md auto-deploy via existing pages.yml workflow (Phase 1 — paths: 'docs/**'). No workflow edit needed."
  - "Plan 02-09 (final integration smoke): independent of Plan 02-08. The manual-smoke tooling is dev-only and does not run in CI."

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Build-pinned API documentation — docs/aversApiV4_NNNNN.md per АВЕРС build, with parallel Kotlin module :core:api-avers-v4-NNNNN. Changelog tracks deltas. Old doc/module retained as fallback during decommission window. Pattern locked in for Phase 5+ multi-build support."
    - "Dev-host-only smoke tooling — bash + curl + python3 (no Gradle/JVM/Kotlin runtime). Multi-CI-env-var detection (CI / GITHUB_ACTIONS / GITLAB_CI / JENKINS_URL) refuses to run in any automated environment. mktemp cookie jar + body files with trap-on-EXIT/INT/TERM cleanup. Username masked, password SHA-1-hashed via python3 -c reading os.environ (never argv-visible)."
    - "ExtJS-array preprocessor portable in Python — manual-smoke.sh ports the Kotlin ExtJsArrayPreprocessor.toStrictJson regex (`new\\s+Date\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,[^)]*)?\\)` → ISO `YYYY-MM-DD`) so dev-host can validate response shape without JVM."

key-files:
  created:
    - "docs/aversApiV4_23813.md"
    - "docs/aversApiV4_23813-CHANGELOG.md"
    - "tools/manual-smoke.sh"
    - "tools/manual-smoke.README.md"
  modified: []

key-decisions:
  - "Direct-array response format documented as the dominant АВЕРС pattern — Plan template assumed envelope OR direct-JSON OR mix; Plan 02-02 + 02-06 reality is direct JsonArray for all 6 endpoints (no `{success, data}` wrapper). Doc records the AversEnvelope.unwrap behavior table for forward-compat in case future build switches to envelope."
  - "ys-* client-set cookies documented as the auth mechanism — explicit negative findings (no JSESSIONID / no PHPSESSID / no Set-Cookie / no CSRF token) make the unusual scheme unmistakable to future maintainers. JS escape() polyfill rationale embedded with worked example (Cyrillic codepoint emission as %uXXXX, not UTF-8 byte sequence)."
  - "Bootstrap chain (B1-B5) included in §Endpoint map — Plan 02-06 deferred bootstrap orchestration to Phase 4 :core:data, but the doc captures the chain so Phase 4 author has the contract reference. Phase 5 multi-account scope also depends on this chain to derive per-account StudentScope."
  - "Manual-smoke does NOT replicate full ys-* handshake — that requires JS-escape() polyfill (Phase 4 scope). Smoke-test scope deliberately limited to login + best-effort grades fetch with documented expectation that response will typically be `[]` without cookies. Still validates HTTP plumbing, Content-Type, ExtJS-preprocessor compatibility — sufficient as a tripwire."
  - "Multi-CI-env-var detection (not just CI / GITHUB_ACTIONS) — added GITLAB_CI + JENKINS_URL guards to harden against future migration off GitHub Actions. Deviation from plan's example (which only listed CI + GITHUB_ACTIONS); accepted because cost is zero and benefit is forward-protection."
  - "SHA-1 password hashing via python3 -c reading os.environ — Plan example used --data-urlencode `password=$AVERS_PASSWORD` directly (visible in `ps -ef`). Switched to: read AVERS_PASSWORD from env, python3 -c hashes it to SHA-1 hex, --data-urlencode receives only the hex digest (per real АВЕРС /login contract: form `p=<sha1_hex>`, not plain-text). Matches Plan 02-02 capture script's hash logic + improves T-08-02 mitigation (only hash visible in argv, not original password)."
  - "Bad-creds detection via grep for [error_symbol] — АВЕРС returns text literal `[error_symbol]` (Plan 02-02 SUMMARY) for failed login, not HTTP 401. Smoke-test treats this as exit 2 (auth failure) so user sees `Verify AVERS_LOGIN / AVERS_PASSWORD in .env.local` instead of confusing 200-PASS message."

requirements-completed: [SUCCESS-2, SUCCESS-5, D-02, D-05, D-09, D-12, D-23, D-24, D-27, D-28]

# Metrics
duration: ~9min
completed: 2026-04-29
---

# Phase 02 Plan 08: API contract docs + dev-host smoke summary

**Authors `docs/aversApiV4_23813.md` (canonical АВЕРС v4.1 build 23813 contract narrative — ROADMAP Phase 2 success #2 deliverable), seeds `docs/aversApiV4_23813-CHANGELOG.md` (versioned-API diff journal), and ships `tools/manual-smoke.sh` + README (dev-host-only live smoke; refuses CI per D-25). Closes the documentation gap left by Plans 02-02/06/07 and explicitly addresses D-05 (passive anti-bot doc) + D-27 (pagination doc-only).**

## Performance

- **Duration:** ~9 min (8m 56s)
- **Started:** 2026-04-29T12:55:45Z
- **Completed:** 2026-04-29T13:04:41Z (approximate)
- **Tasks:** 3
- **Files created:** 4 (2 docs + 1 bash script + 1 README)
- **Files modified:** 0
- **Plan-level commits:** 3 (one per task) + 1 docs commit (this SUMMARY)

## Accomplishments

- **`docs/aversApiV4_23813.md`** — 558 lines, all 9 required H2 sections (`Login flow` / `Response format` / `Endpoint map` / `Anti-bot signals (D-05)` / `Pagination (D-27)` / `Coverage gaps` / `Error model` / `Logging hygiene` / `Kill-switch`). Cross-references HAR fixtures, AversApi/AversAuthApi suspend fns, Plans 02-02/06/07 SUMMARYs, the canary string `kanareyka_PASSWORD_DO_NOT_LEAK_42`, AversApiError variants (`AntiBotChallenge`, `KillSwitchTriggered`, etc.), `api-config.json`, sanitized fixtures, and Chrome DevTools (D-02).
- **`docs/aversApiV4_23813-CHANGELOG.md`** — 66 lines, single 2026-04-28 build 23813 baseline entry + Format template + workflow checklist for future АВЕРС upgrades (`docs/aversApiV4_NNNNN.md` per build, parallel `core/api-avers-v4-NNNNN/` module).
- **`tools/manual-smoke.sh`** — 257 lines, executable, syntactically valid (`bash -n` passes), refuses CI on any of `$CI` / `$GITHUB_ACTIONS` / `$GITLAB_CI` / `$JENKINS_URL`. Sources `.env.local`, SHA-1-hashes password via `python3 -c` (env-read), `mktemp` cookie jar + body files with `trap` cleanup on EXIT/INT/TERM, masked username, never echoes raw password. Live-mode does login + best-effort grades fetch + ExtJS-array shape sanity via Python regex preprocessor port. 5 exit codes (0 PASS / 1 missing-deps-or-CI / 2 login-fail / 3 grades-fail / 4 anti-bot HTML).
- **`tools/manual-smoke.README.md`** — 167 lines covering: when-to-run, when-NOT-to-run, prerequisites, setup, usage, expected output, exit codes table, security & D-28 compliance, CI-relationship matrix, endpoint-path-update workflow, why-bash-not-Kotlin rationale, troubleshooting.
- **CI prohibition verified at runtime** — `CI=1 bash tools/manual-smoke.sh --dry` exits 1 with explicit refusal message naming all detected CI env-vars.
- **Sanitize-har-canary stays green** — `bash tests/sanitize-har-canary.sh` PASSes after this plan; no real PII leaked into the public doc (uses `Иванов И.И.` / `Петров П.П.` / `Сидоров С.С.` from `tools/sanitize-har.py` NAME_POOL in worked examples).
- **No module sources touched** — `git diff HEAD~3 HEAD -- 'composeApp/**' 'core/**/*.kt' 'core/**/*.kts' 'build-logic/**'` is empty. Plan boundary respected (Plan 02-08 is documentation + tooling only).
- **STATE.md / ROADMAP.md / REQUIREMENTS.md unchanged** — orchestrator owns those after wave merge.

## File map

| File | Role | Size |
|------|------|------|
| `docs/aversApiV4_23813.md` | Canonical API-contract narrative for build 23813 | 558 lines |
| `docs/aversApiV4_23813-CHANGELOG.md` | Versioned-API diff journal (single baseline entry) | 66 lines |
| `tools/manual-smoke.sh` | Dev-host live smoke — bash + curl + python3 | 257 lines (~11 KB), executable |
| `tools/manual-smoke.README.md` | Usage guide + security policy + troubleshooting | 167 lines |

## API contract narrative (docs/aversApiV4_23813.md) — section walkthrough

1. **Базовые координаты** — host / schema / charset / build / capture date / server / ExtJS engine table.
2. **Login flow** — auth mechanism (client-set ys-* cookies, server NEVER Set-Cookie), 3 cookies table with sanitized example values, JS-escape() encoding-gotcha section, worked example referencing `fixtures/sanitized/account-A/login.har`, cross-account proof.
3. **Response format** — direct JsonArray dominant pattern, AversEnvelope.unwrap behavior table, NOT-strict-JSON `new Date(...)` literals + ExtJsArrayPreprocessor regex + worked example with sanitized fakes (Иванов И.И., Петров П.П., Сидоров С.С.), strict-but-tolerant parsing rules (D-09), Content-Type quirks.
4. **Endpoint map** — 8-row table (login + auth + logout + 5 reads) with HTTP / path / params / DTO / suspend fn columns; Bootstrap chain (B1-B5) for Phase 4; fixture-to-endpoint matrix; cross-account proof table; DTO column-shape table.
5. **Anti-bot signals (D-05 — passive)** — cookies-table + headers-table; explicit negative findings (no anti-CAPTCHA / no CSRF / no 429 / no Retry-After observed in 28 captures); defense-in-depth via `AntiBotDetector` documented; baseline rate values from convention (1 login per 5s).
6. **Pagination (D-27 — document-only)** — explicit "no `start`/`limit`/`page`/`size` params observed"; canonical full-fetch pattern locked-in; future paging deferred to Phase 4 if needed.
7. **Coverage gaps (Phase 5 deferred)** — 5-row table: итоговые оценки / история по периодам / замены / файлы ДЗ / веса оценок with rationale.
8. **Error model** — AversApiError × 6 variants → UI handling table; ApiResult sealed shape; never-throw boundary contract.
9. **Logging hygiene (D-28)** — 7-key sensitive-keys list; sanitizeHeader rule; canary `kanareyka_PASSWORD_DO_NOT_LEAK_42` + CI gate `tests/log-redactor-canary.sh`.
10. **Kill-switch (D-12/13/14)** — schema verbatim, fields table, fail-open semantics, cadence, T-02-38 hardening rule.
11. **Manual smoke (dev-host only)** — D-25 reference, link to `tools/manual-smoke.sh` + README.
12. **References** — links to ROADMAP, CONTEXT, RESEARCH, three SUMMARYs (02-02/06/07), all 12 fixtures, the `:core:api-avers-v4` module, the capture/sanitize tooling, manual-smoke.

## Manual-smoke security gates

| Gate | Mechanism | Verified by |
|------|-----------|-------------|
| Refuses CI | `if [[ -n "${CI:-}" \|\| -n "${GITHUB_ACTIONS:-}" \|\| -n "${GITLAB_CI:-}" \|\| -n "${JENKINS_URL:-}" ]]; then exit 1; fi` (first action after shebang + set -euo pipefail) | Manual `CI=1 bash tools/manual-smoke.sh --dry` → exit 1 with refusal message |
| Never echoes password | Password read from `os.environ` in `python3 -c`; SHA-1 hex digest is the only thing passed to curl `--data-urlencode`. No `echo $AVERS_PASSWORD` anywhere | `grep -E -c 'echo[[:space:]].*\$AVERS_PASSWORD' tools/manual-smoke.sh` → 0 |
| Username masked | `LOGIN_HEAD="${AVERS_LOGIN:0:3}***"` | Visual inspection of pre-flight summary block |
| Cookie jar tmpfile | `COOKIE_JAR=$(mktemp)` with `trap 'rm -f ...' EXIT INT TERM` | `grep mktemp` → 6 occurrences; `grep 'trap.*rm'` → 1 |
| Honors `.gitignore` | `.env.local` is in repo `.gitignore` (Phase 1 D-04 invariant) | `git check-ignore .env.local` → returns path (gitignored) |
| .env.local missing → exit 1 | Explicit `if [[ ! -f .env.local ]]` guard | Manually delete `.env.local` and re-run → exits 1 with setup hint |

## Threat coverage

Plan's `<threat_model>` T-08-01 through T-08-07 — all addressed:

| Threat | Status | Mitigation |
|--------|--------|------------|
| T-08-01 (doc commits real session cookie / non-redacted endpoint sample) | Mitigated | Only `Иванов И.И.` / `Петров П.П.` / `Сидоров С.С.` (NAME_POOL) used in worked examples; sanitize-har-canary regression-protects fixtures referenced by the doc; verify-block enforces no `{login_path}`/`{path-from-HAR}`/`{X-* header}`/`{имя cookie}` placeholders left unfilled. |
| T-08-02 (manual-smoke leaks password to ps / shell history) | Mitigated (improved over plan's example) | (1) Password SHA-1-hashed in `python3 -c` reading `os.environ` — only the SHA-1 hex enters curl argv (matches actual АВЕРС /login contract `p=<sha1_hex>`). (2) Username masked first 3 chars + `***`. (3) Cookie jar mktemp + trap cleanup. Residual risk: SHA-1 hex visible in ps for the duration of the curl call — accepted (single-user dev-host, not the original password). |
| T-08-03 (`.env.local` accidentally committed) | Mitigated | Phase 1 added `.env.local` to `.gitignore`; README §Setup explicitly tells developer "gitignored, never commit"; `git check-ignore` works. |
| T-08-04 (Public GitHub Pages doc readable) | Accepted (D-19 PROJECT decision applies) | Endpoints already appear in HAR fixtures and the AVERS web client; doc just narrates publicly-visible information. Risk = same as Phase 1 Privacy Policy publication. |
| T-08-05 (Frequent manual-smoke runs trigger anti-bot) | Mitigated | README §When NOT to run forbids cron / loop usage; CI env-var detection; D-05 passive-observation cited. |
| T-08-06 (Doc claims AVERS shape but is wrong / drifted) | Mitigated | Acceptance criteria enforced no `{...}` placeholders left; doc was authored against actual HAR fixtures + verified Kotlin AversEndpoints constants. Future drift caught by manual-smoke exit 3 (envelope changed) or fixture re-capture. |
| T-08-07 (CI-detection bypass — custom runner without standard env-vars) | Accepted (improved) | Multi-env-var detection (CI / GITHUB_ACTIONS / GITLAB_CI / JENKINS_URL) — better than plan's CI + GITHUB_ACTIONS only. Residual risk = self-hosted runner without those vars. Out of scope for Phase 2 (no self-hosted runners — only ubuntu-latest + macos-15). |

## Task Commits

Each task was committed atomically (worktree mode, `git commit --no-verify`):

1. **Task 1: docs/aversApiV4_23813.md** — `f4711ab` (docs)
2. **Task 2: docs/aversApiV4_23813-CHANGELOG.md** — `f74e6d0` (docs)
3. **Task 3: tools/manual-smoke.sh + README** — `e2b7f13` (feat)

Plan metadata commit (this SUMMARY.md) follows after self-check.

## Decisions Made

See `key-decisions:` block in frontmatter. Highlights:

- **Direct-array response format** — Plan template offered three options (envelope / direct / mix); Plans 02-02 + 02-06 reality is uniformly direct `JsonArray` for all 6 endpoints. Doc records this as dominant pattern + AversEnvelope.unwrap forward-compat.
- **Multi-CI-env-var detection in manual-smoke** — extended plan's `CI` + `GITHUB_ACTIONS` to also cover `GITLAB_CI` + `JENKINS_URL`. Zero cost, future-proofs against migration off GitHub Actions.
- **SHA-1 password hashing in manual-smoke** — improves over plan's example (which would have put plain-text password in curl argv). Now only SHA-1 hex digest enters argv; matches actual АВЕРС /login form contract; mitigates T-08-02 better than plan-level mitigation.
- **Bad-creds detection via `[error_symbol]` grep** — Plan didn't anticipate that АВЕРС returns text literal (not HTTP 401) for failed creds. Smoke-test handles this explicitly so exit code 2 + "Verify AVERS_LOGIN / AVERS_PASSWORD" message surfaces correctly.
- **Doc references SHA-1 password hashing in §Login flow** — doc's worked example shows `l=REDACTED&p=REDACTED` and explains the SHA-1-hex-of-password convention so future maintainers don't try plain-text.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical functionality] SHA-1 password hashing in manual-smoke**
- **Found during:** Task 3 source authoring (cross-checking `tools/capture-avers-fixtures.py` + Plan 02-02 SUMMARY)
- **Issue:** Plan's example for manual-smoke.sh used `--data-urlencode "password=$AVERS_PASSWORD"` — but АВЕРС `/login` expects `l=<login>&p=<sha1_hex>` (Plan 02-02 SUMMARY: "POST /login form l=<login>&p=<sha1_hex_of_password>"). Plain-text password in form body would just return `[error_symbol]` (auth fail).
- **Fix:** Added SHA-1 hashing step via `python3 -c` reading `os.environ.get("AVERS_PASSWORD")` and writing hex digest to stdout. Curl `--data-urlencode "p=${SHA1_PASSWORD}"` — only hex digest in argv. Matches actual АВЕРС contract + improves T-08-02 mitigation (plain-text password never enters argv).
- **Files modified:** `tools/manual-smoke.sh`
- **Verification:** Script passes bash syntax check; SHA-1 logic exercised in dry-run (no actual HTTP).
- **Committed in:** `e2b7f13` (Task 3 commit).

**2. [Rule 2 - Missing critical functionality] Multi-CI-env-var detection**
- **Found during:** Task 3 source authoring (defense-in-depth review of CI prohibition)
- **Issue:** Plan example only checked `CI` + `GITHUB_ACTIONS`. Self-hosted GitLab CI runner (`GITLAB_CI`) or Jenkins runner (`JENKINS_URL`) wouldn't be caught.
- **Fix:** Extended detection to all 4 standard CI env-vars: `CI`, `GITHUB_ACTIONS`, `GITLAB_CI`, `JENKINS_URL`. Refusal message names all detected vars for diagnostics.
- **Files modified:** `tools/manual-smoke.sh`
- **Verification:** `CI=1 bash tools/manual-smoke.sh --dry` exits 1; verify block from plan still passes.
- **Committed in:** `e2b7f13` (Task 3 commit).

**3. [Rule 1 - Bug] АВЕРС returns text-literal `[error_symbol]` for bad creds, not HTTP 401**
- **Found during:** Task 3 source authoring (cross-checking Plan 02-02 SUMMARY observation: "failed login (bad creds) returns `[error_symbol]` text literal")
- **Issue:** Plan example treated 200 with empty body as PASS. But АВЕРС returns HTTP 200 + `[error_symbol]` body for auth failure — script would have reported PASS for wrong creds.
- **Fix:** Added `grep -q 'error_symbol' "$LOGIN_BODY"` check after HTTP 200 confirmation; exits 2 with "credentials rejected" message.
- **Files modified:** `tools/manual-smoke.sh`
- **Verification:** Logic-only fix; not exercised in dry-run, but correct per Plan 02-02 SUMMARY auth observation.
- **Committed in:** `e2b7f13` (Task 3 commit).

**4. [Rule 2 - Missing critical functionality] Best-effort grades note documenting cookie limitation**
- **Found during:** Task 3 source authoring
- **Issue:** Plan example expected manual-smoke to do a full grades fetch — but full fetch requires JS-escape() polyfill + ys-* cookie injection (Phase 4 scope per Plan 02-02 SUMMARY + Plan 02-06 SUMMARY). Smoke-test would have falsely failed every run with "grades returned []" without explanation.
- **Fix:** Script POSTs grades with `cls=0&student=0` (deliberately invalid scope); shape-validates response (typically `[]` without ys-* cookies but still parses as JsonArray); README + script comments explain that full handshake is Phase 4 scope. Validates HTTP plumbing + Content-Type + ExtJS preprocessor compatibility — sufficient as tripwire.
- **Files modified:** `tools/manual-smoke.sh`, `tools/manual-smoke.README.md`
- **Verification:** Verify-block intact; doc consistent with Plan 02-02/06 reality.
- **Committed in:** `e2b7f13` (Task 3 commit).

---

**Total deviations:** 4 (1 × Rule 1, 3 × Rule 2). All within plan boundary — no architectural changes, no module sources touched, no STATE/ROADMAP edits. Each deviation is forward-compatible: future АВЕРС contract changes update `docs/aversApiV4_NNNNN.md` + script env-var overrides; bad-creds detection generalizes to any text-literal failure mode.

## Authentication Gates

None — no external service authentication required for Plan 02-08 itself. The manual-smoke script exercises АВЕРС auth at the developer's discretion on dev-host; this is documented as an explicit dev-only loop, not an authentication gate for the executor.

## Issues Encountered

- **Plan example used plain-text password in curl argv** — fixed via SHA-1 hashing through `python3 -c` env-read (Deviation #1). Cross-checked against Plan 02-02 capture-script's `js_escape()` + SHA-1 hashing pattern.
- **Plan example didn't anticipate `[error_symbol]` text-literal failure mode** — fixed via grep guard (Deviation #3).
- **Doc length budget** — final 558 lines, well above 150-line minimum. Each section earns its keep with at least one table or worked example.
- **No Kotlin / Gradle build run** — by design (no module sources touched). Sanitize-har-canary regression check serves as the only build-level verification (PASSes).

## Threat Surface Scan

No new threat surface beyond `<threat_model>` (T-08-01 through T-08-07) — all addressed in implementation. The doc is publishable to the same GitHub Pages CDN that already serves Privacy Policy + api-config.json (no new attack surface). Manual-smoke script is dev-host-only (multi-CI-env-var refusal) and only opens connections to journal.school28-kirov.ru on dev-host invocation. No new external endpoints, no new schema changes at trust boundaries.

## Known Stubs

None — all four files deliver working production content. The doc's §Coverage gaps section explicitly enumerates Phase 5 deferred items (итоговые оценки / история / замены / файлы / веса) so future maintainers aren't surprised by gaps; this is documented intent, not a stub.

`AttendanceDto` and `MessageDto` column shapes in §Endpoint map are noted as "placeholder" because the captured fixtures return `[]` for student role — but this is upstream Plan 02-06 status, faithfully documented here, not introduced by Plan 02-08.

## User Setup Required

None for Plan 02-08 deployment — `pages.yml` will auto-deploy `docs/aversApiV4_23813.md` + `docs/aversApiV4_23813-CHANGELOG.md` after the wave merges to `main`. The maintainer can verify by visiting `https://chudoxl.github.io/LintehJournal/aversApiV4_23813.md` (or `.../aversApiV4_23813-CHANGELOG.md`) once the workflow has run.

For dev-host smoke usage: see `tools/manual-smoke.README.md` §Setup. Requires `.env.local` with `AVERS_LOGIN` + `AVERS_PASSWORD`.

## Next Phase Readiness

- **Plan 02-09 (final integration smoke / iosTest variant of EndpointsContractTest):** independent of Plan 02-08. The manual-smoke tooling is dev-only and does not affect Plan 02-09's CI runtime work.
- **Phase 3 Auth UI:** doc §Login flow + §Anti-bot signals + §Error model give the implementation context needed for the WebView CAPTCHA fallback (D-06). `AversApiError.AntiBotChallenge.rawHtml` is the WebView payload. Phase 3 author can reference `docs/aversApiV4_23813.md` directly without grepping HAR files.
- **Phase 4 :core:data:** doc §Endpoint map + §Bootstrap chain (B1-B5) + DTO column-shapes table covers the full contract surface. Phase 4 must implement: JS-escape() polyfill in `:core:platform`, SHA-1 password hashing helper, /auth two-step completion, mapper DTO→domain. All referenced from the doc.
- **Future АВЕРС upgrades (build 23901+):** the changelog Format template + workflow checklist define the parallel-module + parallel-doc invariant. The first upgrade triggers: capture → new module → new doc → changelog entry → api-config.json bump after rollout.
- **No blockers for the wave merge.**

## Self-Check: PASSED

Verified before signing off:

```bash
# Created files (4 files)
[ -f docs/aversApiV4_23813.md ]                                  # → exists, 558 lines
[ -f docs/aversApiV4_23813-CHANGELOG.md ]                        # → exists, 66 lines
[ -f tools/manual-smoke.sh ]                                     # → exists, executable, syntax OK
[ -f tools/manual-smoke.README.md ]                              # → exists, 167 lines

# Doc artifact integrity
[ "$(wc -l < docs/aversApiV4_23813.md)" -ge 150 ]                # → 558 ≥ 150
grep -c -E '^## (Login flow|Response format|Endpoint map|Anti-bot signals|Pagination|Coverage gaps|Error model|Logging hygiene|Kill-switch)' docs/aversApiV4_23813.md  # → 9
grep -q 'kanareyka_PASSWORD_DO_NOT_LEAK_42' docs/aversApiV4_23813.md   # → 2 occurrences
grep -q 'AntiBotChallenge' docs/aversApiV4_23813.md                    # → 3 occurrences
grep -q 'KillSwitchTriggered' docs/aversApiV4_23813.md                 # → 2 occurrences
grep -q 'api-config.json' docs/aversApiV4_23813.md                     # → 2 occurrences
grep -q 'fixtures/sanitized/' docs/aversApiV4_23813.md                 # → 11 occurrences
grep -q 'Chrome DevTools' docs/aversApiV4_23813.md                     # → 2 occurrences
grep -q 'build 23813' docs/aversApiV4_23813.md                         # → 1 occurrence
grep -E -c '\{login_path\}|\{path-from-HAR\}|\{X-\* header\}|\{имя cookie\}' docs/aversApiV4_23813.md  # → 0 (no placeholders left)

# Changelog seeded
grep -q '^## 2026-04-28 — build 23813' docs/aversApiV4_23813-CHANGELOG.md  # → present

# Manual-smoke security gates
test -x tools/manual-smoke.sh                                            # → executable
bash -n tools/manual-smoke.sh                                            # → syntax OK
grep -q 'GITHUB_ACTIONS' tools/manual-smoke.sh                           # → 2 occurrences
grep -q '\.env\.local' tools/manual-smoke.sh                             # → 11 occurrences
grep -E -c 'echo[[:space:]].*\$AVERS_PASSWORD|echo[[:space:]].*\$\{AVERS_PASSWORD' tools/manual-smoke.sh  # → 0 (no leak)
CI=1 bash tools/manual-smoke.sh --dry                                    # → exit 1 (refused, OK)

# README content
[ "$(wc -l < tools/manual-smoke.README.md)" -ge 60 ]                     # → 167 ≥ 60
grep -q 'D-25' tools/manual-smoke.README.md                              # → 2 occurrences
grep -q '\.env\.local' tools/manual-smoke.README.md                      # → 8 occurrences

# Workflow chain intact
grep -q "'docs/\\*\\*'" .github/workflows/pages.yml                      # → present (auto-deploy)

# Sanitize-har-canary regression
bash tests/sanitize-har-canary.sh                                        # → PASS

# Boundary respect
git diff --name-only HEAD~3 HEAD -- 'composeApp/**' 'core/**/*.kt' 'core/**/*.kts' 'build-logic/**'  # → empty
git diff --name-only HEAD~3 HEAD -- '.planning/STATE.md' '.planning/ROADMAP.md' '.planning/REQUIREMENTS.md'  # → empty

# Commits
git log --oneline | grep -q f4711ab && echo COMMIT-1-FOUND               # → COMMIT-1-FOUND
git log --oneline | grep -q f74e6d0 && echo COMMIT-2-FOUND               # → COMMIT-2-FOUND
git log --oneline | grep -q e2b7f13 && echo COMMIT-3-FOUND               # → COMMIT-3-FOUND
```

All expected outputs returned. Worktree boundaries respected — zero edits to:
- `.planning/STATE.md`
- `.planning/ROADMAP.md`
- `.planning/REQUIREMENTS.md`
- `composeApp/**` Kotlin sources
- `core/**/*.kt` / `core/**/*.kts`
- `build-logic/**`

---

*Phase: 02-api-reverse-engineering-network-layer*
*Plan: 08*
*Completed: 2026-04-29*
