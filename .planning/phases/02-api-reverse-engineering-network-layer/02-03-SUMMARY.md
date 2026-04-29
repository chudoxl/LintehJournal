---
phase: 02-api-reverse-engineering-network-layer
plan: 03
subsystem: database
tags: [room, ksp, kmp, sqlite, expect-actual, robolectric, nsfileprotectioncomplete, allowbackup, cookie-persistence]

# Dependency graph
requires:
  - phase: 02-api-reverse-engineering-network-layer
    provides: ":core:database module skeleton (lintech-kmp + per-target KSP + schemaDirectory + allowBackup=false manifest); Room/SQLite versions registered in libs.versions.toml; :core:platform module with internal applicationContextHolder pattern"
provides:
  - "JournalDatabase @Database(version=1, entities=[CookieEntity::class]) in commonMain — root Room DB for one account"
  - "CookieEntity with composite PK (accountId, name, domain, path) — RFC 6265 cookie identity + per-account scope (D-22)"
  - "CookieDao — upsert + findFor (RFC 6265 subdomain-suffix LIKE matching, Pitfall #7) + deleteByAccount + deleteByDomain + countForAccount"
  - "expect/actual DatabaseFactory.create(accountId) — D-19 factory pattern; Android uses Context.getDatabasePath + BundledSQLiteDriver; iOS uses NSDocumentDirectory + NSFileProtectionComplete on parent dir + BundledSQLiteDriver"
  - "expect object JournalDatabaseConstructor : RoomDatabaseConstructor — Room KMP standard (KSP-generated actuals, no manual stubs)"
  - "Schema v1 exported to core/database/schemas/<dbname>/1.json (committed) — migration parity baseline for Phase 3 v1→v2"
  - "ApplicationContextProvider.kt accessor in :core:platform/androidMain — public getApplicationContext() backed by internal var holder (BL-02 module-level encapsulation preserved)"
  - "CookieDaoSpec + SchemaV1Spec — cross-platform behaviour specs (commonTest); platform test wrappers (CookieDaoTestAndroid + CookieDaoTestIos + SchemaV1TestAndroid + SchemaV1TestIos) delegate to specs (Phase 1 AppTestHelpers WR-05 pattern)"
  - "expect/actual newInMemoryDatabase() — Android JVM uses ApplicationProvider + AndroidSQLiteDriver (Robolectric-friendly); iOS Native uses no-context KMP overload + BundledSQLiteDriver"
affects:
  - "02-04 (Ktor wiring): RoomCookiesStorage adapter wraps CookieDao; consumes CookieEntity + JournalDatabase via :core:database; uses CookieDao.findFor + upsert + deleteByDomain"
  - "02-05/02-07 (AccountDataPurger / kill-switch): AccountDataPurger.purge(accountId) calls CookieDao.deleteByAccount + DatabaseFactory file-deletion semantics"
  - "Phase 3 (Auth): KVault credential storage + DB rename journal_default.db → journal_${realAccountId}.db; schema v1→v2 migration adds accounts table"
  - "Phase 4+ (Grades, Schedule, Homework, Attendance, Messages): schema v3+ migrations add domain entities; same DAO + DatabaseFactory pattern reused"
  - "Phase 5 (Multi-account): per-account JournalDatabase instances via existing factory (no factory changes needed)"
  - "Phase 6 (BGAppRefreshTask): MUST downgrade iOS file protection to NSFileProtectionCompleteUntilFirstUserAuthentication — D-20 reminder embedded in DatabaseFactory.ios.kt KDoc"

