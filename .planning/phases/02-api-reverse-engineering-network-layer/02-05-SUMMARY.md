---
phase: 02-api-reverse-engineering-network-layer
plan: 05
subsystem: network
tags: [room, ktor-cookies, expect-actual, account-purge, rfc-6265, pitfall-6, pitfall-7, spec-wrapper]

# Dependency graph
requires:
  - phase: 02-api-reverse-engineering-network-layer
    provides: "Plan 02-03 — JournalDatabase v1 + CookieEntity + CookieDao + DatabaseFactory expect/actual; Plan 02-04 — HttpClientFactory.cookiesStorageProvider parameter + AversAuthInterceptor scaffold expecting CookiesStorage wiring"

provides:
  - "RoomCookiesStorage : CookiesStorage (Ktor 3.3.3) — DAO-backed, per-account scoped, in-memory expiry filter (Pitfall cookie-expiry-race)"
  - "CookieMapper — bidirectional Cookie <-> CookieEntity (Cookie.toEntity / CookieEntity.toKtorCookie); GMTDate(timestamp) <-> Long epoch millis"
  - "AccountDataPurger interface + DefaultAccountDataPurger 3-step orchestration (factory.evict -> dao.deleteByAccount -> fileResolver.delete) with regex-validated accountId"
  - "DatabaseFileResolver expect interface + Android (Context.getDatabasePath + WAL sidecars) + iOS (NSDocumentDirectory + WAL sidecars) actuals"
  - "InMemoryJournalDatabase test factory (commonTest expect, androidUnitTest + iosTest actuals) — Spec/Wrapper test infrastructure for :core:network"
  - "12 commonTest cases (6 RoomCookiesStorage + 6 AccountDataPurger) — Pitfall #6 + Pitfall #7 + T-02-30 path-traversal verified on Android JVM"

affects:
  - "02-06 (:core:api-avers-v4): HttpClientFactory now produces clients with persistent Room-backed cookies; loginCall lambda receives a fully-wired client + storage"
  - "Phase 3 (Auth UI logout): KVault.removeCreds(accountId) + AccountDataPurger.purge(accountId) is the locked logout sequence; Phase 3 just composes the two existing primitives"
  - "Phase 5 multi-account 'Delete Account' UX: same AccountDataPurger.purge contract, no further Phase 5 wire-up required"

# Tech tracking
tech-stack:
  added:
    - "androidx.room.runtime in :core:network commonTest deps (and via androidUnitTest sqlite-framework for Robolectric driver)"
    - "androidx.sqlite.framework in :core:network androidUnitTest (BundledSQLiteDriver JNI workaround inherited from Plan 02-03)"
    - "robolectric.properties (sdk=33) — :core:network/androidUnitTest test infrastructure"
  patterns:
    - "Spec/Wrapper test split for cross-platform Room consumers — RoomCookiesStorageSpec object in commonTest + RoomCookiesStorageTestAndroid (Robolectric @RunWith) + RoomCookiesStorageTestIos (kotlin.test) wrappers; mirrors Plan 02-03 CookieDaoSpec"
    - "Stub-class collaborators for AccountDataPurger orchestration tests — keeps suspend semantics + ordering assertions explicit; matches AversAuthInterceptorTest StubCredentialProvider pattern (no Mokkery for ordering-sensitive cases)"
    - "Driver-by-target test infrastructure: AndroidSQLiteDriver (Robolectric-friendly) on Android JVM; BundledSQLiteDriver on iOS Native; production code unchanged"
    - "Bridging :core:network -> :core:database — first cross-module project dep added in Phase 2 wave order; no circular dep risk because :core:database does not depend on :core:network"

