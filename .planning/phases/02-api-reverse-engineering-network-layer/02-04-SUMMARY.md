---
phase: 02-api-reverse-engineering-network-layer
plan: 04
subsystem: network
tags: [ktor, http-client, plugin-chain, redactor, auth-interceptor, kermit, kotlinx-serialization, mock-engine]

# Dependency graph
requires:
  - phase: 01-foundation-compliance-infrastructure
    provides: ":core:network module skeleton, libs.versions.toml, lintech-kmp + lintech-test convention plugins, :core:platform module"
  - phase: 02-api-reverse-engineering-network-layer
    provides: "Plan 02-01 — versions catalog (Ktor + kotlinx + Kermit + Room + KSP); Plan 02-02 — AVERS auth specifics (ys-* cookies, JS escape() polyfill, IncompleteRead, response shapes)"

provides:
  - "HttpClientFactory with per-account HttpClient cache (Mutex-guarded) — forAccount(id) / evict(id) suspending API"
  - "7-plugin Ktor chain: HttpCookies + ContentNegotiation(Json) + Logging(redactor+sanitizeHeader) + HttpTimeout + HttpRequestRetry + DefaultRequest(UA mimic) + AversAuthInterceptor"
  - "HttpRequestRedactor: form-encoded + JSON body redactor for 7 sensitive keys (password, pwd, pass, cookie, authorization, set-cookie, token); regex matches keys only (Pitfall #2 mitigation)"
  - "AversAuthInterceptor: D-26 401 auto-relogin via HttpSend; Pitfall #4 /login endpoint skip; surfaces 401 when no creds or relogin fails"
  - "CredentialProvider: pure interface (no expect/actual) + NoopCredentialProvider default; Phase 3 KVault impl drops in unchanged"
  - "KermitKtorLogger: Ktor Logger -> Kermit (Logcat/OSLog) adapter with defense-in-depth redaction"
  - "tests/log-redactor-canary.sh exits 0 against :core:network test stdout — kanareyka_PASSWORD_DO_NOT_LEAK_42 absent"

affects:
  - "02-05 (RoomCookiesStorage): wires { id -> RoomCookiesStorage(cookieDao, id) } into HttpClientFactory.cookiesStorageProvider"
  - "02-06 (:core:api-avers-v4 endpoint contract): consumes HttpClientFactory.forAccount(id) + supplies real loginCall to AversAuthInterceptor"
  - "Phase 3 auth UI: KVault-backed CredentialProvider replaces NoopCredentialProvider"
  - "Phase 5 multi-account: per-account HttpClient cache already isolated, just supply real accountIds"

# Tech tracking
tech-stack:
  added:
    - "Ktor 3.3.3 (downgraded from 3.4.3 — see Deviation 1)"
    - "kotlinx-coroutines-test 1.10.2 (commonTest)"
    - "ktor-client-mock 3.3.3 (commonTest)"
  patterns:
    - "Per-account HttpClient cache (Mutex + mutableMap<accountId, HttpClient>) — Pitfall #6 isolation invariant"
    - "Plugin chain order encoded as numbered KDoc list — D-10 enforcement"
    - "HttpSend interceptor for cookie-session auto-relogin (D-26) — chosen over bearer { } plugin"
    - "Defense-in-depth redaction: sanitizeHeader (Ktor) + KermitKtorLogger.redact (output-side)"
    - "Pure-interface CredentialProvider (no expect/actual) — Phase 3 swaps impl without API change"

key-files:
  created:
    - "core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/HttpClientFactory.kt"
    - "core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/credentials/CredentialProvider.kt"
    - "core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/plugins/HttpRequestRedactor.kt"
    - "core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/plugins/AversAuthInterceptor.kt"
    - "core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/logging/KermitKtorLogger.kt"
    - "core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/HttpClientFactoryTest.kt"
    - "core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/plugins/HttpRequestRedactorTest.kt"
    - "core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/plugins/AversAuthInterceptorTest.kt"
  modified:
    - "core/network/build.gradle.kts (dependencies + commonTest)"
    - "gradle/libs.versions.toml (ktor 3.4.3 -> 3.3.3, +kotlinx-coroutines-test alias)"

