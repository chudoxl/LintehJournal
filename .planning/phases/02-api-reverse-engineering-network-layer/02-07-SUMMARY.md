---
phase: 02-api-reverse-engineering-network-layer
plan: 07
subsystem: kill-switch
tags: [kill-switch, github-pages, fail-open, hardening, kotlinx-serialization-runtime, mutex-cache, t-02-38]

# Dependency graph
requires:
  - phase: 02-api-reverse-engineering-network-layer
    provides: "Plan 02-06 — ApiResult<T> sealed (Success/Mismatch/Failure) + AversApiError sealed including KillSwitchTriggered (already declared); Plan 01 (Foundation) — pages.yml workflow watching docs/** auto-deploys to https://chudoxl.github.io/LintehJournal/"

provides:
  - "docs/api-config.json — static kill-switch config deployed via existing GitHub Pages workflow at https://chudoxl.github.io/LintehJournal/api-config.json"
  - "KillSwitchConfig (commonMain) — @Serializable(with = KillSwitchConfigSerializer::class) data class with fields {latestSupportedAversBuild, message, severity, minAppVersion} + Severity enum (info/warning/block) + Severity.fromWire() forward-compatible parser"
  - "KillSwitchClient (commonMain) — checkOrFailOpen() with D-13 fail-open + D-14 in-memory cache (Mutex-guarded double-checked locking) + T-02-38 hardening (severity=block honored only when builds differ)"
  - "KillSwitchClientTest (commonTest, 8 cases) — covers happy path, severity=warning, T-02-38 trigger arm, T-02-38 hardening arm (same-build severity=block downgraded), HTTP 404 fail-open, malformed JSON fail-open, D-14 cache hit, invalidateCache forces refetch"

affects:
  - "Phase 4+ UI banner: consumes ApiResult<KillSwitchConfig>.value.severity (warning) + value.message to render non-blocking banner; catches ApiResult.Failure(AversApiError.KillSwitchTriggered) to halt fetches and show 'обновите приложение' fallback (D-12 + Phase 2 success criterion #4)"
  - "Phase 6 BGAppRefreshTask / WorkManager: may invoke KillSwitchClient.invalidateCache() periodically to refresh the cached config in long-running sessions (default cadence is cold-start only per D-14)"
  - "Plan 02-09 (kotlinx-serialization compiler plugin): if Plan 02-09 chooses to add the plugin to :core:api-avers-v4, KillSwitchConfigSerializer becomes optional (could be replaced by the generated companion serializer); custom serializer still works either way"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Custom KSerializer reading JsonObject for runtime-only kotlinx-serialization (no compiler plugin) — matches Plan 02-06 DTO convention. KillSwitchConfigSerializer reads the four required/optional fields from JsonObject directly via JsonPrimitive.contentOrNull, with a Severity.fromWire(value: String?) helper that downgrades unknown wire values to info for forward-compat."
    - "Mutex-guarded double-checked in-memory cache — fast path returns cached value without acquiring the lock; slow path acquires the lock and re-checks before fetching, so concurrent first-callers fan in to a single network request."
    - "Fail-open via runCatching{...}.getOrElse{e -> defaultConfig()} — single arm catches HttpRequestTimeoutException + non-2xx (explicit response.status.isSuccess() check inside the runCatching block) + JSON parse errors + DNS failure. Logger.w{} records the cause; the user-visible flow always proceeds."

key-files:
  created:
    - "docs/api-config.json"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/killswitch/KillSwitchConfig.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/killswitch/KillSwitchClient.kt"
    - "core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/killswitch/KillSwitchClientTest.kt"
  modified: []