# Tech tracking
tech-stack:
  added:
    - "kotlinx-coroutines-test (libs alias) — runTest{} support in commonTest"
    - "androidx.sqlite:sqlite-framework (libs alias) — AndroidSQLiteDriver for Robolectric-friendly Android JVM tests"
    - "Kermit added to :core:database commonMain — Logger.i for DB-creation diagnostics in both DatabaseFactory actuals"
  patterns:
    - "Plan 03 Spec/Wrapper test pattern: cross-platform behavioural assertions in commonTest object (CookieDaoSpec/SchemaV1Spec); platform-specific @Test wrappers in androidUnitTest (Robolectric @RunWith) and iosTest (kotlin.test) delegate to spec functions — direct adaptation of Phase 1 AppTestHelpers WR-05 pattern"
    - "Driver-by-target pattern: BundledSQLiteDriver for production + iOS Native tests; AndroidSQLiteDriver for Android JVM (Robolectric) tests — sqlite-framework dependency only in androidUnitTest source set, never bleeds into production runtime"
    - "Internal-holder cross-file accessor: `internal var` holder in UrlOpener.android.kt + public accessor function in ApplicationContextProvider.kt (same source set) — preserves BL-02 module-level encapsulation while allowing :core:database to read context without re-introducing top-level mutable globals"
    - "expect object JournalDatabaseConstructor (no manual actuals) — Room 2.8 KMP compiler/KSP generates per-target actuals automatically; @Suppress(\"NO_ACTUAL_FOR_EXPECT\") is the documented workaround"

key-files:
  created:
    - "core/database/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/database/JournalDatabase.kt"
    - "core/database/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/database/JournalDatabaseConstructor.kt"
    - "core/database/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/database/DatabaseFactory.kt"
    - "core/database/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/database/entity/CookieEntity.kt"
    - "core/database/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/database/dao/CookieDao.kt"
    - "core/database/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/database/DatabaseFactory.android.kt"
    - "core/database/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/database/DatabaseFactory.ios.kt"
    - "core/database/schemas/io.github.chudoxl.linteh.journal.core.database.JournalDatabase/1.json"
    - "core/database/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/database/InMemoryRoom.kt"
    - "core/database/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/database/CookieDaoSpec.kt"
    - "core/database/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/database/SchemaV1Spec.kt"
    - "core/database/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/core/database/InMemoryRoom.android.kt"
    - "core/database/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/core/database/CookieDaoTestAndroid.kt"
    - "core/database/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/core/database/SchemaV1TestAndroid.kt"
    - "core/database/src/androidUnitTest/resources/robolectric.properties"
    - "core/database/src/iosTest/kotlin/io/github/chudoxl/linteh/journal/core/database/InMemoryRoom.ios.kt"
    - "core/database/src/iosTest/kotlin/io/github/chudoxl/linteh/journal/core/database/CookieDaoTestIos.kt"
    - "core/database/src/iosTest/kotlin/io/github/chudoxl/linteh/journal/core/database/SchemaV1TestIos.kt"
    - "core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/ApplicationContextProvider.kt"
  modified:
    - "core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt — visibility of applicationContextHolder lifted from `private` to `internal`"
    - "core/database/build.gradle.kts — added Kermit to commonMain; added kotlinx-coroutines-test to commonTest; added Robolectric + androidx-test-ext-junit + androidx-sqlite-framework to androidUnitTest"
    - "gradle/libs.versions.toml — added kotlinx-coroutines-test + androidx-sqlite-framework aliases"

key-decisions:
  - "Used Dispatchers.Default for setQueryCoroutineContext on iOS — Dispatchers.IO is internal on Kotlin/Native and unavailable in iosMain; Default is acceptable per Room KMP guidance and matches the no-IO-class semantics of iOS Native runtime"
  - "Adopted Spec/Wrapper test split (CookieDaoSpec + platform @Test wrappers) over open-class inheritance — JUnit 4 picks up open superclass directly without RunWith, causing setUp() to fail on commonTest classes; explicit platform classes with @RunWith(RobolectricTestRunner) for Android + plain kotlin.test for iOS keep test infrastructure local to each platform"
  - "Switched Android JVM tests to AndroidSQLiteDriver via androidx.sqlite:sqlite-framework — BundledSQLiteDriver's JNI .so cannot load under Robolectric (UnsatisfiedLinkError); sqlite-framework lives only in androidUnitTest scope, never bleeds into production runtime"
  - "JournalDatabaseConstructor declared as expect object without manual actuals — Room 2.8 KMP KSP generates actuals automatically; @Suppress(\"NO_ACTUAL_FOR_EXPECT\") is the documented workaround. Verified: KSP-generated JournalDatabaseConstructor.kt found in build/generated/ksp/android/androidDebug after Task 2 build"
  - "Refactored RFC 6265 subdomain test cookie path to '/' (instead of default '/journal') — original plan would have failed because the LIKE-prefix path matching is strict (cookie path = '/journal' does NOT match request path = '/'); test now correctly verifies sticky session cookies attached to parent domain with root path"
  - "ApplicationContextProvider.kt as a separate file rather than extending UrlOpener.android.kt — keeps the public accessor visually independent of the URL-opening concern; future Phase 3+ consumers (KVault, WorkManager) can find it by filename without grep through UrlOpener"

