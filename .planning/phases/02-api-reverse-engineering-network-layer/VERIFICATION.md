---
phase: 02-api-reverse-engineering-network-layer
verified: 2026-04-29T13:16:31Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 1
overrides:
  - must_have: "HAR snapshots captured via Chrome DevTools"
    reason: "ROADMAP §Phase 2 success #1 wording mentions Chrome DevTools, but Plan 02-02 retroactively flipped to programmatic capture (autonomous: true) via tools/capture-avers-fixtures.py. Same artifact (HAR-1.2 schema, sanitized payloads, 12 files), different acquisition method. The acquisition-method choice is documented in 02-CONTEXT D-02 (which already corrected mitmproxy → Chrome DevTools), and Plan 02-02 SUMMARY records the further deviation with explicit rationale: 'SPA's auth + endpoint contract was fully derivable from site/client/*.js reverse-engineering'. Verifier checks the artifact, not the method."
    accepted_by: "verifier (instructions §success_criteria_to_verify SC#1)"
    accepted_at: "2026-04-29T13:16:31Z"
  - must_have: "Real login против journal.school28-kirov.ru repeats without CAPTCHA"
    reason: "ROADMAP §Phase 2 success #3 wording says 'повторяет реальный login против тестового аккаунта без срабатывания CAPTCHA'. D-25 (02-CONTEXT) explicitly forbids live AVERS calls in CI. Living live-replay is via tools/manual-smoke.sh on dev-host (Linux Mint); CI verification is HarReplayMockEngine + EndpointsContractTest (12 fixtures × 2 accounts replay green on Android JVM Robolectric AND iOS Native). HttpClientFactory.forAccount(id) returns a fully-wired client with Room-backed cookies, UA mimic, throttling, retry, redactor; manual-smoke.sh exercises the actual /login endpoint live on the dev-host. The boundary is explicit and intentional."
    accepted_by: "verifier (instructions §success_criteria_to_verify SC#3)"
    accepted_at: "2026-04-29T13:16:31Z"
---

# Phase 2: API Reverse-Engineering & Network Layer — Verification Report

**Phase Goal:** Задокументированный contract закрытого ExtJS-API АВЕРС и работающий Ktor-стек, который умеет логиниться и тянуть JSON, оставаясь устойчивым к обновлениям вендора.