key-decisions:
  - "Custom KSerializer for KillSwitchConfig + Severity instead of plain @Serializable — :core:api-avers-v4 does NOT apply the kotlinx-serialization compiler plugin (only the runtime). Plan 02-09 owns build.gradle.kts; adding the plugin is out-of-scope for Plan 02-07. Custom serializer is the established Plan 02-06 DTO convention; tests prove it works through Ktor MockEngine without ContentNegotiation."
  - "bodyAsText() + Json.parseToJsonElement instead of body<KillSwitchConfig>() — removes ContentNegotiation as a hard dependency for callers and aligns with how AversApi parses bodies (Plan 02-06 paragraph). Tests exercise plain HttpClient(MockEngine) without any ContentNegotiation install."
  - "Severity.fromWire(value: String?) returns info for unknown values — forward-compatible with future severity codes (e.g. silent, nudge) that older app versions would otherwise reject."
  - "Explicit response.status.isSuccess() guard inside runCatching — KillSwitchClient enforces 2xx semantics regardless of the caller's HttpClient configuration (expectSuccess = true | false | default). Non-2xx flows into the same fail-open arm as parse failures and timeouts."
  - "Did NOT modify core/api-avers-v4/build.gradle.kts — Plan 02-09 owns module configuration. All required deps (ktor-client-core, ktor-client-mock, kotlinx-coroutines-core, kotlinx-coroutines-test, kotlinx-serialization-json, kermit) are already present from Plan 02-06."
  - "Test count = 8 (matches plan's <action> code; plan's <behavior> bullet list mentioned a separate timeout case but the action block did not write one — MockEngine cannot exercise HttpRequestTimeoutException without HttpTimeout installed and synchronous mock responses, so the existing 404 + malformed JSON + the runCatching catch-all cover the same fail-open arm)."

requirements-completed: [SUCCESS-4, D-12, D-13, D-14]

# Metrics
duration: ~25min
completed: 2026-04-29
---

# Phase 02 Plan 07: Kill-switch infrastructure Summary

**Server-published kill-switch — `docs/api-config.json` deployed via the existing Phase 1 GitHub Pages workflow + `KillSwitchClient` in `:core:api-avers-v4` with D-13 fail-open semantics, D-14 in-memory cache, and T-02-38 hardening. Phase 2 success criterion #4 ("remote kill-switch (статический JSON на CDN) умеет показать пользователю баннер «обновите приложение»") is satisfied at the contract layer; Phase 4+ UI consumes the ApiResult<KillSwitchConfig> + AversApiError.KillSwitchTriggered surface.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-29T14:21:00Z
- **Completed:** 2026-04-29T14:46:00Z
- **Tasks:** 3
- **Files created:** 4 (1 static JSON + 2 commonMain Kotlin + 1 commonTest Kotlin)
- **Files modified:** 0
- **Plan-level commits:** 3 (one per task) + 1 docs commit (this SUMMARY)

## Accomplishments

- **`docs/api-config.json`** — valid JSON (`python3 -m json.tool` pass) with the production schema {`latestSupportedAversBuild=23813`, `severity=info`, `message=""`, `minAppVersion=0.2.0`, `_comment` documentation field}. The Phase 1 `pages.yml` workflow watches `docs/**` so this file auto-deploys to `https://chudoxl.github.io/LintehJournal/api-config.json` after the next merge to `main` (verifiable via `curl` post-merge).
- **`KillSwitchConfig` + `Severity` enum + `KillSwitchConfigSerializer`** — runtime-only kotlinx-serialization custom serializer (no compiler plugin needed). `Severity.fromWire(String?)` downgrades unknown wire values to `info`, future-proof against new severity codes.
- **`KillSwitchClient.checkOrFailOpen()`** — single suspend entry point. Mutex-guarded double-checked in-memory cache so concurrent first-callers fan in to one network request. 5s default request timeout. Fail-open arm collapses HttpRequestTimeoutException, non-2xx HTTP, malformed JSON, DNS failure — all into `ApiResult.Success(defaultConfig())`. T-02-38 hardening: `severity=block` honored ONLY when `latestSupportedAversBuild != currentAversBuild`.
- **8 commonTest cases — all pass on Android JVM:** happy path (info), severity=warning (Success), T-02-38 trigger (different build + block → Failure), T-02-38 hardening (same build + block → Success), HTTP 404 fail-open, malformed JSON fail-open, D-14 cache hit (second `checkOrFailOpen()` does NOT call MockEngine again), `invalidateCache()` forces refetch.
- **iOS X64 compile clean** — `./gradlew :core:api-avers-v4:compileKotlinIosX64 :core:api-avers-v4:compileTestKotlinIosX64` both BUILD SUCCESSFUL. Plan 02-08 (CI iosX64Test runtime) and Plan 02-09 (final integration smoke) will pick up the new commonTest case automatically without further wiring.
- **`AversApiError.KillSwitchTriggered`** — already declared in Plan 02-06's `AversApiError.kt` (`data object`). Wiring complete; no edits needed to Plan 02-06 artifacts.