patterns-established:
  - "Spec/Wrapper test split for KMP modules — commonTest spec object (per-platform @Test wrapper delegates), preserves Phase 1 WR-05 helper pattern at module level"
  - "Driver-by-target Room test infrastructure — production uses BundledSQLiteDriver everywhere; tests use AndroidSQLiteDriver on Android JVM (Robolectric-compatible) and BundledSQLiteDriver on iOS Native; achieved purely through actual implementations, no expect/actual driver indirection in production code"
  - "Public accessor + internal holder split — when a Phase N+1 module needs to read a Phase N module-private state, expose a small accessor file in the original module's source set rather than re-introducing global mutable state in the consumer module"

requirements-completed: [SUCCESS-3, D-15, D-16, D-17, D-18, D-19, D-20, D-21, D-22]

# Metrics
duration: ~50min
completed: 2026-04-29
---

# Phase 02 Plan 03: Room Schema v1 — Cookies Persistence Foundation Summary

**Working `:core:database` Room KMP module with `JournalDatabase` v1 (single `cookies` table, composite PK, RFC 6265 subdomain-aware DAO), `expect/actual DatabaseFactory` enforcing iOS NSFileProtectionComplete + Android per-app file path, and Spec/Wrapper test split running 8 commonTest assertions on Android JVM (Robolectric + AndroidSQLiteDriver) plus iOS Native (compileKotlinIosX64 verified locally; iosX64Test reserved for CI macos-15).**

## Performance

- **Duration:** ~50 min (start ~2026-04-29T13:01Z, end ~2026-04-29T13:51Z)
- **Started:** 2026-04-29T13:01:00Z (worktree branch reset to base 23a84ee)
- **Completed:** 2026-04-29T13:51:00Z (Task 3 final test re-run)
- **Tasks:** 3 / 3 (Task 1 + Task 2 + Task 3 TDD)
- **Files modified:** 22 (19 created, 3 modified)
- **Plan-level commits:** 3 (one per task)

## Accomplishments