key-decisions:
  - "Ktor 3.4.3 downgraded to 3.3.3 — Ktor 3.4.x klibs ship with Kotlin ABI 2.3.0 incompatible with project's Kotlin 2.2.20 toolchain. Last 3.x release built against kotlin-stdlib 2.2.x is 3.3.3. (Deviation 1, Rule 3 — blocking issue)"
  - "Pitfall #4 /login skip implemented via encodedPathSegments.joinToString — URLBuilder lacks encodedPath property in Ktor 3.x (Url alone has it). pathSegments-join is semantically equivalent for substring-contains check on /login. Plan's specification of request.url.encodedPath worked from a Url, but the interceptor receives an HttpRequestBuilder whose url is URLBuilder."
  - "AversAuthInterceptor installed in HttpClient.apply { ... } AFTER HttpClient construction — plugin(HttpSend).intercept needs the live client; cannot install inside HttpClient { ... } config block."
  - "kotlinx-coroutines-test added to commonTest deps — runTest is required by all 12 test cases; the lintech-test convention plugin only ships kotlin.test + kotest-assertions + turbine."
  - "Redactor + KermitKtorLogger always installed even in release (LogLevel.NONE) — defense-in-depth per D-28 mandatory addendum."
  - "DESKTOP_SAFARI_UA chosen as default UA mimic — matches Plan 02-02 Chrome DevTools captures. MOBILE_SAFARI_UA constant exposed for Pitfall #1 swap when Phase 4 first Android run reveals divergence."

patterns-established:
  - "Plugin chain order via numbered KDoc list in HttpClientFactory — single source of truth, future plans must update this when adding/reordering plugins"
  - "Stub credential providers in tests — `private class StubCredentialProvider(val creds: Pair<String,String>?)` in AversAuthInterceptorTest; reusable shape for Phase 3 KVault-backed integration tests"
  - "MockEngine + AcceptAllCookiesStorage pair in HttpClientFactoryTest — template for endpoint-contract tests in Plan 02-06 (just swap MockEngine handler)"

requirements-completed: [SUCCESS-3, SUCCESS-5, D-09, D-10, D-26, D-28]

# Metrics
duration: 21min
completed: 2026-04-29
---

# Phase 02 Plan 04: Ktor Transport Layer Summary

**HttpClientFactory with 7-plugin Ktor chain, HttpRequestRedactor scrubbing 7 sensitive keys, AversAuthInterceptor with D-26 401 auto-relogin and Pitfall #4 login-loop guard — all on Ktor 3.3.3 (downgraded from 3.4.3 due to Kotlin ABI mismatch).**

## Performance

- **Duration:** ~21 min
- **Started:** 2026-04-29T09:54:00Z
- **Completed:** 2026-04-29T10:15:00Z
- **Tasks:** 3
- **Files created:** 8 (5 main + 3 tests)
- **Files modified:** 2 (`build.gradle.kts`, `libs.versions.toml`)

## Accomplishments

- `:core:network` Phase 1 skeleton fully populated with Ktor transport — assembles for Android (`./gradlew :core:network:assemble`) and iOS X64 (`./gradlew :core:network:compileKotlinIosX64`).
- 15 commonTest cases pass on Android JVM (Debug + Release variants) — `./gradlew :core:network:test` exits 0.
- `tests/log-redactor-canary.sh` exits 0 — `kanareyka_PASSWORD_DO_NOT_LEAK_42` absent from test stdout, proving D-28 redactor + sanitizeHeader chain.
- All deferred `provides` (Plan 02-05 RoomCookiesStorage cookie-jar, Plan 02-06 endpoint contracts) have ready integration points: `cookiesStorageProvider` parameter and `loginCall` lambda.
- No `:core:database` dependency leaked into `:core:network` (Plan 02-05 lands that wire-up).