## File map

| File | Role | Public API |
|------|------|------------|
| `docs/api-config.json` | Static config served from GitHub Pages | `{latestSupportedAversBuild, message, severity, minAppVersion}` (current values: `23813 / "" / info / 0.2.0`) |
| `KillSwitchConfig.kt` | DTO + Severity enum + custom KSerializer | `data class KillSwitchConfig(...)`, `enum class Severity { info, warning, block }`, `Severity.fromWire(String?)` |
| `KillSwitchClient.kt` | Fetch + cache + interpret | `class KillSwitchClient(httpClient, killSwitchUrl, currentAversBuild, currentAppVersion, timeoutMillis)`, `suspend fun checkOrFailOpen(): ApiResult<KillSwitchConfig>`, `suspend fun invalidateCache()` |
| `KillSwitchClientTest.kt` | 8 contract tests | runTest{} + MockEngine — no ContentNegotiation needed |

## Behaviour matrix (KillSwitchClient.checkOrFailOpen)

| Scenario | HTTP response | Configured `currentAversBuild` | Result |
|----------|---------------|--------------------------------|--------|
| Happy path | 200 OK + `severity=info` | matches | `Success(KillSwitchConfig(severity=info, ...))` |
| Soft warning | 200 OK + `severity=warning` | (any) | `Success(KillSwitchConfig(severity=warning, message=...))` — UI shows banner; AVERS fetches proceed |
| Block, build mismatch | 200 OK + `severity=block` + `latestSupportedAversBuild=24000` | `23813` | `Failure(AversApiError.KillSwitchTriggered)` — AVERS fetches halted |
| Block, same build (T-02-38 hardening) | 200 OK + `severity=block` + `latestSupportedAversBuild=23813` | `23813` | `Success(...)` — accident-resistant; logged at WARN level |
| 404 (server down / file moved) | 404 + empty body | (any) | `Success(defaultConfig())` — fail-open |
| Malformed JSON | 200 OK + `<<<not json>>>` | (any) | `Success(defaultConfig())` — fail-open |
| Timeout (production) | request exceeds 5s | (any) | `Success(defaultConfig())` — fail-open via HttpRequestTimeoutException catch arm (verified by code review; not exercised in unit test because MockEngine responds synchronously) |
| Cached call | already invoked once | (any) | Returns cached interpretation; MockEngine call count stays at 1 |
| invalidateCache + refetch | cache cleared then called again | (any) | New MockEngine call; result reflects current response sequence |

## Threat coverage

Plan's `<threat_model>` T-02-38 through T-02-42 — all addressed at the contract layer:

| Threat | Status | Mitigation |
|--------|--------|------------|
| T-02-38 (GitHub Pages compromised → severity=block bricks user base) | Mitigated | Hardening guard: `severity=block` honored ONLY when `latestSupportedAversBuild != currentAversBuild`. Verified by `severity_block_with_same_build_returns_success_hardening_t_02_38` test case. |
| T-02-39 (GitHub Pages downtime / 404 / timeout) | Mitigated | D-13 fail-open: any non-2xx, parse failure, or thrown exception drops into `ApiResult.Success(defaultConfig())`. Verified by `http_404_returns_fail_open_d_13` and `malformed_json_returns_fail_open` test cases. |
| T-02-40 (Slow GitHub Pages response blocks app launch) | Mitigated | 5s default `requestTimeoutMillis` via Ktor `timeout {}` block; HttpRequestTimeoutException caught in the same fail-open arm. |
| T-02-41 (Malicious config injection via DNS / proxy) | Accepted (D-19 PROJECT decision) | System trust store + HTTPS-only; no SSL pinning. Documented in plan. |
| T-02-42 (KillSwitchClient reveals app version + build via fetch) | Accepted | Fetch is GET to a public CDN URL; no user data transmitted. |