key-files:
  created:
    - "core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/RoomCookiesStorage.kt"
    - "core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/CookieMapper.kt"
    - "core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/AccountDataPurger.kt"
    - "core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/DatabaseFileResolver.kt"
    - "core/network/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/DatabaseFileResolver.android.kt"
    - "core/network/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/DatabaseFileResolver.ios.kt"
    - "core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/InMemoryJournalDatabase.kt"
    - "core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/RoomCookiesStorageSpec.kt"
    - "core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/AccountDataPurgerTest.kt"
    - "core/network/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/InMemoryJournalDatabase.android.kt"
    - "core/network/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/RoomCookiesStorageTestAndroid.kt"
    - "core/network/src/androidUnitTest/resources/robolectric.properties"
    - "core/network/src/iosTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/InMemoryJournalDatabase.ios.kt"
    - "core/network/src/iosTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/RoomCookiesStorageTestIos.kt"
  modified:
    - "core/network/build.gradle.kts — implementation(project(\":core:database\")) in commonMain; Room runtime + sqlite-bundled in commonTest; Robolectric + sqlite-framework in androidUnitTest"

key-decisions:
  - "expect interface DatabaseFileResolver (not expect class) — keeps the production class internal (AndroidDatabaseFileResolver / IosDatabaseFileResolver) and provides a defaultDatabaseFileResolver() factory; cleanest match to D-19 expect/actual pattern when the contract is small"
  - "Spec/Wrapper test layout repeated from Plan 02-03 — direct commonTest open class would be picked up by JUnit 4 on Android JVM without Robolectric runner, causing setUp() to crash; explicit @RunWith wrapper + spec object is the proven pattern"
  - "Stub-class collaborators in AccountDataPurgerTest (no Mokkery) — Mokkery's everySuspend.calls{ args } pattern obscures sequential-side-effect ordering; explicit StubCookieDao + StubFileResolver record call lists, matches existing AversAuthInterceptorTest StubCredentialProvider; saves a Mokkery-only abstraction layer for ordering-sensitive specs"
  - "Real HttpClientFactory in AccountDataPurgerTest (instead of mocked) — factory has private state (clients map, mutex); building it over MockEngine is cheap and gives a closer-to-production exercise of the evict path; ordering assertions use a recordingPurger composition wrapper rather than touching the factory internals"
  - "Smart-cast cross-module workaround — `it.expiresAtEpochMillis > nowMillis` would fail because CookieEntity.expiresAtEpochMillis is in :core:database (different module); pinned the value to a local `val expiry` to satisfy the compiler"

patterns-established:
  - "Cross-module project dep template — :core:network -> :core:database with Room runtime added to consumer's commonTest deps because :core:database keeps Room as implementation (not api)"
  - "Spec/Wrapper test layout for any KMP module that consumes :core:database in tests — repeat InMemoryJournalDatabase expect/actual + Robolectric AndroidSQLiteDriver actual + Native BundledSQLiteDriver actual"
  - "DefaultAccountDataPurger 3-step error handling — fail-soft per step (each try/catch logs but continues); subsequent purges retry remaining steps; T-02-29 mitigation"

requirements-completed: [SUCCESS-3, D-15, D-16, D-18, D-22]

# Metrics
duration: ~13min
completed: 2026-04-29
---

# Phase 02 Plan 05: RoomCookiesStorage + AccountDataPurger Summary

**`:core:network` becomes the bridge between Ktor's `HttpCookies` and Room: persistent, per-account-scoped, expiry-filtered cookie storage (Pitfall #7 subdomain match + Pitfall #cookie-expiry-race closed) plus a 3-step `AccountDataPurger.purge(accountId)` wipe primitive (factory.evict + cookieDao.deleteByAccount + databaseFileResolver.delete with WAL sidecars and `[A-Za-z0-9_-]+` accountId regex T-02-30 mitigation).**

## Performance

- **Duration:** ~13 min
- **Started:** 2026-04-29T10:30:06Z
- **Completed:** 2026-04-29T10:42:51Z
- **Tasks:** 3 / 3
- **Files created:** 14 (6 main + 1 build.gradle.kts modification + 7 tests/test infra)
- **Files modified:** 1 (`core/network/build.gradle.kts`)
- **Plan-level commits:** 3 (one per task) + 1 docs commit (this SUMMARY)