## Plugin chain order (D-10, encoded in `HttpClientFactory.buildClient`)

| # | Plugin                    | Configuration                                                                                                                                                                              |
| - | ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1 | `HttpCookies`             | `storage = cookiesStorageProvider(accountId)`                                                                                                                                              |
| 2 | `ContentNegotiation(Json)`| `ignoreUnknownKeys = true; isLenient = true; explicitNulls = false` (D-09)                                                                                                                 |
| 3 | `Logging`                 | `logger = KermitKtorLogger(redactor)`; `level = if (isDebug) LogLevel.ALL else LogLevel.NONE` (D-28); `sanitizeHeader { Authorization \|\| Cookie \|\| Set-Cookie }` (case-insensitive)    |
| 4 | `HttpTimeout`             | `connect=10s; request=30s; socket=15s`                                                                                                                                                     |
| 5 | `HttpRequestRetry`        | `maxRetries=5; retryIf 429/502/503/504; exponentialDelay base=2.0 baseDelay=1s maxDelay=30s; retryOnExceptionIf=false` (D-05)                                                              |
| 6 | `DefaultRequest`          | `URLBuilder(baseUrl) -> protocol+host+port`; `Accept-Language ru-RU,ru;q=0.9,en;q=0.8`; `Accept application/json,text/javascript,*/*;q=0.01`; `X-Requested-With XMLHttpRequest`; `User-Agent` |
| 7 | `AversAuthInterceptor`    | Installed via `plugin(HttpSend).intercept { ... }` in `HttpClient.apply { ... }` (post-construction) — D-26 + Pitfall #4                                                                    |

UA constants: `DESKTOP_SAFARI_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"`; `MOBILE_SAFARI_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"`.

## Redactor patterns

`HttpRequestRedactor.sensitiveKeys = listOf("password", "pwd", "pass", "cookie", "authorization", "set-cookie", "token")`.