## Task Commits

Each task was committed atomically (worktree mode, `git commit --no-verify`):

1. **Task 1: docs/api-config.json static kill-switch config** — `2b55531` (feat)
2. **Task 2: KillSwitchConfig + KillSwitchClient (commonMain)** — `79d82cc` (feat)
3. **Task 3: KillSwitchClientTest + custom-serializer refactor** — `e83f0e0` (test)

Plan metadata commit (this SUMMARY.md) follows after self-check.

## Decisions Made

See `key-decisions:` block in frontmatter. Highlights:

- **Custom KSerializer over @Serializable + compiler plugin** — `:core:api-avers-v4` ships only the kotlinx-serialization runtime, not the compiler plugin. Plan 02-09 owns `core/api-avers-v4/build.gradle.kts` (parallel-execution constraint), so adding the plugin is out-of-scope for Plan 02-07. Custom serializer aligns with the Plan 02-06 DTO convention and works without any module-level config change.
- **bodyAsText() + manual Json parse** instead of `body<KillSwitchConfig>()` — removes ContentNegotiation as a caller obligation, matches AversApi's body-handling style, and lets the test suite drive a plain `HttpClient(MockEngine)` without plugin installation.
- **Severity.fromWire() with `info` fallback** — forward-compatible if the static JSON ever ships a new severity code (e.g. `nudge`, `silent`); older clients won't crash, they'll just default to no-op.
- **Explicit `response.status.isSuccess()` check inside `runCatching`** — works correctly regardless of the caller's `expectSuccess` setting; non-2xx is treated identically to a thrown exception.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] kotlinx-serialization compiler plugin not applied to `:core:api-avers-v4`**
- **Found during:** Task 3 first test run — 4 of 8 cases failed with `expected:<warning> but was:<info>` and `expected:<block> but was:<info>`. Diagnosis: `@Serializable data class KillSwitchConfig(...)` parsed `latestSupportedAversBuild` (String) correctly via runtime reflection but silently fell through to default `severity = info` because the compiler-generated companion serializer was absent.
- **Issue:** The plan's `<action>` block specified `@Serializable data class KillSwitchConfig(...)` plus `body<KillSwitchConfig>()`. That requires the kotlinx-serialization Gradle plugin (`org.jetbrains.kotlin.plugin.serialization`) to be applied to the consuming module. `:core:api-avers-v4` only depends on the runtime — every existing DTO uses `@Serializable(with = XxxSerializer::class)` with a custom KSerializer (Plan 02-06 SUMMARY line 84-85, deviation note that the auto-generated companion is blocked when `with =` is set).
- **Constraint:** Per parallel-execution rules in this prompt, Plan 02-09 owns `core/api-avers-v4/build.gradle.kts`. Adding the serialization plugin would require modifying that file, which is out-of-scope.
- **Fix:** Refactored `KillSwitchConfig` to declare `@Serializable(with = KillSwitchConfigSerializer::class)` and added an `internal object KillSwitchConfigSerializer : KSerializer<KillSwitchConfig>` that decodes from `JsonObject` via `JsonPrimitive.contentOrNull` per field. Added `Severity.fromWire(String?)` for forward-compatible enum decoding without runtime reflection. Refactored `KillSwitchClient` to use `bodyAsText()` + `Json.parseToJsonElement` + `decodeFromJsonElement(KillSwitchConfigSerializer, ...)` instead of `body<KillSwitchConfig>()` — also drops the ContentNegotiation requirement on the test HttpClient.
- **Files modified:** `KillSwitchConfig.kt` (added custom serializer + fromWire helper), `KillSwitchClient.kt` (switched to bodyAsText pattern), `KillSwitchClientTest.kt` (dropped ContentNegotiation install in test client builder).
- **Verification:** All 8 KillSwitchClientTest cases pass. Module assemble + iOS X64 compile both clean.
- **Committed in:** `e83f0e0` (Task 3 commit captures the test + the custom-serializer refactor in a single atomic delivery — the in-progress code from Task 2 was strictly worse because tests would have failed with it).