- **Schema v1 in production:** `JournalDatabase` with `version=1`, `entities=[CookieEntity::class]`, `exportSchema=true`. Composite PK `(accountId, name, domain, path)` enforces RFC 6265 cookie identity + per-account scope (D-22). Room KSP generates `JournalDatabase_Impl.kt` + `CookieDao_Impl.kt` + `JournalDatabaseConstructor.kt` for the Android target; iOS targets generate equivalents during `compileKotlinIosX64`. Schema v1 JSON exported and committed for Phase 3 v1→v2 migration parity.
- **DAO contract proven on Android JVM (Robolectric):** 8 `@Test` assertions across `CookieDaoTestAndroid` (6) + `SchemaV1TestAndroid` (2) — upsert+find, replace-on-PK, RFC 6265 subdomain LIKE match (Pitfall #7), path prefix match, per-account isolation `deleteByAccount` (Pitfall #6), cross-account `findFor` invariant (D-22), DAO presence, 8-field round trip. `./gradlew :core:database:test` passes deterministically.
- **iOS Native compile-time validation:** `./gradlew :core:database:compileKotlinIosX64` + `compileTestKotlinIosX64` BUILD SUCCESSFUL. The `iosX64Test` runtime execution requires macOS — Plan 02-08 will wire it into CI's macos-15 job; locally on Linux Mint the compile-time check is the strongest signal we have (matches Phase 1 protocol).
- **iOS file protection enforced:** `DatabaseFactory.ios.kt` calls `NSFileManager.setAttributes(NSFileProtectionKey to NSFileProtectionComplete)` on the Documents directory before `Room.databaseBuilder` opens the DB file (D-20). KDoc embeds the Phase 6 reminder to downgrade to `completeUntilFirstUserAuthentication` when BGAppRefreshTask reads cookies on a locked device.
- **Android no-backup invariant inherited from Plan 01:** `:core:database/src/androidMain/AndroidManifest.xml` (`allowBackup="false"` + `tools:replace`) created in Plan 01 is unchanged; manifest merger folds it into `:composeApp` final manifest at app build time.
- **Per-account architecture invariant operationalised:** `DatabaseFactory.create(accountId)` resolves `journal_${accountId}.db` (Phase 2 placeholder = "default", Phase 3 rebinds to real ID via AccountDataPurger orchestration). `CookieDao.deleteByAccount(accountId)` is the wipe primitive; cross-account `findFor` test proves the WHERE-clause filter blocks cookie leak at the query level.
- **`:core:platform` accessor extension preserves BL-02:** `applicationContextHolder` visibility lifted `private` → `internal` only — still hidden outside `:core:platform` module. New `ApplicationContextProvider.kt` exposes `fun getApplicationContext(): Context` for `:core:database/DatabaseFactory.android.kt` consumption. No new top-level mutable globals.

## Task Commits

Each task was committed atomically (worktree mode — `git commit --no-verify`):

1. **Task 1: Expose getApplicationContext() from :core:platform/androidMain** — `c56bc4f` (feat)
2. **Task 2: Implement JournalDatabase v1 schema with CookieEntity + CookieDao** — `639e894` (feat)
3. **Task 3: Add CookieDaoSpec + SchemaV1Spec with platform test wrappers** — `f932d55` (test)

_Plan metadata commit (this SUMMARY.md) follows after self-check._

## Files Created/Modified

### Created (19)

**`:core:platform/androidMain` (1)**
- `core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/ApplicationContextProvider.kt` — public accessor `fun getApplicationContext(): Context`; reads the same `internal var applicationContextHolder` as UrlOpener.android.kt; throws IllegalStateException if accessed before `Application.onCreate()` initialiser ran

**`:core:database/commonMain` (5)**
- `core/database/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/database/JournalDatabase.kt` — `@Database(version=1, entities=[CookieEntity::class], exportSchema=true)` + `@ConstructedBy(JournalDatabaseConstructor::class)`; `abstract fun cookieDao(): CookieDao`
- `core/database/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/database/JournalDatabaseConstructor.kt` — `expect object JournalDatabaseConstructor : RoomDatabaseConstructor<JournalDatabase>` with `@Suppress("NO_ACTUAL_FOR_EXPECT")`; KSP generates per-target actuals
- `core/database/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/database/DatabaseFactory.kt` — `expect object DatabaseFactory { fun create(accountId: String): JournalDatabase }`; KDoc embeds Phase 3+ rebind plan and Phase 6 NSFileProtection downgrade reminder
- `core/database/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/database/entity/CookieEntity.kt` — `@Entity(tableName="cookies", primaryKeys=["accountId","name","domain","path"])` data class with 8 fields
- `core/database/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/database/dao/CookieDao.kt` — `@Dao interface` with `@Upsert upsert`, `findFor` (LIKE subdomain + path prefix), `deleteByAccount` (returns Int), `deleteByDomain`, `countForAccount` (test helper)

**`:core:database/{androidMain, iosMain}` (2)**
- `core/database/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/database/DatabaseFactory.android.kt` — `actual object DatabaseFactory`; uses `getApplicationContext().getDatabasePath("journal_${accountId}.db")` + `BundledSQLiteDriver` + `Dispatchers.IO`
- `core/database/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/database/DatabaseFactory.ios.kt` — `@OptIn(ExperimentalForeignApi::class) actual object DatabaseFactory`; reads `NSDocumentDirectory`, sets `NSFileProtectionComplete` on parent dir via `NSFileManager.setAttributes`, then `Room.databaseBuilder<JournalDatabase>(name = dbPath)` + `BundledSQLiteDriver` + `Dispatchers.Default`

**Schema export (1)**
- `core/database/schemas/io.github.chudoxl.linteh.journal.core.database.JournalDatabase/1.json` — Room schema v1 baseline (committed for migration parity); fields with affinities, primary key columns, room_master_table identity hash

**`:core:database/commonTest` (3)**
- `core/database/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/database/InMemoryRoom.kt` — `expect fun newInMemoryDatabase(): JournalDatabase`
- `core/database/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/database/CookieDaoSpec.kt` — `object CookieDaoSpec` with 6 spec functions consuming `CookieDao` parameter
- `core/database/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/database/SchemaV1Spec.kt` — `object SchemaV1Spec` with 2 spec functions consuming `JournalDatabase` parameter

**`:core:database/{androidUnitTest, iosTest}` test wrappers + actuals (6)**
- `core/database/src/androidUnitTest/kotlin/.../InMemoryRoom.android.kt` — `actual fun newInMemoryDatabase()`; `Room.inMemoryDatabaseBuilder + ApplicationProvider + AndroidSQLiteDriver + Dispatchers.IO`
- `core/database/src/androidUnitTest/kotlin/.../CookieDaoTestAndroid.kt` — `@RunWith(RobolectricTestRunner::class) @Config(sdk=[33])`; 6 `@Test` methods delegate to `CookieDaoSpec`
- `core/database/src/androidUnitTest/kotlin/.../SchemaV1TestAndroid.kt` — `@RunWith(RobolectricTestRunner::class) @Config(sdk=[33])`; 2 `@Test` methods delegate to `SchemaV1Spec`
- `core/database/src/androidUnitTest/resources/robolectric.properties` — `sdk=33`
- `core/database/src/iosTest/kotlin/.../InMemoryRoom.ios.kt` — `actual fun newInMemoryDatabase()`; `Room.inMemoryDatabaseBuilder<JournalDatabase>() + BundledSQLiteDriver + Dispatchers.Default`
- `core/database/src/iosTest/kotlin/.../CookieDaoTestIos.kt` + `SchemaV1TestIos.kt` — kotlin.test `@Test` methods delegating to specs (no `@RunWith` — Kotlin/Native test runner picks them up directly)

### Modified (3)

- `core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt` — visibility of `applicationContextHolder` lifted from `private` to `internal` (single line + KDoc note explaining the rationale)
- `core/database/build.gradle.kts` — added `libs.kermit` to commonMain; added `commonTest.dependencies { libs.kotlinx-coroutines-test }`; added `androidUnitTest.dependencies { robolectric, androidx-test-ext-junit, androidx-sqlite-framework }`
- `gradle/libs.versions.toml` — added `kotlinx-coroutines-test` alias (version.ref = "kotlinxCoroutines"); added `androidx-sqlite-framework` alias (version.ref = "sqlite")

## Decisions Made

1. **Plain `expect object DatabaseFactory` (no `expect class JournalDatabase`)** — followed RESEARCH.md's anti-pattern callout: `expect class JournalDatabase` "fights Koin/DI" and is fragile around Room compiler-plugin generation. Plain class + `expect object DatabaseFactory + expect object JournalDatabaseConstructor` is the documented Room 2.8 KMP standard.
2. **`@Suppress("NO_ACTUAL_FOR_EXPECT")` on JournalDatabaseConstructor + no manual actual stubs** — Room 2.8 KMP KSP generates per-target actuals during `kspDebugKotlinAndroid` / `kspKotlinIosX64` etc. Manual stubs (per the plan's fallback Step 2.5/2.6) would either duplicate the body (compiler error) or cause body-mismatch warnings. Verified: KSP output `build/generated/ksp/android/androidDebug/.../JournalDatabaseConstructor.kt` exists after Task 2 assemble.
3. **Spec/Wrapper test split** — see Deviations / Issues below; in short, kotlin.test on Android JVM picks up `open class` `@Test` methods directly via JUnit 4, bypassing the `@RunWith(RobolectricTestRunner)` on the platform subclass. Solution: extract spec functions into a `commonTest object`, and have explicit `@Test` wrappers per platform delegate to spec functions.
4. **AndroidSQLiteDriver for Android JVM tests** — BundledSQLiteDriver's JNI `.so` cannot load under Robolectric. Switched to `AndroidSQLiteDriver` from `androidx.sqlite:sqlite-framework` for the Android JVM test path only. Production runtime + iOS Native tests retain `BundledSQLiteDriver`.
5. **`Dispatchers.Default` for iOS query context** — `Dispatchers.IO` is `internal` on Kotlin/Native (Pitfall not enumerated in RESEARCH but well-known KMP gotcha). Future Phase 4+ may introduce an `expect val ioDispatcher: CoroutineDispatcher` in `:core:platform` if other modules need IO-class semantics on iOS.
6. **`internal var` (not `lateinit var public`)** for `applicationContextHolder` — preserves BL-02 module-level encapsulation. Downstream modules (`:composeApp`, `:feature:*`) cannot reassign nor read; only files in `:core:platform/androidMain` can.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `Dispatchers.IO` not available on Kotlin/Native**
- **Found during:** Task 2 (Step 2.10 build verification — `./gradlew :core:database:assemble`)
- **Issue:** `DatabaseFactory.ios.kt` used `Dispatchers.IO` per the plan — but `Dispatchers.IO` is `internal` on Kotlin/Native and unavailable in `iosMain`. Build error: `Cannot access 'val IO: CoroutineDispatcher': it is internal in 'kotlinx.coroutines.Dispatchers'.`
- **Fix:** Replaced `Dispatchers.IO` with `Dispatchers.Default` in `DatabaseFactory.ios.kt`; added inline comment + KDoc justification. Android-side keeps `Dispatchers.IO`.
- **Files modified:** `core/database/src/iosMain/kotlin/.../DatabaseFactory.ios.kt`
- **Verification:** `./gradlew :core:database:assemble` BUILD SUCCESSFUL after fix
- **Committed in:** 639e894 (Task 2 commit; deviation noted in commit message)

**2. [Rule 1 - Bug] BundledSQLiteDriver JNI fails to load under Robolectric**
- **Found during:** Task 3 (first `./gradlew :core:database:test` run)
- **Issue:** `CookieDaoTestAndroid` runtime crashed with `UnsatisfiedLinkError: ... BundledSQLiteDriver.jvmAndroid.kt:69` and `NoClassDefFoundError`. Robolectric's classloader cannot load the bundled SQLite native library for the JVM target architecture.
- **Fix:** Added `androidx.sqlite:sqlite-framework` (alias `androidx-sqlite-framework`, version.ref = "sqlite") to `:core:database/androidUnitTest` deps. Switched `InMemoryRoom.android.kt` actual to `AndroidSQLiteDriver()` (which uses Android system SQLite via Robolectric's shadow). Production code (`DatabaseFactory.android.kt`) is unchanged — it still uses `BundledSQLiteDriver` (real device has the .so available).
- **Files modified:** `gradle/libs.versions.toml`, `core/database/build.gradle.kts`, `core/database/src/androidUnitTest/kotlin/.../InMemoryRoom.android.kt`
- **Verification:** `./gradlew :core:database:test` 8/8 PASS
- **Committed in:** f932d55 (Task 3 commit; deviation noted in commit message)

**3. [Rule 1 - Bug] `findFor_matches_subdomain_pitfall_7` test path mismatch**
- **Found during:** Task 3 (second `./gradlew :core:database:test` run, after Rule-1 fix #2)
- **Issue:** Test inserted cookie with default `path="/journal"`, then queried `host="journal.school28-kirov.ru", path="/"`. SQL `:path LIKE path || '%'` evaluates to `"/" LIKE "/journal%"` → false → 0 rows. Test asserted 1 row. The test as written asserted RFC 6265-incompliant behaviour (request path `/` is shorter than cookie path `/journal`).
- **Fix:** Adjusted the test cookie to `path="/"` (a sticky parent-domain session cookie) and queried request `path="/journal"`. Now SQL is `"/journal" LIKE "/%"` → true. Inline comment in the test explains the RFC 6265 reasoning.
- **Files modified:** `core/database/src/commonTest/kotlin/.../CookieDaoSpec.kt`
- **Verification:** `./gradlew :core:database:test` 8/8 PASS (including the now-correct subdomain test)
- **Committed in:** f932d55 (Task 3 commit)

**4. [Rule 3 - Blocking] Spec/Wrapper test split (architectural deviation from plan's open-class inheritance)**
- **Found during:** Task 3 (first test run after writing `open class CookieDaoTest` in commonTest with `class CookieDaoTestAndroid : CookieDaoTest()` Robolectric wrapper)
- **Issue:** JUnit 4 on Android JVM picks up the parent `CookieDaoTest` directly (without `@RunWith(RobolectricTestRunner)`) AND the `CookieDaoTestAndroid` subclass (with the runner). The parent class invocation lacks Robolectric Application context, so `setUp()` throws `IllegalStateException` from `ApplicationProvider.getApplicationContext()`. Plan's open-class pattern was incorrect for this test runner combination.
- **Fix:** Refactored to Spec/Wrapper pattern (Phase 1 AppTestHelpers WR-05): extracted assertions into `commonTest` objects (`CookieDaoSpec`, `SchemaV1Spec`); platform-specific test classes in androidUnitTest (`CookieDaoTestAndroid`, `SchemaV1TestAndroid` with `@RunWith`) and iosTest (`CookieDaoTestIos`, `SchemaV1TestIos` with kotlin.test) each declare 6/2 explicit `@Test` methods that delegate to spec functions. No `open class` test base; no JUnit 4 picking up bare parent.
- **Files modified:** Removed `CookieDaoTest.kt` + `SchemaV1Test.kt` (open-class, never committed); added `CookieDaoSpec.kt` + `SchemaV1Spec.kt` + 4 wrapper classes
- **Verification:** `./gradlew :core:database:test` 8/8 PASS (only Android-specific wrappers run on JVM); iOS-side compileTestKotlinIosX64 BUILD SUCCESSFUL
- **Committed in:** f932d55 (Task 3 commit; the test files in this commit reflect the final Spec/Wrapper layout — earlier failed attempts were never committed)

---

**Total deviations:** 4 auto-fixed (3 Rule 1 - Bug, 1 Rule 3 - Blocking)
**Impact on plan:** All four deviations were necessary for correctness (1+3) and infrastructure-compatibility (2+4). The Spec/Wrapper test split is materially the same final test scope as the plan's open-class approach — same 6 `CookieDao` behaviours + 2 schema invariants are verified — but with a structurally-correct test class layout. No scope creep; the planned `must_haves.truths` and `success_criteria` are all met.

## Issues Encountered

1. **Worktree branch base mismatch at agent startup** — `git merge-base HEAD <expected>` returned `4ca50fb` instead of expected `23a84ee`; HEAD was several commits ahead of the expected base (likely a parallel branch checkout artifact). Resolved per `<worktree_branch_check>` instruction by `git reset --hard 23a84eee7602db65ca26cf8c1dacc2aa6dc9c6c5`. The reset incidentally erased an early `ApplicationContextProvider.kt` write (made before the reset) — re-applied immediately as part of Task 1.

2. **`local.properties` missing in worktree** — The Linux Mint dev-host stores `sdk.dir=/home/chudoxl/Android/Sdk` in the main repo's `local.properties`, but the worktree's `local.properties` was absent (git-ignored, not transferred to worktrees). Fixed by `cp /home/chudoxl/src/LintehJournal/local.properties .` into the worktree root. This is a one-time per-worktree setup step that future parallel executors may also need.

3. **Robolectric warning about missing AndroidManifest.xml in :core:database** — During Android JVM tests, Robolectric emitted a warning: `WARNING: No manifest file found at ./AndroidManifest.xml. Falling back to the Android OS resources only.` This is benign for `:core:database` (which has no UI surface and only consumes Android Application context for Room.databaseBuilder); the manifest fragment for `allowBackup="false"` lives in `src/androidMain/AndroidManifest.xml` and is irrelevant for unit tests. Suppression via `@Config(manifest=Config.NONE)` is a future cleanup if the warning becomes noisy.

## Wave 2 Status

This is one of the two Wave 2 plans (Plan 02-04 runs in parallel in its own worktree). Plan 02-03 publishes the data interface (`JournalDatabase` + `CookieDao` + `DatabaseFactory`) that Plan 02-04 (`:core:network` Ktor wiring + RoomCookiesStorage) will consume. No coordination needed during execution; both plans share `gradle/libs.versions.toml` modifications which the orchestrator merges centrally.

## Next Phase Readiness

**Ready for Plan 02-04 (:core:network):**

- `RoomCookiesStorage : CookiesStorage` adapter can wrap `CookieDao.upsert + findFor + deleteByDomain` — DAO contract documented in `CookieDao.kt` KDoc with RFC 6265 + Pitfall #6/#7 references.
- `:core:database` artifacts assemble cleanly (Android `assemble` and iOS `compileKotlinIosX64`); KSP runs on all four targets per Pitfall #3 mitigation.
- `expect/actual DatabaseFactory.create(accountId="default")` is callable from `:core:network` once it adds `implementation(project(":core:database"))` — this dependency is already declared in the Plan 01 `:core:network/build.gradle.kts` skeleton.

**Ready for Plan 02-05 (AccountDataPurger / kill-switch):**

- `CookieDao.deleteByAccount(accountId): Int` returns row count — the wipe primitive's smoke-test signal.
- `DatabaseFactory.create(accountId)` resolves a per-account file path; AccountDataPurger.purge(accountId) needs only to: (1) call `DatabaseFactory.create(accountId).cookieDao().deleteByAccount(accountId)` (or shorter — drop the entire DB file), (2) close any cached HttpClient, (3) delete `journal_${accountId}.db` file via platform fileapi.

**Ready for Phase 3 (Auth):**

- Schema v1 baseline committed → Phase 3 v1→v2 migration adds `accounts` table; Room's `Migration` API + `room { schemaDirectory(...) }` (Plan 01) gives the test harness automatically.
- `journal_default.db` filename works as Phase 2 placeholder; Phase 3 logout-then-login flow can delete `default` and create `${realAccountId}`.

**No blockers.** Build infrastructure (per-target KSP, allowBackup manifest, schema export, Robolectric driver split) all working as designed.

## Threat Flags

None — no new security-relevant surface introduced beyond what the plan's `<threat_model>` already enumerated. The four threats T-02-13 through T-02-17 (ADB backup, NSFileProtection on locked device, schema migration, cross-account leak, wrong-DB-for-accountId) are all addressed at the implementation level:

- T-02-13: `core/database/src/androidMain/AndroidManifest.xml` (Plan 01) declares `allowBackup="false"` + `tools:replace` (verified manifest grep)
- T-02-14: `DatabaseFactory.ios.kt` calls `NSFileManager.setAttributes(NSFileProtectionKey to NSFileProtectionComplete)` before `Room.databaseBuilder` (verified by grep)
- T-02-15: Schema v1 JSON committed at `core/database/schemas/<dbname>/1.json`; Room's `Migration` API ready for Phase 3 v1→v2
- T-02-16: All `CookieDao` queries filter by `accountId`; `crossAccountFindForReturnsNoOtherAccountCookies` test asserts the WHERE-clause filter blocks leak
- T-02-17: `DatabaseFactory.create(accountId)` uses string interpolation `"journal_${accountId}.db"` in both Android and iOS actuals; AccountDataPurger (Plan 05) will unwind via the same accountId-keyed path

## Self-Check: PASSED

All 19 created files exist on disk; all 3 modified files (`UrlOpener.android.kt`, `core/database/build.gradle.kts`, `gradle/libs.versions.toml`) exist; all 3 task commits (c56bc4f, 639e894, f932d55) are present in `git log` (verified via `git log --oneline 23a84ee..HEAD`). Module assembles cleanly:

- `./gradlew :core:database:assemble` → BUILD SUCCESSFUL
- `./gradlew :core:database:test` → BUILD SUCCESSFUL (8/8 tests pass)
- `./gradlew :core:database:compileKotlinIosX64` → BUILD SUCCESSFUL
- `./gradlew :core:database:compileTestKotlinIosX64` → BUILD SUCCESSFUL
- `./gradlew :core:platform:compileDebugKotlinAndroid` → BUILD SUCCESSFUL (UrlOpener.android.kt internal modifier change verified)

Schema v1 JSON exported to `core/database/schemas/io.github.chudoxl.linteh.journal.core.database.JournalDatabase/1.json` with all 8 fields + composite PK + room_master_table identity hash.

KSP per-target generation confirmed via `find core/database/build/generated/ksp -type f`:
- `build/generated/ksp/android/androidDebug/.../JournalDatabase_Impl.kt`
- `build/generated/ksp/android/androidDebug/.../JournalDatabaseConstructor.kt`
- `build/generated/ksp/android/androidDebug/.../dao/CookieDao_Impl.kt`

(iOS targets KSP runs during `compileKotlinIosX64` etc. — verified by BUILD SUCCESSFUL on those tasks.)

---
*Phase: 02-api-reverse-engineering-network-layer*
*Plan: 03*
*Completed: 2026-04-29*