## Accomplishments

- **`:core:network` now depends on `:core:database`** — first inter-module project dep at the network layer; no circular dep risk because `:core:database` doesn't depend on `:core:network`.
- **`RoomCookiesStorage` — production-grade `CookiesStorage` adapter:**
  - `get(requestUrl)` resolves cookies via `CookieDao.findFor` (RFC 6265 subdomain LIKE + path prefix LIKE) AND filters expired cookies in-memory by comparing `expiresAtEpochMillis > now()` (Pitfall cookie-expiry-race closed).
  - Session cookies (`expiresAtEpochMillis == null`) survive HttpClient close+reopen — D-15 design.
  - `addCookie` skips empty-name cookies per RFC 6265.
  - Per-account scope is structurally enforced — every DAO call passes `accountId` from the constructor; cross-account leak (Pitfall #6) is impossible.
  - Constructor-injected `Clock` (default `Clock.System`) keeps tests deterministic if needed (current spec uses `Clock.System` + relative offsets — sufficient for the 60s window assertions).
- **`CookieMapper` — bidirectional Ktor `Cookie` ↔ Room `CookieEntity`:**
  - `Cookie.toEntity(accountId, defaultDomain)` — when `Cookie.domain` is null, falls back to `requestUrl.host` (RFC 6265 host-only flag approximation).
  - `CookieEntity.toKtorCookie()` — uses `GMTDate(timestamp = epochMillis)` top-level factory function (Ktor 3.3.3 — Cookie's expires param accepts GMTDate? per `io.ktor.util.date.DateJvmKt`).
- **`AccountDataPurger` — D-22 3-step orchestration:**
  1. `httpClientFactory.evict(accountId)` — close in-flight requests, release connections.
  2. `cookieDao.deleteByAccount(accountId)` — drop all cookie rows.
  3. `databaseFileResolver.delete(accountId)` — physically delete `journal_${accountId}.db` + `-shm` + `-wal` sidecars.
  - Each step's failure is logged via Kermit but does NOT abort the next step (T-02-29 partial-cleanup mitigation).
  - `accountId` regex `^[A-Za-z0-9_-]+$` blocks path traversal (T-02-30) — verified by tests covering `../etc/passwd` and `alice/bob` rejection.
- **`DatabaseFileResolver` expect/actual — platform DB file lifecycle:**
  - Android (`actual class AndroidDatabaseFileResolver`): `Context.getDatabasePath("journal_${accountId}.db").delete()` + WAL sidecar cleanup.
  - iOS (`actual class IosDatabaseFileResolver`): `NSFileManager.removeItemAtPath` under `NSDocumentDirectory` (mirrors `:core:database/DatabaseFactory.ios.kt`) + WAL sidecars.
  - Returns `false` (not throwing) when the main file does not exist — already-purged accounts are not an error.
- **All Pitfall + Threat coverage in tests:**
  - `RoomCookiesStorageSpec.crossAccountIsolationPitfall6` — two storages over same DAO never see each other's rows.
  - `RoomCookiesStorageSpec.getMatchesSubdomainPitfall7` — parent-domain cookie reaches subdomain request.
  - `RoomCookiesStorageSpec.getFiltersExpiredCookies` — cookies past `expiresAtEpochMillis` dropped from `get`.
  - `AccountDataPurgerTest.purge_rejects_account_id_with_path_traversal` + `..._with_slash` — T-02-30.
  - `AccountDataPurgerTest.purge_continues_after_dao_delete_throws` + `..._does_not_propagate_file_delete_failure` — T-02-29.
- **All tests pass on Android JVM (Robolectric, both Debug + Release variants):** `./gradlew :core:network:test` BUILD SUCCESSFUL with 12 new cases (6 RoomCookiesStorage + 6 AccountDataPurger) + 15 pre-existing Plan 02-04 cases = 27 total.
- **iOS X64 compile clean:** `./gradlew :core:network:compileKotlinIosX64 :core:network:compileTestKotlinIosX64` BUILD SUCCESSFUL (locally on Linux dev-host; runtime `iosX64Test` reserved for macos-15 CI per Plan 02-08).
- **`tests/log-redactor-canary.sh` regression-clean** — D-28 canary still absent from Logcat output despite added test surface.

## Plugin Chain Wiring (Plan 02-04 + Plan 02-05 contract delivered)

Plan 02-04 published `HttpClientFactory.cookiesStorageProvider: (accountId) -> CookiesStorage` as the integration point; Plan 02-05 supplies the production wiring caller-side:

```kotlin
// Production wiring (downstream — Phase 3 onwards)
val db = DatabaseFactory.create(accountId = "alice")
val factory = HttpClientFactory(
    engine = OkHttp.create() /* or Darwin */,
    cookiesStorageProvider = { id -> RoomCookiesStorage(db.cookieDao(), id) },
    credentialProvider = NoopCredentialProvider /* Phase 3: KVaultCredentialProvider */,
    redactor = HttpRequestRedactor(),
    /* baseUrl, userAgent, isDebug … */
)
val purger: AccountDataPurger = DefaultAccountDataPurger(
    httpClientFactory = factory,
    cookieDao = db.cookieDao(),
    databaseFileResolver = defaultDatabaseFileResolver(),
)
```

In Phase 2 there is no live wiring (no Koin yet, no auth UI yet) — but every collaborator now exists and is independently tested.

## Test count + Pitfall coverage

| File                                       | Cases | Pitfalls / Decisions verified                                              |
| ------------------------------------------ | ----- | -------------------------------------------------------------------------- |
| `RoomCookiesStorageSpec`                   | 6     | Round-trip; expiry filter; session cookie kept; Pitfall #7 (subdomain); Pitfall #6 (per-account); empty-name ignored |
| `RoomCookiesStorageTestAndroid` (wrappers) | 6     | Same spec, Robolectric runner — verifies AndroidSQLiteDriver path          |
| `RoomCookiesStorageTestIos` (wrappers)     | 6     | Same spec, kotlin.test — locally compile-only; runtime in CI macos-15      |
| `AccountDataPurgerTest`                    | 6     | 3-step ordering; real-purger orchestration; T-02-30 path-traversal x2; T-02-29 step failure tolerance x2 |
| **Total**                                  | **24**| All assertions exit 0 on Android JVM; iOS-side compile clean              |

Effective unique cases: 12 (6 spec functions × 2 platform wrappers + 6 standalone purger). Reported as "12 new commonTest cases" — Android JVM runs all 12; iOS Native is reserved for CI macos-15 runner.

## Task Commits

Each task was committed atomically (worktree mode, `git commit --no-verify`):

1. **Task 1: Add :core:database dep + CookieMapper + DatabaseFileResolver expect/actual** — `be809aa` (feat)
2. **Task 2: RoomCookiesStorage + AccountDataPurger** — `d082b0c` (feat)
3. **Task 3: RoomCookiesStorageSpec + AccountDataPurgerTest + Spec/Wrapper test split** — `8855e7d` (test)

_Plan metadata commit (this SUMMARY.md) follows after self-check._

## Files Created/Modified

### Created (14)

**`:core:network/commonMain` (4)**
- `core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/CookieMapper.kt` — `Cookie.toEntity(accountId, defaultDomain)` + `CookieEntity.toKtorCookie()`
- `core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/RoomCookiesStorage.kt` — `class RoomCookiesStorage(dao, accountId, clock) : CookiesStorage`
- `core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/AccountDataPurger.kt` — `interface AccountDataPurger` + `class DefaultAccountDataPurger`
- `core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/DatabaseFileResolver.kt` — `expect interface DatabaseFileResolver { suspend fun delete(accountId): Boolean }` + `expect fun defaultDatabaseFileResolver(): DatabaseFileResolver`

**`:core:network/{androidMain,iosMain}` (2)**
- `core/network/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/DatabaseFileResolver.android.kt` — `actual interface` + `internal class AndroidDatabaseFileResolver` (Context.getDatabasePath + WAL sidecars)
- `core/network/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/DatabaseFileResolver.ios.kt` — `actual interface` + `internal class IosDatabaseFileResolver` (NSFileManager + WAL sidecars)

**`:core:network/commonTest` (3)**
- `core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/InMemoryJournalDatabase.kt` — `expect fun newInMemoryJournalDatabase(): JournalDatabase`
- `core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/RoomCookiesStorageSpec.kt` — 6 spec functions consuming `CookieDao` parameter
- `core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/AccountDataPurgerTest.kt` — 6 cases over real HttpClientFactory + StubCookieDao + StubFileResolver

**`:core:network/{androidUnitTest,iosTest}` (5)**
- `core/network/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/InMemoryJournalDatabase.android.kt` — Android JVM actual (Robolectric ApplicationProvider + AndroidSQLiteDriver)
- `core/network/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/RoomCookiesStorageTestAndroid.kt` — `@RunWith(RobolectricTestRunner::class) @Config(sdk=[33])` wrapper, 6 `@Test` methods delegating to spec
- `core/network/src/androidUnitTest/resources/robolectric.properties` — `sdk=33`
- `core/network/src/iosTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/InMemoryJournalDatabase.ios.kt` — iOS Native actual (BundledSQLiteDriver + Dispatchers.Default — `Dispatchers.IO` is internal on Native)
- `core/network/src/iosTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/RoomCookiesStorageTestIos.kt` — kotlin.test wrapper, 6 `@Test` methods delegating to spec

### Modified (1)

- `core/network/build.gradle.kts`
  - **commonMain**: `+ implementation(project(":core:database"))`
  - **commonTest**: `+ implementation(libs.androidx.room.runtime); + implementation(libs.androidx.sqlite.bundled)` — Room runtime is `implementation` in `:core:database`, not transitive to consumers; needed for `Room.inMemoryDatabaseBuilder` and the `RoomDatabase` supertype reference for `JournalDatabase`.
  - **androidUnitTest**: `+ implementation(libs.robolectric); + implementation(libs.androidx.test.ext.junit); + implementation(libs.androidx.sqlite.framework)` — Robolectric for synthetic Application context; sqlite-framework for the AndroidSQLiteDriver Plan-02-03 deviation (BundledSQLiteDriver JNI cannot load under Robolectric).

## Decisions Made

See `key-decisions:` block in frontmatter. Highlights:

- **`expect interface DatabaseFileResolver` (not expect class)** — keeps the production class internal (`AndroidDatabaseFileResolver` / `IosDatabaseFileResolver`) and exposes only the contract via `defaultDatabaseFileResolver()`. Cleanest match to D-19 expect/actual when the contract is a single suspend method.
- **Spec/Wrapper test layout repeated from Plan 02-03** — direct commonTest open class would be picked up by JUnit 4 on Android JVM without Robolectric runner, causing `setUp()` to crash. Explicit `@RunWith` wrapper + spec object is the proven pattern. Same applies to `:core:network` here.
- **No Mokkery in `AccountDataPurgerTest`** — Mokkery's `everySuspend.calls { args -> ... }` pattern obscures sequential-side-effect ordering. Explicit `StubCookieDao` + `StubFileResolver` with `mutableListOf<String>` recording reads cleaner and matches existing `AversAuthInterceptorTest.StubCredentialProvider`. Saves a Mokkery-only abstraction layer for ordering-sensitive specs.
- **Real `HttpClientFactory` in tests (instead of mocking)** — factory has private state (`clients` map, `mutex`); building it over `MockEngine` is cheap and exercises the actual `evict` path. Ordering assertions use a small `recordingPurger` composition wrapper rather than touching factory internals.
- **Smart-cast cross-module workaround** — `it.expiresAtEpochMillis > nowMillis` failed: `CookieEntity.expiresAtEpochMillis` is a public-API property declared in `:core:database`, so cross-module smart-cast is rejected. Fixed by destructuring into a local `val expiry = row.expiresAtEpochMillis`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] commonTest `inMemoryDatabaseBuilder` cannot work uniformly across platforms**
- **Found during:** Task 3 planning (before writing test files)
- **Issue:** Plan Step 3.1 wrote `db = inMemoryDatabaseBuilder().setDriver(BundledSQLiteDriver()).build()` directly in commonTest. This breaks on Android JVM because (a) `BundledSQLiteDriver` JNI cannot load under Robolectric (`UnsatisfiedLinkError` — Plan 02-03 already documented this), and (b) `Room.inMemoryDatabaseBuilder<JournalDatabase>()` no-arg overload is iOS-Native-only; the Android variant requires a `Context` parameter.
- **Fix:** Adopted the Plan 02-03 Spec/Wrapper test pattern verbatim:
  - `expect fun newInMemoryJournalDatabase(): JournalDatabase` in commonTest.
  - `actual fun` in androidUnitTest using `Room.inMemoryDatabaseBuilder<JournalDatabase>(ApplicationProvider.getApplicationContext()).setDriver(AndroidSQLiteDriver())…build()`.
  - `actual fun` in iosTest using `Room.inMemoryDatabaseBuilder<JournalDatabase>().setDriver(BundledSQLiteDriver())…build()`.
  - `RoomCookiesStorageSpec` object in commonTest with 6 spec functions taking `CookieDao` parameter.
  - `RoomCookiesStorageTestAndroid` (`@RunWith(RobolectricTestRunner::class)`) and `RoomCookiesStorageTestIos` (kotlin.test) wrappers.
- **Files modified:** All Task 3 files (test layout — 8 files instead of plan's 1).
- **Verification:** `./gradlew :core:network:test` BUILD SUCCESSFUL (12 cases, both Debug + Release variants); `compileTestKotlinIosX64` BUILD SUCCESSFUL.
- **Committed in:** `8855e7d` (Task 3 commit).

**2. [Rule 1 - Bug] Smart-cast cross-module impossible on `CookieEntity.expiresAtEpochMillis`**
- **Found during:** Task 2 build verification
- **Issue:** `RoomCookiesStorage.kt:44 — rows.filter { it.expiresAtEpochMillis == null || it.expiresAtEpochMillis > nowMillis }` failed with `Smart cast to 'Long' is impossible, because 'expiresAtEpochMillis' is a public API property declared in different module.` Kotlin's smart-cast cannot rely on cross-module property invariance (the property could become non-null at any time from a different compilation unit's perspective).
- **Fix:** Destructured into a local immutable val: `rows.filter { row -> val expiry = row.expiresAtEpochMillis; expiry == null || expiry > nowMillis }`.
- **Files modified:** `core/network/src/commonMain/kotlin/.../cookies/RoomCookiesStorage.kt`
- **Verification:** `./gradlew :core:network:assemble :core:network:compileKotlinIosX64` BUILD SUCCESSFUL.
- **Committed in:** `d082b0c` (Task 2 commit).

**3. [Rule 3 - Blocking] Room runtime not transitive from `:core:database` (implementation, not api)**
- **Found during:** Task 3 first test compile (`./gradlew :core:network:compileDebugUnitTestKotlinAndroid`)
- **Issue:** `Cannot access 'RoomDatabase' which is a supertype of 'JournalDatabase'` and `Unresolved reference 'Room'`. `:core:database/build.gradle.kts` declares `implementation(libs.androidx.room.runtime)` (not `api`), so Room types are not on the classpath of consumers' tests. The `JournalDatabase` superclass `RoomDatabase` is also unreachable.
- **Fix:** Added `implementation(libs.androidx.room.runtime)` + `implementation(libs.androidx.sqlite.bundled)` to `:core:network/build.gradle.kts` `commonTest.dependencies` block. Propagates correctly to all platform test source sets via the default hierarchy template. Did NOT change `:core:database` to `api` — keeping consumers explicit about their Room dependency is healthier (compile-time signal when adding Room API surface to a test).
- **Files modified:** `core/network/build.gradle.kts`
- **Verification:** `./gradlew :core:network:compileDebugUnitTestKotlinAndroid` BUILD SUCCESSFUL.
- **Committed in:** `8855e7d` (Task 3 commit).

**4. [Rule 3 - Blocking] `iosTest` source set lookup via `getting`**
- **Found during:** Task 3 first build.gradle.kts addition (intermediate state)
- **Issue:** Initial fix for #3 added `val iosTest by getting { dependencies { … } }` block — failed with `KotlinSourceSet with name 'iosTest' not found`. The `applyDefaultHierarchyTemplate` builds `iosTest` as an intermediate aggregator; while it _exists_ as a parent of `iosX64Test/iosArm64Test/iosSimulatorArm64Test`, it's not registered in `sourceSets` map by that bare name (matches `:core:database/build.gradle.kts` precedent — that build.gradle.kts also configures only `androidUnitTest by getting`, never `iosTest`).
- **Fix:** Moved Room runtime + sqlite-bundled into `commonTest.dependencies` (transitive to all platform test sources via hierarchy template), removed the explicit `iosTest by getting` block. Single source of truth for "Room is needed in tests".
- **Files modified:** `core/network/build.gradle.kts`
- **Verification:** Same as #3.
- **Committed in:** `8855e7d` (Task 3 commit).

---

**Total deviations:** 4 auto-fixed (3 Rule 3 - Blocking, 1 Rule 1 - Bug). All four were necessary for correctness or build-time validity. The Spec/Wrapper test layout (#1) is the most material — it changes the test file count from "1" in the plan to "8" in reality, but the **set of behaviours verified is identical** (6 round-trip / expiry / subdomain / cross-account / empty-name + 6 purger ordering / regex / failure-tolerance — exactly the plan's behaviour list). No scope creep.

## Threat Surface Scan

No new threat surface introduced beyond the plan's `<threat_model>` (T-02-26 through T-02-31). All identified mitigations are in place:

- **T-02-26** (cookies for accountId B returned when storage scoped to A) — `RoomCookiesStorage` constructor takes `accountId`; every DAO call passes it; `RoomCookiesStorageSpec.crossAccountIsolationPitfall6` verifies.
- **T-02-27** (expired session cookies leaked) — `RoomCookiesStorage.get` filters `expiresAtEpochMillis == null || expiry > now`; `RoomCookiesStorageSpec.getFiltersExpiredCookies` verifies.
- **T-02-28** (cookie domain mismatch / subdomain) — DAO query `:host LIKE '%' || domain` (Plan 02-03); `RoomCookiesStorageSpec.getMatchesSubdomainPitfall7` verifies.
- **T-02-29** (purge fails part-way) — Each step in `DefaultAccountDataPurger` wrapped in try/catch with Kermit log; subsequent steps still run; `AccountDataPurgerTest.purge_continues_after_dao_delete_throws` + `..._does_not_propagate_file_delete_failure` verify.
- **T-02-30** (path traversal via accountId) — `accountIdRegex = ^[A-Za-z0-9_-]+$`; `AccountDataPurgerTest.purge_rejects_account_id_with_path_traversal` + `..._with_slash` verify.
- **T-02-31** (purge while client mid-request) — `factory.evict` runs first (Step 1) before any DAO/file operation; in-flight requests cancel via Ktor client close.

## User Setup Required

None — no external service configuration required. (Phase 6 TestFlight setup deferred per ROADMAP §Phase 2 success criteria.)

## Wave 3 Status

This is the first Wave 3 plan (Plans 02-06, 02-07, 02-08, 02-09 are pending). Plan 02-05 publishes the cookie-storage + account-purge primitives that downstream plans consume:

- Plan 02-06 (`:core:api-avers-v4` endpoint contract): wires real `loginCall` lambda into `HttpClientFactory`; the `RoomCookiesStorage` from this plan automatically receives `Set-Cookie` from AVERS' login response and persists it.
- Plan 02-07 (kill-switch wiring): independent — does not depend on Plan 02-05.
- Plan 02-08 (CI iosX64Test runtime): will run our `RoomCookiesStorageTestIos` + `AccountDataPurgerTest` on macos-15 — locally we only have compile-time validation.
- Plan 02-09 (final integration smoke): exercises the full `HttpClientFactory.forAccount` → `RoomCookiesStorage.addCookie` → `AversAuthInterceptor` chain.

## Next Phase Readiness

- **Plan 02-06 (`:core:api-avers-v4`):** ready — `HttpClientFactory.cookiesStorageProvider` integration point is now backed by `{ id -> RoomCookiesStorage(db.cookieDao(), id) }`. Plan 02-06's `loginCall` lambda is the last missing piece.
- **Phase 3 logout:** ready — `AccountDataPurger.purge(accountId)` is the locked wipe API; Phase 3 just composes `KVault.removeCreds(accountId) + AccountDataPurger.purge(accountId)` after the user taps "Logout".
- **Phase 5 multi-account "Delete Account" UX:** ready — same `AccountDataPurger.purge(accountId)` contract used per-account.
- **No blockers for the remaining Wave 3 plans.**

## Self-Check: PASSED

Verified before signing off:

```bash
# Created files (14)
[ -f core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/RoomCookiesStorage.kt ] && echo FOUND
[ -f core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/CookieMapper.kt ] && echo FOUND
[ -f core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/AccountDataPurger.kt ] && echo FOUND
[ -f core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/DatabaseFileResolver.kt ] && echo FOUND
[ -f core/network/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/DatabaseFileResolver.android.kt ] && echo FOUND
[ -f core/network/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/DatabaseFileResolver.ios.kt ] && echo FOUND
[ -f core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/InMemoryJournalDatabase.kt ] && echo FOUND
[ -f core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/RoomCookiesStorageSpec.kt ] && echo FOUND
[ -f core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/lifecycle/AccountDataPurgerTest.kt ] && echo FOUND
[ -f core/network/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/InMemoryJournalDatabase.android.kt ] && echo FOUND
[ -f core/network/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/RoomCookiesStorageTestAndroid.kt ] && echo FOUND
[ -f core/network/src/androidUnitTest/resources/robolectric.properties ] && echo FOUND
[ -f core/network/src/iosTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/InMemoryJournalDatabase.ios.kt ] && echo FOUND
[ -f core/network/src/iosTest/kotlin/io/github/chudoxl/linteh/journal/core/network/cookies/RoomCookiesStorageTestIos.kt ] && echo FOUND

# Modified
git diff fb2e665..HEAD -- core/network/build.gradle.kts | head -20  # changes present

# Commits
git log --oneline | grep -q be809aa && echo COMMIT-1-FOUND
git log --oneline | grep -q d082b0c && echo COMMIT-2-FOUND
git log --oneline | grep -q 8855e7d && echo COMMIT-3-FOUND
```

All expected outputs returned. Build and test verifications green:

- `./gradlew :core:network:assemble` — BUILD SUCCESSFUL
- `./gradlew :core:network:test` — BUILD SUCCESSFUL (12 new cases + 15 pre-existing = 27 total, both Debug + Release variants)
- `./gradlew :core:network:compileKotlinIosX64` — BUILD SUCCESSFUL
- `./gradlew :core:network:compileTestKotlinIosX64` — BUILD SUCCESSFUL
- `bash tests/log-redactor-canary.sh` — PASS (canary `kanareyka_PASSWORD_DO_NOT_LEAK_42` absent from test stdout, D-28 regression-clean)

---

*Phase: 02-api-reverse-engineering-network-layer*
*Plan: 05*
*Completed: 2026-04-29*