**2. [Rule 1 - Bug] Kotlin KDoc nested-comment trap with `docs/**` substring**
- **Found during:** Task 2 first build (`./gradlew :core:api-avers-v4:assemble` after creating `KillSwitchConfig.kt`).
- **Issue:** `Syntax error: Unclosed comment` at line 45 (EOF) of `KillSwitchConfig.kt`. KDoc `/**` opener at the top of the file paired with `\`docs/**\`` inside a backtick-quoted phrase: Kotlin's KDoc lexer treats `/*` as a nested-block opener regardless of surrounding markdown formatting, then expects `*/` to close it before the outer KDoc can close. Same class of issue as Plan 02-06 deviation #4 with `/act/*` — recurrence confirms this is a long-lived hazard worth documenting in CLAUDE.md if it surfaces a third time.
- **Fix:** Rephrased `\`docs/**\`` to `the \`docs/\` tree`. No semantic loss; the workflow still references `docs/**` in `pages.yml`.
- **Files modified:** `KillSwitchConfig.kt` line 9.
- **Verification:** `./gradlew :core:api-avers-v4:assemble` succeeded after edit.
- **Committed in:** `79d82cc` (Task 2 commit — fix rolled into the initial source delivery).

**3. [Rule 3 - Blocking] Missing `local.properties` in worktree**
- **Found during:** First `./gradlew :core:api-avers-v4:assemble` run — Android Gradle Plugin failed with `SDK location not found`.
- **Issue:** `local.properties` is gitignored; the worktree was freshly created from base commit `cbfc07f` and never received a copy. The main checkout has `sdk.dir=/home/chudoxl/Android/Sdk` configured.
- **Fix:** Created `local.properties` in the worktree root with the same `sdk.dir` value. File remains gitignored.
- **Files modified:** none committed (gitignored).
- **Verification:** Subsequent gradle invocations succeed.
- **Committed in:** N/A (gitignored).

**Total deviations:** 3 (1 × Rule 1, 2 × Rule 3). Deviation #1 was the substantive change — refactoring KillSwitchConfig to use a custom KSerializer keeps Plan 02-07 within its module-ownership boundaries (no edits to `core/api-avers-v4/build.gradle.kts`). All deviations have unit-test or build-time evidence of resolution.

## Authentication Gates

None — kill-switch fetch is unauthenticated GET to a public GitHub Pages URL.

## Issues Encountered

- **Recurring Kotlin KDoc nested-comment trap** — second occurrence in Phase 2 (Plan 02-06 hit it with `/act/*`, Plan 02-07 with `docs/**`). Worth a one-line note in CLAUDE.md §Conventions if a third recurrence happens. Workaround: rephrase substrings containing `/*` so they don't open a nested block.
- **kotlinx-serialization compiler-plugin gap** — `:core:api-avers-v4` is intentionally configured with the runtime only. Plan 02-09 may decide to add the plugin (would simplify future DTOs by allowing plain `@Serializable` without custom KSerializers), but until then the custom-serializer convention remains the established pattern and KillSwitchConfig follows it.

## Threat Surface Scan

No new threat surface beyond `<threat_model>` (T-02-38 through T-02-42) — all addressed in the implementation. The kill-switch URL is a public CDN endpoint (`https://chudoxl.github.io/LintehJournal/api-config.json`) that already serves the project's privacy policy and landing page, so no new attack surface. Schema changes at trust boundaries: none.

## Known Stubs