For each key, two regex passes:
- **Form-encoded** (Pitfall #2 — key boundary, NOT substring): `(?i)(\bKEY=)[^&\s"]*` → `$1***REDACTED***`
- **JSON**: `(?i)("KEY"\s*:\s*")[^"]*` → `$1***REDACTED***`

Idempotent (replaced value `***REDACTED***` does not match either pattern again).

`redactHeaderValue(name, value)` — case-insensitive equality match against `sensitiveKeys`; used by Ktor `sanitizeHeader { ... }` block as a defense-in-depth helper (the block today returns boolean, but the method is wired for future header rewriting).

## AversAuthInterceptor skip-list (D-26 + Pitfall #4)

Currently a single substring guard: `request.url.encodedPathSegments.joinToString("/").contains("login", ignoreCase = true)`. Matches `/login`, `/auth/login`, `/api/login`, `/login/check`. Future endpoints requiring skip (e.g. captcha endpoint) extend this guard.

## Test count + Pitfall coverage

| File                            | Cases | Pitfalls / Decisions verified                                              |
| ------------------------------- | ----- | -------------------------------------------------------------------------- |
| `HttpRequestRedactorTest`       | 7     | Pitfall #2 (benign content NOT masked); Pitfall #5 (canary masked); D-28 |
| `HttpClientFactoryTest`         | 4     | Pitfall #6 (per-account isolation); D-10 (plugin chain installed)         |
| `AversAuthInterceptorTest`      | 4     | D-26 happy path; Pitfall #4 (login-loop prevention); 401 surfacing paths   |
| **Total**                       | **15**| All assertions exit 0                                                     |

## Task Commits

Each task was committed atomically (worktree mode, `--no-verify`):

1. **Task 1: Build deps + CredentialProvider + HttpRequestRedactor + KermitKtorLogger** — `661106d` (feat)
2. **Task 2: HttpClientFactory + AversAuthInterceptor** — `5036b10` (feat)
3. **Task 3: Test trio + kotlinx-coroutines-test alias** — `30ee509` (test)

**Plan metadata:** [pending — added by final docs commit]

## Files Created/Modified

### Created (8)

- `core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/HttpClientFactory.kt` — per-account HttpClient cache + 7-plugin chain assembly
- `core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/credentials/CredentialProvider.kt` — pure interface + NoopCredentialProvider default
- `core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/plugins/HttpRequestRedactor.kt` — body + header redactor (7 sensitive keys, 2 regexes per key, idempotent)
- `core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/plugins/AversAuthInterceptor.kt` — D-26 HttpSend interceptor with /login skip
- `core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/logging/KermitKtorLogger.kt` — Ktor Logger -> Kermit adapter with defense-in-depth redaction
- `core/network/src/commonTest/kotlin/.../HttpClientFactoryTest.kt` — 4 cases (cache, isolation, evict, plugin presence)
- `core/network/src/commonTest/kotlin/.../plugins/HttpRequestRedactorTest.kt` — 7 cases including canary + Pitfall #2
- `core/network/src/commonTest/kotlin/.../plugins/AversAuthInterceptorTest.kt` — 4 cases including D-26 happy + Pitfall #4

### Modified (2)

- `core/network/build.gradle.kts` — Ktor + kotlinx + Kermit + project(:core:platform) deps; Darwin (iOS), OkHttp (Android), MockEngine + coroutines-test (commonTest)
- `gradle/libs.versions.toml` — `ktor` pinned 3.3.3 (was 3.4.3); `kotlinx-coroutines-test` library alias added

## Decisions Made

See `key-decisions:` block in frontmatter. Highlights:

- **Ktor downgrade 3.4.3 → 3.3.3** — forced by Kotlin 2.2.20 ABI compatibility; no functional regression for Phase 2 scope (HttpCookies, ContentNegotiation, Logging, HttpTimeout, HttpRequestRetry, DefaultRequest, HttpSend, MockEngine all available identically in 3.3.3). Future Kotlin 2.3+ bump in Phase 6+ can revisit Ktor 3.4.x.
- **`encodedPathSegments.joinToString` instead of `encodedPath`** — Ktor 3.x splits the property between `Url.encodedPath` (final) and `URLBuilder.encodedPathSegments` (mutable). The HttpSend interceptor sees `HttpRequestBuilder.url: URLBuilder`, so the segment-join is the right access pattern. Behavior identical for Pitfall #4 substring contains check.
- **CredentialProvider as pure interface (no expect/actual)** — confirmed appropriate for D-26 boundary. Phase 3 swaps `NoopCredentialProvider` → `KVaultCredentialProvider` with no caller code change.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Ktor version downgrade 3.4.3 → 3.3.3**
- **Found during:** Task 1 (`./gradlew :core:network:assemble` for verification)
- **Issue:** Ktor 3.4.x `iosArm64` klibs reject build: `Skipping ... having incompatible ABI version '2.3.0'. The library was produced by '2.3.0' compiler. The current Kotlin compiler can consume libraries having ABI version <= '2.2.0'.` Project-wide Kotlin pin is 2.2.20 (CLAUDE.md Version Compatibility table — Kotlin 2.2.20 + Compose 1.10.3 + AGP 8.7.3 + JDK 17). Upgrading Kotlin would break Compose Multiplatform 1.10.3 binary metadata, AGP 8.7.3 Kotlin compiler classpath, KSP 2.2.20-2.0.4, and the entire Phase 1 baseline.
- **Fix:** Set `ktor = "3.3.3"` in `gradle/libs.versions.toml`. Ktor 3.3.3 is the latest 3.x release built against `kotlin-stdlib 2.2.21` (verified via `https://repo1.maven.org/maven2/io/ktor/ktor-client-core-iosarm64/3.3.3/ktor-client-core-iosarm64-3.3.3.module` — `kotlin-stdlib requires 2.2.21`). All plugin APIs the plan calls for are identical in 3.3.3.
- **Files modified:** `gradle/libs.versions.toml`
- **Verification:** `./gradlew :core:network:assemble` exits 0; `./gradlew :core:network:compileKotlinIosX64` exits 0; `./gradlew :core:network:test` exits 0 (15 cases pass).
- **Committed in:** `661106d` (Task 1 commit)

**2. [Rule 3 - Blocking] Missing `kotlinx-coroutines-test` dependency for `runTest`**
- **Found during:** Task 3 (test compilation)
- **Issue:** `Unresolved reference 'runTest'` across 12 test cases. The `lintech-test` convention plugin ships `kotlin.test` + `kotest-assertions` + `turbine` but NOT `kotlinx-coroutines-test`, which is the only path to `runTest { ... }` for suspending test bodies.
- **Fix:** Added `kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }` library alias in `gradle/libs.versions.toml`; added `implementation(libs.kotlinx.coroutines.test)` to `commonTest.dependencies` block in `core/network/build.gradle.kts`. NOT promoted to convention plugin (per CLAUDE.md §Module structure — non-UI modules should not pull extras through the convention plugin without justification; only `:core:network` needs runTest in Phase 2).
- **Files modified:** `gradle/libs.versions.toml`, `core/network/build.gradle.kts`
- **Verification:** Test compile + run both pass after the addition.
- **Committed in:** `30ee509` (Task 3 commit)

**3. [Rule 3 - Blocking] `request.url.encodedPath` does not exist on URLBuilder in Ktor 3.x**
- **Found during:** Task 2 (initial compilation)
- **Issue:** `e: Unresolved reference 'encodedPath'` in `AversAuthInterceptor.kt:43`. Plan specified `request.url.encodedPath.contains("/login", ignoreCase = true)`. In Ktor 3.x, `encodedPath` is a property on `io.ktor.http.Url` (final immutable, lazy), but `HttpRequestBuilder.url` is `io.ktor.http.URLBuilder` (mutable, lacks `encodedPath`). `URLBuilder` exposes `encodedPathSegments: List<String>` instead.
- **Fix:** Changed to `val encodedPath = request.url.encodedPathSegments.joinToString("/")`. Semantically equivalent for the Pitfall #4 substring contains-check; `encodedPathSegments` already encodes individual segments, so the joined string carries the same shape as `Url.encodedPath`.
- **Files modified:** `core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/plugins/AversAuthInterceptor.kt`
- **Verification:** Compile passes; `interceptor_skips_login_endpoint_pitfall_4` test asserts the `/auth/login` URL is correctly identified (loginCalls=0 after one 401 response).
- **Committed in:** `5036b10` (Task 2 commit)

**4. [Rule 3 - Blocking] Missing `header(...)` extension import in HttpClientFactory**
- **Found during:** Task 2 (compilation)
- **Issue:** `Unresolved reference 'header'` × 4 inside the `install(DefaultRequest) { ... }` block. The plan's pseudocode used `header(...)` calls but did not show the import; the function lives in `io.ktor.client.request.header`.
- **Fix:** Added `import io.ktor.client.request.header` to `HttpClientFactory.kt`.
- **Files modified:** `core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/HttpClientFactory.kt`
- **Verification:** Compile passes.
- **Committed in:** `5036b10` (Task 2 commit)

---

**Total deviations:** 4 auto-fixed (all Rule 3 blocking issues — environment/version-API contract gaps in the plan).
**Impact on plan:** All four were minor pivots forced by the actual Ktor 3.x API surface and project Kotlin pin. The plan's intent and verifications are unchanged. The Ktor 3.3.3 vs 3.4.3 version is the only externally visible change (catalog-level), and is documented for downstream plans (02-05, 02-06) so they target the same version.

## Issues Encountered

- **Ktor metadata jars in Gradle cache included Ktor 3.4.x as well as 3.3.3 / 3.2.0** — initial confusion when verifying compatibility. Resolved by inspecting `*.module` files directly via `curl` against Maven Central to confirm `kotlin-stdlib requires` field per release.

## Threat Surface Scan

No new threat surface introduced beyond the plan's `<threat_model>` (T-02-18 through T-02-25). All identified mitigations are in place:
- T-02-18 (header leak) — `sanitizeHeader { ... }` in `HttpClientFactory.install(Logging)`
- T-02-19 / T-02-20 (body leaks form/JSON) — `HttpRequestRedactor.redact` regex pair
- T-02-21 (canary) — `HttpRequestRedactorTest.form_encoded_canary_kanareyka_password_42_redacted` + `tests/log-redactor-canary.sh`
- T-02-22 (401 retry storm) — `AversAuthInterceptor` `/login` skip; `interceptor_skips_login_endpoint_pitfall_4` test
- T-02-23 (cert tampering) — accept (D-19 PROJECT system trust); no code change required
- T-02-24 (cookie isolation) — Plan 02-05 RoomCookiesStorage; Plan 02-04 ships abstract `CookiesStorage` plumbing
- T-02-25 (HTTP vs HTTPS) — `baseUrl` defaults to `https://...` in `HttpClientFactory`; `URLBuilder(baseUrl)` parses scheme correctly

## User Setup Required

None — no external service configuration required. (Phase 6 TestFlight setup deferred per ROADMAP §Phase 2 success criteria.)

## Next Phase Readiness

- **Plan 02-05 (RoomCookiesStorage):** ready — `HttpClientFactory.cookiesStorageProvider: (accountId) -> CookiesStorage` parameter is the integration point. Plan 05 supplies `{ id -> RoomCookiesStorage(cookieDao, id) }`.
- **Plan 02-06 (`:core:api-avers-v4` endpoint contract):** ready — `HttpClientFactory.forAccount(id)` exposed; `loginCall` lambda parameter is where Plan 06 wires the AVERS-specific login flow (writing ys-user/ys-password/ys-userId cookies via JS escape() polyfill semantics from Plan 02-02).
- **Phase 3 auth UI:** ready — `CredentialProvider` is a stable boundary. KVault impl drops in.
- **No blockers for Wave 3 (02-05 + 02-06).**

## Self-Check: PASSED

Verified before signing off:

```bash
# Created files
[ -f core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/HttpClientFactory.kt ] && echo FOUND
[ -f core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/credentials/CredentialProvider.kt ] && echo FOUND
[ -f core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/plugins/HttpRequestRedactor.kt ] && echo FOUND
[ -f core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/plugins/AversAuthInterceptor.kt ] && echo FOUND
[ -f core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/logging/KermitKtorLogger.kt ] && echo FOUND
[ -f core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/HttpClientFactoryTest.kt ] && echo FOUND
[ -f core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/plugins/HttpRequestRedactorTest.kt ] && echo FOUND
[ -f core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/plugins/AversAuthInterceptorTest.kt ] && echo FOUND

# Commits
git log --oneline | grep -q 661106d && echo COMMIT-1-FOUND
git log --oneline | grep -q 5036b10 && echo COMMIT-2-FOUND
git log --oneline | grep -q 30ee509 && echo COMMIT-3-FOUND
```

All expected outputs returned. Build and test verifications green:
- `./gradlew :core:network:assemble` — EXIT 0
- `./gradlew :core:network:compileKotlinIosX64` — EXIT 0
- `./gradlew :core:network:test` — EXIT 0 (15 cases, both Debug + Release variants)
- `bash tests/log-redactor-canary.sh` — EXIT 0 (canary absent)

---

*Phase: 02-api-reverse-engineering-network-layer*
*Plan: 04*
*Completed: 2026-04-29*