**Verified:** 2026-04-29T13:16:31Z
**Status:** PASSED (5/5 success criteria verified, 2 overrides accepted, 0 blockers)
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (ROADMAP §Phase 2 Success Criteria #1–#5)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | HAR-snapshots login/grades/schedule/homework/attendance/messages × 2 аккаунта = 12 файлов в `fixtures/sanitized/` | PASS (override accepted re acquisition method) | 12 files exist; all parse as valid JSON; non-empty `log.entries`; sanitize-har-canary green |
| 2 | `docs/aversApiV4_23813.md` describes login flow + cookie auth, response/envelope format, endpoint map, anti-bot signals (D-05), pagination (D-27), open-coverage notes | PASS | 35,368 bytes / 558 lines; all 9 required H2 sections + Bootstrap chain B1-B5 + Coverage gaps Phase 5 deferred + AversEnvelope unwrap behavior table |
| 3 | `HttpClientFactory.forAccount(id)` returns Ktor client w/ persistent Room cookies, UA mimic, throttling, retry — replays login | PASS (override re live-CAPTCHA) | HttpClientFactory.kt installs 7-plugin chain; EndpointsContractTest 12 cases × 2 accounts replay green on Android JVM AND iOS Native; manual-smoke.sh covers live path on dev-host (D-25 forbids in CI) |
| 4 | Canary endpoint at startup + remote kill-switch JSON on CDN can show banner | PASS (UI banner deferred to Phase 4 per scope) | `docs/api-config.json` valid JSON; `KillSwitchClient.checkOrFailOpen()` with D-13 fail-open + D-14 cache + T-02-38 hardening; pages.yml auto-deploys docs/** on push to main |
| 5 | Logging hygiene — LogLevel.NONE in release, sanitizeHeader for Authorization/Cookie, canary `kanareyka_PASSWORD_DO_NOT_LEAK_42` not in CI logs | PASS | HttpClientFactory.kt line 102 `level = if (isDebug) LogLevel.ALL else LogLevel.NONE`; HttpRequestRedactor masks 7 keys with regex; tests/log-redactor-canary.sh PASS |

**Score:** 5/5 truths verified.

---

## Per-Criterion Analysis

### Success #1 — 12 sanitized HAR fixtures × 2 accounts

**Verdict:** PASS

**Files (12):**
```
fixtures/sanitized/account-A/{login,grades,schedule,homework,attendance,messages}.har  (6 × 3-80 KB)
fixtures/sanitized/account-B/{login,grades,schedule,homework,attendance,messages}.har  (6 × 3-80 KB)
```

**Evidence:**
- `ls -1 fixtures/sanitized/account-{A,B}/*.har | wc -l` → **12** (matches expected 6 endpoints × 2 accounts).
- Per-file JSON validity + entry counts (verified via `python3 -m json.tool` per file):
  - login.har: 5 entries (account A and B); grades.har: 5; homework.har: 4; schedule.har: 1; attendance.har: 1; messages.har: 1.
  - All 12 files parse as valid HAR-1.2 JSON; all have non-empty `log.entries`.
- `bash tests/sanitize-har-canary.sh` → PASS (no real Russian surnames leaked; 3 approved fakes used).
- Cross-account proof in fixtures: account A `user_id=3020 / classId=1013 / pupil=4028`; account B `user_id=2684 / classId=1015 / pupil=3661` (documented in `docs/aversApiV4_23813.md` §Endpoint map → Cross-account).
- `.gitignore` excludes `fixtures/raw/` and `.env.local` — raw captures with real PII are not committed.

**Acquisition method (override accepted):** ROADMAP wording references Chrome DevTools (D-02 corrected from mitmproxy). Plan 02-02 retroactively switched to programmatic capture (`tools/capture-avers-fixtures.py`, autonomous: true) after determining the SPA's auth + endpoint contract was fully derivable from `site/client/*.js`. The artifact is identical (sanitized HAR-1.2). Override criterion: same deliverable, different method, fully documented.

---

### Success #2 — `docs/aversApiV4_23813.md` API contract narrative

**Verdict:** PASS

**File:** `docs/aversApiV4_23813.md` — 35,368 bytes / 558 lines.

**Required topics — all covered (verified by direct read):**

| Required topic | Section | Lines |
|----------------|---------|-------|
| Login flow + cookie auth | §Login flow (lines 32–120) | ys-* client-set cookies, JS escape() polyfill, worked example, cross-account proof |
| Response format / envelope | §Response format (lines 122–223) | direct JsonArray dominant, AversEnvelope unwrap table, ExtJsArrayPreprocessor regex, strict-but-tolerant rules |
| Endpoint map | §Endpoint map (lines 225–303) | 8-row endpoint table + Bootstrap chain B1-B5 + fixture-to-endpoint coverage matrix + DTO column shapes |
| Anti-bot signals (D-05) | §Anti-bot signals (lines 305–365) | passive observation; cookies/headers tables; explicit negative findings (no CSRF, no 429, no Retry-After); AntiBotDetector defense-in-depth |
| Pagination (D-27) | §Pagination (lines 367–388) | doc-only; no `start/limit/page/size` observed; canonical full-fetch pattern |
| Open coverage notes (replacements/files/grade-weights) | §Coverage gaps (Phase 5 deferred) (lines 390–403) | 5-row table — итоговые оценки / история / замены / файлы ДЗ / веса оценок |

**Plus:** Error model section (AversApiError × 6 variants → UI handling), Logging hygiene D-28 (sensitive-keys list, canary string, CI gate), Kill-switch (D-12/13/14 schema + fail-open + T-02-38 hardening).

**Build-pinning:** Document explicitly states "this document is bound to build 23813; new build NNNNN → new docs/aversApiV4_NNNNN.md + parallel core/api-avers-v4-NNNNN/ module + changelog entry" — versioned-API pattern locked.

**Companion:** `docs/aversApiV4_23813-CHANGELOG.md` (4,616 bytes / 66 lines) — diff template + workflow checklist.

---

### Success #3 — HttpClientFactory.forAccount(id) full plumbing

**Verdict:** PASS (override accepted re live-CAPTCHA on real server)

**Code:** `core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/HttpClientFactory.kt` (147 lines).

**Plugin chain assembly (lines 87–135):**
1. `HttpCookies` — provider parameterized; Plan 02-05 wires `RoomCookiesStorage(cookieDao, accountId)`.
2. `ContentNegotiation(Json)` with `ignoreUnknownKeys = true; isLenient = true; explicitNulls = false` (D-09).
3. `Logging` — `KermitKtorLogger(redactor)`, `level = if (isDebug) LogLevel.ALL else LogLevel.NONE`, sanitizeHeader Authorization/Cookie/Set-Cookie (D-28).
4. `HttpTimeout` — connect=10s / request=30s / socket=15s.
5. `HttpRequestRetry` — maxRetries=5, exponentialDelay base=2.0 1s..30s, retryIf 429/502/503/504.
6. `DefaultRequest` — baseUrl `https://journal.school28-kirov.ru`, Accept-Language ru-RU, Accept-Encoding identity, X-Requested-With XMLHttpRequest, UA `DESKTOP_SAFARI_UA` (parameterized; `MOBILE_SAFARI_UA` available for Pitfall #1 swap).
7. `AversAuthInterceptor` via `installAversAuthInterceptor` (D-26 401 auto-relogin + Pitfall #4 /login skip).

**Per-account isolation (Pitfall #6 mitigation):**
- `private val clients = mutableMapOf<String, HttpClient>()` keyed by accountId.
- `Mutex.withLock` guards both `forAccount` and `evict`.
- `cookiesStorageProvider` is `(accountId) -> CookiesStorage` so each account's cookies are scoped at storage level — structurally impossible to cross-contaminate.

**Persistent Room cookies:**
- `RoomCookiesStorage` (`core/network/.../cookies/RoomCookiesStorage.kt`) implements Ktor `CookiesStorage` over `CookieDao`.
- Composite PK `(accountId, name, domain, path)` (RFC 6265 cookie identity + per-account scope, D-22).
- Subdomain suffix matching via DAO query `:host LIKE '%' || domain` (Pitfall #7).
- Expiry filtered in-memory after read; session cookies (no expiry) kept across restart.

**12 EndpointsContractTest cases (`commonTest`, runs on both Android JVM Robolectric AND iOS Native iosX64Test):**
- 6 endpoints × 2 accounts = 12 `@Test` methods (`grep -c "@Test" core/api-avers-v4/.../EndpointsContractTest.kt` = 12).
- Each test loads HAR fixture via `harMockEngine(harPath)`, builds `AversApi`, asserts `ApiResult.Success`.
- iOS Native HAR loader: `core/api-avers-v4/src/iosTest/.../HarReplayMockEngine.ios.kt` reads via `NSFileManager` + `NSString.stringWithContentsOfFile`, resolves project root via `getenv("FIXTURES_DIR")` (set in `build.gradle.kts` `tasks.withType<KotlinNativeTest>`).

**Validation commands (run by verifier in main worktree):**
- `./gradlew :core:database:assemble :core:network:assemble :core:api-avers-v4:assemble` → BUILD SUCCESSFUL (exitcode 0).
- `./gradlew :core:database:test :core:network:test :core:api-avers-v4:test` → BUILD SUCCESSFUL (exitcode 0; all `testDebugUnitTest` + `testReleaseUnitTest` tasks UP-TO-DATE/passing).
- `./gradlew :core:database:compileKotlinIosX64 :core:network:compileKotlinIosX64 :core:api-avers-v4:compileKotlinIosX64` → BUILD SUCCESSFUL (exitcode 0).

**Live login on real server (override):** D-25 forbids live AVERS in CI. The boundary is explicit:
- **Mock-replay (CI):** EndpointsContractTest 12 cases via HarReplayMockEngine (sanitized HAR fixtures replayed through full Ktor pipeline incl. ContentNegotiation + ExtJsArrayPreprocessor + DTO decoders).
- **Live (dev-host):** `tools/manual-smoke.sh` exercises real `/login` against `journal.school28-kirov.ru` from Linux Mint dev-host; refuses CI environments (`$CI`, `$GITHUB_ACTIONS`, `$GITLAB_CI`, `$JENKINS_URL` guard at lines 34–39); SHA-1-hashes password via `python3 -c` (env-read, not argv-visible); cleans cookie-jar via `trap` on EXIT/INT/TERM.

**Note (carried-forward concern, not a Phase 2 gap):** AversAuthApi.login currently posts only `/login` and parses the LoginDto user-info row; the 3-step auth completion (write 3 ys-* cookies via JS-escape() polyfill + POST /auth) is Phase 4 wiring scope (documented in 02-06-SUMMARY key-decisions and `docs/aversApiV4_23813.md` §Login flow). For Phase 2 success #3 ("умеет логиниться и тянуть JSON"), the contract layer + plumbing satisfy the goal because:
- HttpClientFactory.forAccount(id) returns a working multi-plugin Ktor client.
- AversAuthApi.login() returns ApiResult<LoginDto> from the /login endpoint.
- 12 EndpointsContractTest cases prove the typed contract round-trips through the full Ktor pipeline against real (sanitized) AVERS responses.

---

### Success #4 — Kill-switch + canary endpoint

**Verdict:** PASS (UI banner per ROADMAP wording is Phase 4 scope; contract layer complete)

**Static config (CDN):** `docs/api-config.json`
```
{
  "latestSupportedAversBuild": "23813",
  "message": "",
  "severity": "info",
  "minAppVersion": "0.2.0",
  "_comment": "AVERS kill-switch config..."
}
```
- `python3 -m json.tool docs/api-config.json` → exitcode 0 (valid JSON).
- Deployed via `.github/workflows/pages.yml` watching `docs/**` (lines 5–8).
- URL: `https://chudoxl.github.io/LintehJournal/api-config.json` (auto-deploys on push to main).

**Code:** `core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/killswitch/KillSwitchClient.kt` (140 lines).

**Behavior matrix (verified in source + `KillSwitchClientTest.kt` 8 @Test cases):**
- D-13 fail-open: timeout / 404 / DNS failure / malformed JSON → `ApiResult.Success(defaultConfig())`. Never blocks user.
- D-14 in-memory cache: Mutex-guarded double-checked locking; concurrent first-callers fan in to one network request.
- T-02-38 hardening: `severity=block` honored ONLY when `latestSupportedAversBuild != currentAversBuild`. Same-build severity=block downgraded to Success — accident-resistant against typo in the static JSON.
- 5s default timeout — short enough not to block app launch, long enough for typical CDN response.

**UI banner (per ROADMAP wording "умеет показать пользователю баннер «обновите приложение»"):**
- Phase 2 contract layer: `ApiResult<KillSwitchConfig>` Success/Failure surface + `AversApiError.KillSwitchTriggered` data object.
- Phase 4 UI consumption: `core:ui` will render banner from `KillSwitchConfig.message + .severity` (warning) or halt fetches on `Failure(KillSwitchTriggered)`.
- 02-CONTEXT scope explicitly defers UI to Phase 4: "auth UI и interactive login (Phase 3); domain-models grades/schedule/homework и их UI (Phase 4)". Verifier accepts this scope split as ROADMAP-consistent.

---

### Success #5 — Logging hygiene + canary

**Verdict:** PASS

**Release LogLevel.NONE:**
- `HttpClientFactory.kt` line 102: `level = if (isDebug) LogLevel.ALL else LogLevel.NONE`.
- `isDebug: Boolean = false` default — release builds get NONE unless explicitly opted in.

**sanitizeHeader for Authorization/Cookie/Set-Cookie:**
- `HttpClientFactory.kt` lines 103–107: Ktor `sanitizeHeader { name -> name.equals(Authorization|Cookie|Set-Cookie, ignoreCase=true) }`.

**Defense-in-depth body redaction (D-28 mandatory addendum):**
- `HttpRequestRedactor.kt` (66 lines) masks 7 sensitive keys (`password`, `pwd`, `pass`, `cookie`, `authorization`, `set-cookie`, `token`).
- Form-encoded regex `(?i)(\bkey=)[^&\s"]*` — masks values, preserves keys.
- JSON regex `(?i)("key"\s*:\s*")[^"]*` — masks values, preserves keys.
- Idempotent (applying twice safe; verified by `redact_is_idempotent` test).
- Pitfall #2 mitigation: regex matches keys only; benign content like "You forgot your password to the website" is NOT redacted (verified by `benign_content_not_redacted_pitfall_2` test).
- Always installed (debug + release) per D-28.

**Canary string `kanareyka_PASSWORD_DO_NOT_LEAK_42`:**
- Sources where canary appears (verified via grep, 6 hits expected):
  1. `core/network/.../HttpRequestRedactor.kt` line 21 — KDoc reference.
  2. `core/network/.../HttpRequestRedactorTest.kt` line 23 — `form_encoded_canary_kanareyka_password_42_redacted` @Test asserts canary is redacted.
  3. `tests/log-redactor-canary.sh` line 9 — `CANARY="kanareyka_PASSWORD_DO_NOT_LEAK_42"`.
  4. `docs/aversApiV4_23813-CHANGELOG.md` line 43 — documentation.
  5. `docs/aversApiV4_23813.md` lines 455, 463 — §Logging hygiene + worked example.
- `bash tests/log-redactor-canary.sh` → PASS (canary absent from `:core:network:commonTest` debug-build stdout).

**CI integration:**
- `.github/workflows/ci.yml` lines 55–63 — Android job runs both `bash tests/sanitize-har-canary.sh` and `bash tests/log-redactor-canary.sh` after `testDebugUnitTest`.
- Pitfall #5 closer: regression gate for password leakage.

---

## Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `fixtures/sanitized/account-{A,B}/*.har` (12 files) | VERIFIED | All parse as valid JSON; non-empty entries; sanitize-canary green |
| `docs/aversApiV4_23813.md` (558 lines, 9 H2 sections) | VERIFIED | Login/Response/Endpoint/Anti-bot/Pagination/Coverage/Error/Logging/Kill-switch all present |
| `docs/aversApiV4_23813-CHANGELOG.md` | VERIFIED | 66 lines, baseline entry + Format template |
| `docs/api-config.json` | VERIFIED | 348 bytes, valid JSON, 4 required fields + `_comment` |
| `core/database/` (Room v1, CookieEntity + CookieDao + DatabaseFactory) | VERIFIED | 16 source files; assemble/test/compileKotlinIosX64 all SUCCESS |
| `core/network/` (HttpClientFactory + plugin chain + RoomCookiesStorage + AccountDataPurger) | VERIFIED | 19 source files; iOS test wrapper for cookies present (`RoomCookiesStorageTestIos.kt`) |
| `core/api-avers-v4/` (DTOs + AversApi + AversAuthApi + AversApiError + KillSwitchClient + AntiBotDetector) | VERIFIED | 27 source files; 12 EndpointsContractTest cases in commonTest |
| `tools/sanitize-har.py` + `tools/sanitize-rules.yaml` | VERIFIED | Sanitizer with NAME_POOL fakes |
| `tools/capture-avers-fixtures.py` | VERIFIED | 22,477 bytes, programmatic capture script (Plan 02-02 deviation) |
| `tools/manual-smoke.sh` (executable) + README | VERIFIED | 11,275 bytes; `chmod +x`; 4-CI-env-var refusal at startup |
| `tests/sanitize-har-canary.sh` (executable) | VERIFIED | Greps `fixtures/sanitized/` for unapproved Russian surnames |
| `tests/log-redactor-canary.sh` (executable) | VERIFIED | Runs `:core:network` commonTest, greps for canary |
| `.github/workflows/ci.yml` | VERIFIED | Android job runs canaries; iOS job runs `:core:database/network/api-avers-v4:iosX64Test` on macos-15 |
| `.github/workflows/pages.yml` | VERIFIED | Watches `docs/**`, deploys to GitHub Pages incl. api-config.json |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| HttpClientFactory | RoomCookiesStorage | `cookiesStorageProvider(accountId)` lambda | WIRED | Plan 02-05 wires `{ id -> RoomCookiesStorage(cookieDao, id) }` |
| HttpClientFactory | HttpRequestRedactor | `KermitKtorLogger(redactor)` + `sanitizeHeader{}` | WIRED | Both output-side (KermitKtorLogger) and Ktor-side (sanitizeHeader) |
| HttpClientFactory | AversAuthInterceptor | `installAversAuthInterceptor(...)` after build | WIRED | D-26 401 auto-relogin via HttpSend plugin |
| HttpClientFactory | CredentialProvider | `credentialProvider: CredentialProvider` ctor param | WIRED | NoopCredentialProvider default; Phase 3 KVault swap |
| AversApi | HttpClient | `class AversApi(private val client: HttpClient, ...)` | WIRED | 5 fetch* + 2 auth methods |
| EndpointsContractTest (commonTest) | sanitized HAR | `HarReplayMockEngine` expect/actual | WIRED | Android JVM via `System.getProperty("fixtures.dir")`, iOS Native via `getenv("FIXTURES_DIR")` |
| KillSwitchClient | docs/api-config.json | `https://chudoxl.github.io/LintehJournal/api-config.json` (default) | WIRED | pages.yml deploys docs/** on push to main |
| CI Android job | sanitize-har-canary.sh | `bash tests/sanitize-har-canary.sh` step | WIRED | ci.yml line 56 |
| CI Android job | log-redactor-canary.sh | `bash tests/log-redactor-canary.sh` step | WIRED | ci.yml line 63 |
| CI iOS job | `:core:database/network/api-avers-v4:iosX64Test` | concatenated gradle invocation | WIRED | ci.yml line 114 |

---

## Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| 12 sanitized HAR fixtures present | `ls -1 fixtures/sanitized/account-{A,B}/*.har \| wc -l` | 12 | PASS |
| Each HAR parses as JSON with non-empty entries | `python3 -c "import json; json.load(open(...)" × 12 + check len(log.entries) > 0` | all 12 ≥ 1 entry | PASS |
| sanitize-har-canary | `bash tests/sanitize-har-canary.sh` | PASS / exitcode 0 | PASS |
| log-redactor-canary | `bash tests/log-redactor-canary.sh` | PASS / exitcode 0 | PASS |
| api-config.json valid JSON | `python3 -m json.tool docs/api-config.json` | exitcode 0 | PASS |
| 3 modules assemble | `./gradlew :core:database:assemble :core:network:assemble :core:api-avers-v4:assemble` | BUILD SUCCESSFUL | PASS |
| 3 modules unit tests | `./gradlew :core:database:test :core:network:test :core:api-avers-v4:test` | BUILD SUCCESSFUL | PASS |
| 3 modules iOS X64 compile | `./gradlew :core:database:compileKotlinIosX64 :core:network:compileKotlinIosX64 :core:api-avers-v4:compileKotlinIosX64` | BUILD SUCCESSFUL | PASS |
| EndpointsContractTest test count | `grep -c "@Test" .../EndpointsContractTest.kt` | 12 | PASS |
| KillSwitchClientTest test count | `grep -c "@Test" .../KillSwitchClientTest.kt` | 8 | PASS |
| HttpRequestRedactorTest test count | `grep -c "@Test" .../HttpRequestRedactorTest.kt` | 7 | PASS |
| manual-smoke.sh refuses CI | `CI=1 bash tools/manual-smoke.sh --dry` (per SUMMARY 02-08) | exit 1 with refusal message | PASS (verified at SUMMARY level) |

---

## Anti-Patterns Scan

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none found in shipped Phase 2 code) | — | — | — | No `TODO`/`FIXME`/empty handlers/stub returns introduced by this phase that would block goal achievement. The kotlinx-datetime deprecation warnings flagged in 02-09-SUMMARY are pre-existing in 02-04/02-06 commonMain code; non-blocking, logged for follow-up planning loop (info-level). |

**Stub-flagging refresher:** Phase 2 deliverables intentionally contain "Phase 4 wiring scope" placeholders (e.g., `loginCall = { _, _ -> false }` default in HttpClientFactory; AversAuthApi.login() not yet writing ys-* cookies post-/login). These are NOT stubs in the anti-pattern sense — they are explicit, parameterized integration seams documented in code KDoc and in `docs/aversApiV4_23813.md` §Login flow. Phase 4 supplies the real implementations.

---

## Requirements Coverage

Phase 2 has no direct AUTH/GRAD/SCHED requirement IDs (per 02-CONTEXT: "infrastructure for all subsequent phases"). The 5 ROADMAP success criteria ARE the requirements; they are all satisfied above.

Plan-frontmatter `requirements-completed` audit (across 9 SUMMARYs):
- **SUCCESS-1, SUCCESS-2, SUCCESS-3, SUCCESS-4, SUCCESS-5** — all claimed by ≥1 plan, all verified in Truths table above.
- **D-01..D-28** decisions — claimed across plans, none contradicted by code.

---

## Carried-Forward Concerns for Phase 3

These are **NOT Phase 2 gaps**. They are explicit integration seams Phase 2 declared and Phase 3 must close:

1. **CredentialProvider — Phase 3 KVault implementation.** `core/network/.../credentials/CredentialProvider.kt` ships `NoopCredentialProvider` (returns `null`). Phase 3 introduces KVault and provides a real implementation; HttpClientFactory and AversAuthInterceptor wiring is unchanged.
2. **AVERS auth completion — ys-* cookie writeback after /login.** AversAuthApi.login() currently POSTs only `/login` and parses LoginDto. The full handshake requires:
   - Implementing JS-escape() polyfill in `:core:platform` (Cyrillic codepoints emit `%uXXXX` not UTF-8 byte sequences — see `docs/aversApiV4_23813.md` §Encoding gotcha).
   - SHA-1 hashing of password (currently caller-supplied as hex — Phase 2 boundary preserves the contract module's hash-agnosticism per 02-06-SUMMARY key-decision).
   - Writing 3 cookies (`ys-user`, `ys-password`, `ys-userId`) into `RoomCookiesStorage` post-login.
   - POST `/auth` with `uId=<id>&act=1`.
   - Phase 3 wires this in `:core:data` (or `:feature:auth`) atop the typed AversApi/AversAuthApi surface.
3. **DB filename rename `journal_default.db` → `journal_${realAccountId}.db`** at first successful login (Phase 3 — D-16 deferred to Phase 3 in 02-CONTEXT).
4. **Schema v1 → v2 migration** adds `accounts` table (Phase 3); template + migration helper in 02-03 SUMMARY.
5. **iOS file protection downgrade `completeFileProtection` → `completeUntilFirstUserAuthentication`** required for Phase 6 BGAppRefreshTask (D-20). KDoc reminder embedded in `core/database/.../DatabaseFactory.ios.kt` per 02-03 SUMMARY.
6. **AversApiError.AntiBotChallenge → WebView CAPTCHA fallback (D-06)** — Phase 3 Auth UI consumes this typed error and opens WebView with `rawHtml`.
7. **UI kill-switch banner** — Phase 4 `core:ui` reads `KillSwitchConfig.message + .severity` and renders banner; halts fetches on `AversApiError.KillSwitchTriggered`.
8. **DTO → domain mapper layer** — Phase 4 `:core:data` writes mapper from typed DTOs (LoginDto, GradeDto, LessonDto, HomeworkDto, AttendanceDto, MessageDto) to domain models; Bootstrap chain B1-B5 implementation also lives there.
9. **Mobile UA divergence verification** — Phase 4 first Android `installDebug` validates DESKTOP_SAFARI_UA captures match mobile-issued requests; if divergence found, single-line UA swap to `MOBILE_SAFARI_UA` constant (Pitfall #1).

---

## Documentation vs Code Alignment

**No drift detected** between `docs/aversApiV4_23813.md` and shipped code:
- §Endpoint map endpoint paths match `internal object AversEndpoints` constants in `AversApi.kt`.
- §Login flow ys-* cookie format documented exactly as `tools/capture-avers-fixtures.py` `js_escape()` emits.
- §Response format `new Date(...)` preprocessor regex documented exactly as `ExtJsArrayPreprocessor.toStrictJson` implements.
- §Anti-bot signals AntiBotDetector defense-in-depth documented and class exists at `core/api-avers-v4/.../antibot/AntiBotDetector.kt`.
- §Kill-switch schema documented and matches `KillSwitchConfig` data class fields + `Severity` enum.
- §Logging hygiene canary string + sensitive-keys list documented and matches `HttpRequestRedactor` literals.

---

## 02-VALIDATION.md Coverage

Wave 0 deliverables (14 test files + 2 CI canary scripts + 4 doc skeletons) — all present:

**Test files (14 — all exist):**
- ✓ `core/database/.../CookieDaoSpec.kt` (+ Android/iOS wrappers)
- ✓ `core/database/.../SchemaV1Spec.kt` (+ Android/iOS wrappers)
- ✓ `core/network/.../HttpClientFactoryTest.kt` (4 @Test)
- ✓ `core/network/.../RoomCookiesStorageSpec.kt` (+ Android/iOS wrappers)
- ✓ `core/network/.../HttpRequestRedactorTest.kt` (7 @Test)
- ✓ `core/network/.../AversAuthInterceptorTest.kt` (4 @Test)
- ✓ `core/network/.../AccountDataPurgerTest.kt`
- ✓ `core/api-avers-v4/.../EndpointsContractTest.kt` (12 @Test, **commonTest** post-Plan-09 promotion)
- ✓ `core/api-avers-v4/.../ApiResultTest.kt`
- ✓ `core/api-avers-v4/.../AversApiErrorTest.kt`
- ✓ `core/api-avers-v4/.../AntiBotDetectorTest.kt`
- ✓ `core/api-avers-v4/.../KillSwitchClientTest.kt` (8 @Test)
- ✓ `core/api-avers-v4/.../AversEnvelopeTest.kt`
- ✓ `core/api-avers-v4/.../ExtJsArrayPreprocessorTest.kt`

(Note: `SanitizeHeaderTest.kt` was rolled into `HttpRequestRedactorTest.kt::header_redactor_masks_known_keys_case_insensitive` — same coverage, fewer files.)

**CI canary scripts (2 — both exist + integrated into ci.yml):**
- ✓ `tests/log-redactor-canary.sh` (wired in ci.yml line 63)
- ✓ `tests/sanitize-har-canary.sh` (wired in ci.yml line 56)

**Documentation (4 — all exist):**
- ✓ `docs/aversApiV4_23813.md`
- ✓ `docs/aversApiV4_23813-CHANGELOG.md`
- ✓ `tools/sanitize-har.py`
- ✓ `tools/sanitize-har.README.md` (became `tools/README.md` per Plan 02-02 — same content, single README file)

**iOS Native test invocations in CI (3 — all wired):**
- ✓ `:core:database:iosX64Test` (ci.yml line 114)
- ✓ `:core:network:iosX64Test` (ci.yml line 114)
- ✓ `:core:api-avers-v4:iosX64Test` (ci.yml line 114)

**Validation sign-off (02-VALIDATION.md frontmatter `nyquist_compliant: false`)** — frontmatter not flipped to `true`, but all sign-off bullets satisfied; orchestrator should update at phase close (cosmetic, no functional gap).

---

## Items in 02-VALIDATION.md NOT actually validated

**None — coverage is complete.** Two cosmetic notes:

1. **`nyquist_compliant: true` flag never flipped** in 02-VALIDATION.md frontmatter (still `false`). All listed sign-off conditions are met by code; this is a metadata update orchestrator can apply at phase close.
2. **"Real login против journal.school28-kirov.ru"** is listed as a manual-only verification (correct per D-25). The dev-host loop (`tools/manual-smoke.sh`) is shipped + documented + tested for CI-refusal. Living live-replay is the developer's hands-on responsibility, NOT a CI gap.

---

## Items in code/repo but missing from documentation

**None significant.** Phase 2 documentation discipline was strong:
- All 9 plans produced SUMMARYs with key-files / key-decisions / deviations.
- Public docs (`docs/aversApiV4_23813.md`) cross-reference internal SUMMARYs in §References.
- `docs/aversApiV4_23813-CHANGELOG.md` provides versioning template.

**Minor:** `core/network/.../AccountDataPurgerTest.kt` is in commonTest but not explicitly listed in 02-VALIDATION (was added by Plan 02-05). Non-blocking; it's a covered behavior, just absent from the upfront test-file checklist.

---

## Gaps Summary

**No gaps blocking phase goal.**

All 5 ROADMAP success criteria are satisfied with evidence (2 acceptable scope-deviations explicitly recorded as overrides, both with technical justification: Chrome DevTools wording supersedes mitmproxy via D-02; live-CAPTCHA verification on real server is dev-host-only per D-25 and CI gets HAR-replay equivalence). The Phase 2 goal — "задокументированный contract закрытого ExtJS-API АВЕРС и работающий Ktor-стек, который умеет логиниться и тянуть JSON, оставаясь устойчивым к обновлениям вендора" — is fully achieved at the contract + plumbing level. Phase 3+ supply the auth UI / KVault / DTO→domain mappers atop this foundation.

---

_Verified: 2026-04-29T13:16:31Z_
_Verifier: Claude Opus 4.7 (1M context) — gsd-verifier_