None — all four files deliver working production code. `defaultConfig()` is intentional fallback content (D-13 contract), not a stub. Phase 4+ UI consumes the public API as documented.

## User Setup Required

None for Plan 02-07 itself.

**After main merge** — the maintainer can verify the deployment chain by running `curl -fsS https://chudoxl.github.io/LintehJournal/api-config.json` once the `pages.yml` workflow has run. Expected output is the JSON committed in `docs/api-config.json` (verbatim).

## Next Phase Readiness

- **Plan 02-08 (CI iosX64Test runtime):** ready — KillSwitchClientTest is in commonTest and compiles on iOS X64; macos-15 CI will pick it up automatically.
- **Plan 02-09 (final integration smoke + iosTest variant of EndpointsContractTest):** ready — KillSwitchClientTest already runs on commonTest, so no additional wiring needed when 02-09 promotes EndpointsContractTest to commonTest. If 02-09 chooses to apply the kotlinx-serialization compiler plugin, KillSwitchConfigSerializer can be optionally simplified (reduce to `@Serializable data class` + auto-generated companion); current code keeps working either way.
- **Phase 4 :core:data + UI banner:** consumers should call `KillSwitchClient.checkOrFailOpen()` early in app startup (after HttpClientFactory wiring), inspect the result:
  - `ApiResult.Success(cfg)` with `cfg.severity == warning` → render non-blocking banner using `cfg.message`.
  - `ApiResult.Success(cfg)` with `cfg.severity == info` → no banner.
  - `ApiResult.Failure(AversApiError.KillSwitchTriggered)` → halt AVERS data fetches and render the "обновите приложение" fallback. Since the same screen also handles AntiBotChallenge (D-06), this is a single error-state surface in the Phase 3 Auth UI / Phase 4 dashboard.
- **Phase 6 background polling:** may invoke `invalidateCache()` from `WorkManager` / `BGAppRefreshTask` to refresh the kill-switch decision in long-running sessions. Default cadence (D-14) is cold-start only.
- **No blockers for the remaining Wave 5 plans (02-08, 02-09).**

## Self-Check: PASSED

Verified before signing off:

```bash
# Created files (4 files)
[ -f docs/api-config.json ] && echo FOUND  # → FOUND
[ -f core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/killswitch/KillSwitchConfig.kt ] && echo FOUND  # → FOUND
[ -f core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/killswitch/KillSwitchClient.kt ] && echo FOUND  # → FOUND
[ -f core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/killswitch/KillSwitchClientTest.kt ] && echo FOUND  # → FOUND

# JSON validity
python3 -m json.tool docs/api-config.json > /dev/null && echo VALID  # → VALID

# Commits
git log --oneline | grep -q 2b55531 && echo COMMIT-1-FOUND  # → COMMIT-1-FOUND
git log --oneline | grep -q 79d82cc && echo COMMIT-2-FOUND  # → COMMIT-2-FOUND
git log --oneline | grep -q e83f0e0 && echo COMMIT-3-FOUND  # → COMMIT-3-FOUND
```

Build + test verifications — all green:

- `./gradlew :core:api-avers-v4:assemble` — BUILD SUCCESSFUL
- `./gradlew :core:api-avers-v4:test` — BUILD SUCCESSFUL (44 cases, including the 8 new KillSwitchClientTest cases)
- `./gradlew :core:api-avers-v4:compileKotlinIosX64` — BUILD SUCCESSFUL
- `./gradlew :core:api-avers-v4:compileTestKotlinIosX64` — BUILD SUCCESSFUL
- `python3 -m json.tool docs/api-config.json` — valid JSON

Worktree boundaries respected — zero edits to:
- `.planning/STATE.md`
- `.planning/ROADMAP.md`
- `core/api-avers-v4/build.gradle.kts` (Plan 02-09's domain)
- `.github/workflows/ci.yml` (Plan 02-09's domain)

---

*Phase: 02-api-reverse-engineering-network-layer*
*Plan: 07*
*Completed: 2026-04-29*
