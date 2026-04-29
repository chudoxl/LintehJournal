---
phase: 02-api-reverse-engineering-network-layer
plan: 06
subsystem: api-contract
tags: [ktor, mock-engine, har-replay, sealed-result, positional-decoder, extjs, anti-bot, kotlinx-serialization]

# Dependency graph
requires:
  - phase: 02-api-reverse-engineering-network-layer
    provides: "Plan 02-01 — :core:api-avers-v4 module skeleton + libs.versions.toml entries; Plan 02-02 — 12 sanitized HAR fixtures + auth/endpoint contract observations (ys-* cookies, JS escape() polyfill, IncompleteRead, ExtJS new Date(...) literals, [error_symbol] bad-creds body); Plan 02-04 — HttpClientFactory + AversAuthInterceptor + plugin chain; Plan 02-05 — RoomCookiesStorage + AccountDataPurger"

provides:
  - "ApiResult<T> sealed: Success<T>/Mismatch/Failure — endpoints never throw"
  - "AversApiError sealed (D-06 reconciliation): Unauthorized / AntiBotChallenge(rawHtml) / Network / ContractMismatch / Server(httpCode) / KillSwitchTriggered"
  - "AversEnvelope.unwrapAversEnvelope: handles ExtJS object {success,data} envelope + raw arrays + missing-fields Mismatch + success=false -> Server(200)"
  - "AntiBotDetector: HTML body sniff (DOCTYPE/html/captcha-form) + JsonPrimitive sniff"
  - "ExtJsArrayPreprocessor: regex strips new Date(YYYY,M-1,D,…) → ISO YYYY-MM-DD string before kotlinx-serialization"
  - "6 typed DTOs (Login/Grade/Lesson/Homework/Attendance/Message) + Period + StudentScope — positional KSerializers against AVERS array-of-arrays response shape"
  - "AversApi: 5 suspend read functions (fetchGrades/fetchSchedule/fetchHomework/fetchAttendance/fetchMessages) returning ApiResult<List<…Dto>>"
  - "AversAuthApi: login(loginValue, sha1HexPassword) -> ApiResult<LoginDto> + logout()"
  - "HarReplayMockEngine: expect/actual loadHarFile (Android JVM via fixtures.dir system property)"
  - "EndpointsContractTest (Android JVM): 12 cases — 6 endpoints × 2 accounts replay through full Ktor pipeline + sanitized HAR fixtures"

affects:
  - "Plan 02-07 (kill-switch wiring): independent — does not depend on 02-06"
  - "Plan 02-08 (CI iosX64Test runtime): receives :core:api-avers-v4 unit tests for macos-15 runtime; EndpointsContractTest stays Android-only until Plan 02-09"
  - "Plan 02-09 (final integration smoke): owns iOS Native HAR fixture bundling + iosTest variant of EndpointsContractTest"
  - "Phase 4 :core:data mapper layer: consumes typed DTOs (LoginDto, GradeDto, LessonDto, HomeworkDto, AttendanceDto, MessageDto) and the AversApi/AversAuthApi entry points; bootstrap chain (get_user_data → get_uch_year → GET_STUDENT_CLASS → GET_STUDENT_PARALLEL) lives there"
  - "Phase 4 also implements JS-escape() polyfill for ys-* cookie writing + cookie injection into RoomCookiesStorage post-login (Plan 02-02 SUMMARY constraint)"
  - "Phase 3 Auth UI: catches AversApiError.AntiBotChallenge → opens WebView with rawHtml (D-06)"

# Tech tracking
tech-stack:
  added:
    - "kotlinx-coroutines-test 1.10.2 (commonTest dep — runTest{} required by EndpointsContractTest)"
  patterns:
    - "Positional KSerializer per DTO — custom KSerializer reads JsonArray columns by index, no reflection / annotations on field positions; tolerates trailing whitespace via .trim()"
    - "ExtJsArrayPreprocessor regex pre-pass — strict-but-tolerant by stripping non-JSON ExtJS literals before parser runs"
    - "Path-only HAR matcher — MockEngine compares request.url.encodedPath to URL path of HAR entry, accommodates bare HttpClient without DefaultRequest baseUrl"
    - "Test-platform split — EndpointsContractTest in androidUnitTest only, iOS Native deferred to Plan 02-09 (avoids the iosTest/loadHarFile.ios.kt error path)"
    - "Internal serializer object exposure — `internal object FooDtoSerializer : KSerializer<FooDto>` referenced by name (not via FooDto.serializer()) when @Serializable(with = …) blocks the auto-generated companion serializer"

key-files:
  created:
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/result/ApiResult.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/result/AversApiError.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/dto/envelope/AversEnvelope.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/antibot/AntiBotDetector.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/extjs/ExtJsArrayPreprocessor.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/dto/Period.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/dto/StudentScope.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/dto/PositionalRowReader.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/dto/GradeDto.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/dto/LessonDto.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/dto/HomeworkDto.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/dto/AttendanceDto.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/dto/MessageDto.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/dto/LoginDto.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/AversApi.kt"
    - "core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/AversAuthApi.kt"
    - "core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/result/ApiResultTest.kt"
    - "core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/result/AversApiErrorTest.kt"
    - "core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/dto/envelope/AversEnvelopeTest.kt"
    - "core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/antibot/AntiBotDetectorTest.kt"
    - "core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/extjs/ExtJsArrayPreprocessorTest.kt"
    - "core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/fixtures/HarReplayMockEngine.kt"
    - "core/api-avers-v4/src/commonTest/resources/fixtures/.gitkeep"
    - "core/api-avers-v4/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/EndpointsContractTest.kt"
    - "core/api-avers-v4/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/fixtures/HarReplayMockEngine.android.kt"
    - "core/api-avers-v4/src/iosTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/fixtures/HarReplayMockEngine.ios.kt"
  modified:
    - "core/api-avers-v4/build.gradle.kts (kotlinx-coroutines-test commonTest dep + fixtures.dir system property registration)"

key-decisions:
  - "AversEndpoints URL constants rewritten to match Plan 02-02 SUMMARY HAR captures — plan's draft (/journal/getMarks etc.) was placeholder; real AVERS routes are /login, /act/GET_STUDENT_JOURNAL_DATA, /act/GET_TIMETABLE, /act/GET_STUDENT_DAIRY, /act/GET_ATT_JOURNAL_DATA, /act/get_sms (Rule 1 alignment with reverse-engineered contract)"
  - "DTOs use positional KSerializer (custom KSerializer + JsonArray decode) instead of @SerialName-driven object decoders — AVERS responses are arrays-of-arrays, NOT object envelopes; the plan's <must_haves> labels DTO field shapes 'placeholders' but tests must succeed against real fixtures, so positional decoding is necessary for Success on the 12 HAR replays"
  - "EndpointsContractTest located in androidUnitTest only (not commonTest) — iOS Native fixture loading is explicitly deferred to Plan 02-09; placing the test in commonTest would either trigger the iosTest loadHarFile error path or require @Ignore plumbing; cleanest separation is to scope the test to the Android JVM target where fixtures.dir works reliably"
  - "@Serializable(with = …) blocks auto-generated companion serializer — internal serializer objects referenced by name (e.g. ListSerializer(GradeDtoSerializer)) instead of GradeDto.serializer(); cleaner than @Serializable boilerplate that doesn't apply for positional decoders anyway"
  - "AversAuthApi.login is hash-agnostic — caller passes SHA-1 hex string (Plan 02-02 SUMMARY auth observation); SHA-1 lives in Phase 4 :core:data alongside KVault retrieval, not in the API contract module"
  - "Skip /auth two-step in Phase 2 — AversAuthApi.login only POSTs /login and parses the user-info row; the /auth + manual ys-* cookie write step is Phase 4 wiring (depends on JS-escape() polyfill helper)"
  - "StudentScope value class for per-call (classId, studentId, academicYear) — Phase 2 callers populate manually; Phase 4 mapper derives via the bootstrap chain (get_user_data → get_uch_year → GET_STUDENT_CLASS)"
  - "ExtJsArrayPreprocessor preserves only Y/M/D — drops trailing time-of-day arguments since Plan 02-02 fixtures show all zeros; Phase 4+ may extend to ISO-8601 timestamps if non-zero times appear"

patterns-established:
  - "Custom positional KSerializer template — @Serializable(with = FooSerializer::class) data class Foo(...); internal object FooSerializer : KSerializer<Foo> { override val descriptor = PrimitiveSerialDescriptor(...); override fun deserialize(decoder) = (decoder as JsonDecoder).decodeJsonElement().let { (it as JsonArray) ... }; override fun serialize() = error('read-only') }"
  - "ApiResult.runCatching wrapper — every endpoint method body is `runCatching { … }.getOrElse { e -> mapException(e, endpointName) }` — guarantees the no-throw contract"
  - "HAR fixture system-property convention — tasks.withType<Test>.systemProperty('fixtures.dir', rootProject.projectDir.resolve('fixtures/sanitized').absolutePath); JVM actuals read; iOS Native errors with TODO pointing to next plan"

requirements-completed: [SUCCESS-2, SUCCESS-3, D-06, D-08, D-09, D-11, D-23, D-24, D-25]

# Metrics
duration: ~38min
completed: 2026-04-29
---

# Phase 02 Plan 06: `:core:api-avers-v4` API Contract Summary

**Versioned AVERS endpoint contract — typed DTOs (positional decoders), sealed `ApiResult<T>` + `AversApiError` (D-06 reconciliation: `AntiBotChallenge` is a `data class` carrying `rawHtml`), ExtJS-array preprocessor for `new Date(Y,M-1,D,…)` literals, anti-bot HTML detector, and 6 endpoint suspend functions (login + 5 reads). All 12 sanitized HAR fixtures from Plan 02-02 replay green through Ktor MockEngine.**

## Performance

- **Duration:** ~38 min
- **Started:** 2026-04-29T10:54:00Z
- **Completed:** 2026-04-29T11:32:00Z
- **Tasks:** 3
- **Files created:** 25 (16 main + 9 tests)
- **Files modified:** 1 (`core/api-avers-v4/build.gradle.kts`)
- **Plan-level commits:** 3 (one per task) + 1 docs commit (this SUMMARY)

## Accomplishments

- `:core:api-avers-v4` Phase 2 skeleton fully populated with the AVERS API contract — assembles for Android (`./gradlew :core:api-avers-v4:assemble`) and iOS X64 (`./gradlew :core:api-avers-v4:compileKotlinIosX64`).
- **36 commonTest cases pass on Android JVM** (Debug + Release variants) — `./gradlew :core:api-avers-v4:test` exits 0:
  - ApiResultTest: 4 cases
  - AversApiErrorTest: 2 cases
  - AversEnvelopeTest: 5 cases
  - AntiBotDetectorTest: 8 cases
  - ExtJsArrayPreprocessorTest: 5 cases
  - EndpointsContractTest: **12 cases (6 endpoints × 2 accounts)** — full HAR replay
- **iOS X64 compileTestKotlinIosX64 — clean.** EndpointsContractTest is androidUnitTest-only, so iOS Native does not see it; runtime iOS validation is the responsibility of Plan 02-08 (general iOS test runtime) and Plan 02-09 (HAR fixture bundling for iOS Native).
- **Canary regression tests both pass:** `tests/sanitize-har-canary.sh` + `tests/log-redactor-canary.sh` exit 0. No new Cyrillic surnames in fixtures, redactor still scrubs `kanareyka_PASSWORD_DO_NOT_LEAK_42` from debug-build logs.
- **Cross-account proof intact** (D-01 → D-23 → D-24): account A (user_id 3020 / pupil_id 4028 / classId 1013) and account B (user_id 2684 / pupil_id 3661 / classId 1015) replay through the same `AversApi` instance with distinct `StudentScope`s and produce `ApiResult.Success` for all 6 endpoints each.

## AVERS endpoint URLs (verified — `AversEndpoints` in `AversApi.kt`)

| Plan endpoint | URL | HTTP | Required form params |
|---------------|-----|------|----------------------|
| login | `/login` | POST | `l` (login), `p` (sha1_hex of password) |
| auth (Phase 4) | `/auth` | POST | `uId` (user_id), `act=1` |
| logout | `/auth/logout` | GET | — |
| grades | `/act/GET_STUDENT_JOURNAL_DATA` | POST | `cls`, `student` |
| schedule | `/act/GET_TIMETABLE` | GET | — (cookie scope) |
| homework | `/act/GET_STUDENT_DAIRY` | POST | `student`, `cls`, `begin_dt` (DD.MM.YYYY), `end_dt` |
| attendance | `/act/GET_ATT_JOURNAL_DATA` | POST | `cls`, `period_begin`, `period_end` |
| messages | `/act/get_sms` | POST | `uchYear` |

## DTO field shapes (positional rows — `dto/*.kt`)

All DTOs decode from `JsonArray` columns; missing/null/out-of-bounds positions return `null` via `PositionalRowReader`. Phase 4 mapper extends or pivots as needed without API breakage.

| DTO | Source endpoint | Columns |
|-----|-----------------|---------|
| `LoginDto` | `/login` | 0=user_id, 1=user_type, 5=fio, 8=id_pupil |
| `GradeDto` | `/act/GET_STUDENT_JOURNAL_DATA` | 0=mark_id, 1=pupil_id, 2=value(string), 3=date_iso, 4=lesson_id, 6=type_code, 8=comment |
| `LessonDto` | `/act/GET_TIMETABLE` | 0=id, 1=lesson_id, 2=subject_id, 5=weekday, 6=in_day, 7=shift, 8=room |
| `HomeworkDto` | `/act/GET_STUDENT_DAIRY` | 0=date_iso, 1=lesson_id, 2=subject_id, 3=theme, 4=homework_text, 5=mark, 6=in_day, 9=teacher_id, 11=comment |
| `AttendanceDto` | `/act/GET_ATT_JOURNAL_DATA` | placeholder — endpoint returns `[]` for student role; live shape TBD when teacher fixtures captured (Plan 02-02 SUMMARY) |
| `MessageDto` | `/act/get_sms` | placeholder — endpoint returns `[]` for both captured accounts; conservative `[id, date, sender, subject, body, is_read]` ordering |

**Notes:** `value: String` for grades preserves `"5"` / `"4"` / `"Н"` / `"Б"` / `"ОСВ"` literally — Phase 4 decides display semantics. `dateString: String` is ISO `YYYY-MM-DD` after `ExtJsArrayPreprocessor` substitution; downstream callers can parse via `kotlinx-datetime LocalDate.parse`.

## D-06 reconciliation: `AntiBotChallenge` change

| Before (D-11 in CONTEXT) | After (Plan 02-06 implementation) |
|--------------------------|------------------------------------|
| `object AntiBotChallenge : AversApiError()` | `data class AntiBotChallenge(val rawHtml: String) : AversApiError()` |

This is a sealed-superset change: any caller that was matching `is AntiBotChallenge` keeps working; new callers (Phase 3 Auth UI's WebView fallback per D-06) gain access to the captcha page HTML. No API breakage.

## `AversEnvelope.unwrapAversEnvelope` behaviour table

| Input | Output |
|-------|--------|
| `{"success": true, "data": [...]}` | `Success(UnwrappedEnvelope(data))` |
| `{"success": true}` (missing data) | `Mismatch(missingFields=["data"], endpoint, rawDump)` |
| `{"data": [...]}` (missing success) | `Mismatch(missingFields=["success"], …)` |
| `{"success": false, "msg": "..."}` | `Failure(Server(httpCode=200))` |
| `[1, 2, 3]` (raw array, no envelope) | `Success(UnwrappedEnvelope(rawArray))` — dominant path for AVERS `/act/...` |
| primitive (e.g. `"ok"`) | `Success(UnwrappedEnvelope(primitive))` |

## `HarReplayMockEngine` mechanism

- Reads HAR JSON via `expect fun loadHarFile(relativePath)`.
- **Android JVM actual:** reads `File("$fixturesDir/$relativePath")` where `fixturesDir` comes from `System.getProperty("fixtures.dir")` set by Gradle:

  ```kotlin
  tasks.withType<Test>().configureEach {
      systemProperty(
          "fixtures.dir",
          rootProject.projectDir.resolve("fixtures/sanitized").absolutePath,
      )
  }
  ```

- **iOS Native actual:** raises `error("iOS Native fixture loading not implemented in Phase 2")` — Plan 02-09 owns iOS resource bundling; the actual is intentionally not connected because EndpointsContractTest is `androidUnitTest`-only.
- **Matcher:** `request.method.value` ↔ HAR `request.method` (case-insensitive); `request.url.encodedPath` ↔ HAR `request.url` path component (scheme + host stripped). First positive match in HAR temporal order wins. Misses raise a deterministic error listing all available entries — easy debugging if the AVERS contract drifts.

## Threat coverage

Plan's `<threat_model>` T-02-32 through T-02-37 — all addressed:

| Threat | Status |
|--------|--------|
| T-02-32 (parser crash on new field) | Mitigated by `Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }` + `PositionalRowReader` bounds-safe accessors |
| T-02-33 (kill-switch by mismatch storm) | Mitigated by `ApiResult.Mismatch` envelope check + Phase 6 `KillSwitchClient` (Plan 02-07) |
| T-02-34 (anti-bot HTML crashes parser) | Mitigated by `AntiBotDetector.isCaptchaBody` + Content-Type sniff in `AversApi.executeListEndpoint` |
| T-02-35 (DTO logs sensitive fields) | Accepted — Phase 2 only declares DTOs; Phase 4+ feature modules decide what to log |
| T-02-36 (login URL hard-coded) | Mitigated by centralised `AversEndpoints` constants object |
| T-02-37 (rawHtml in AntiBotChallenge leaks via logging) | Mitigated by Plan 02-04 `HttpRequestRedactor` defense-in-depth + Phase 3 WebView consumes it sandboxed |

## Task Commits

Each task was committed atomically (worktree mode, `git commit --no-verify`):

1. **Task 1: Result types + envelope + AntiBotDetector + 6 DTOs + ExtJS preprocessor** — `09b8e50` (feat)
2. **Task 2: AversApi + AversAuthApi (5 reads + login/logout) + StudentScope + AversEndpoints** — `cb5efd4` (feat)
3. **Task 3: All commonTest unit tests + HarReplayMockEngine + EndpointsContractTest + build.gradle wiring** — `2f6f057` (test)

Plan metadata commit (this SUMMARY.md) follows after self-check.

## Decisions Made

See `key-decisions:` block in frontmatter. Highlights:

- **Real AVERS URLs in `AversEndpoints`** — the plan's draft endpoint paths (`/journal/getMarks` etc.) were placeholders; Plan 02-02 SUMMARY locked the actual reverse-engineered routes (`/login`, `/act/GET_STUDENT_*`, `/act/GET_TIMETABLE`, `/act/get_sms`). Aligning the constants is a Rule 1 fix (the wrong URLs would cause every contract test to miss the HAR entry).
- **Positional KSerializers** — AVERS responses are arrays-of-arrays, not objects. Plan's `@SerialName`-driven DTOs would refuse to decode the real fixtures; positional KSerializers consuming `JsonArray` are the only way to satisfy `<must_haves>` line 51 ("EndpointsContractTest replays 12 HAR fixtures and asserts ApiResult.Success") against real Plan 02-02 captures.
- **EndpointsContractTest in androidUnitTest** — the iosTest variant of `loadHarFile` errors by design (resource bundling reserved for Plan 02-09); placing the test in commonTest would either trigger that error path or require Spec/Wrapper plumbing similar to Plan 02-05's `RoomCookiesStorageSpec`. Phase 2 takes the simpler path: the contract verification runs on Android JVM only, and Plan 02-09 promotes the test to commonTest once iOS resource access is wired.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] AversEndpoints URL constants rewritten to match Plan 02-02 HAR captures**
- **Found during:** Task 2 source authoring (cross-checking against `fixtures/sanitized/account-A/grades.har`)
- **Issue:** Plan's draft constants (`/journal/getMarks`, `/journal/getSchedule` etc.) bear no resemblance to real AVERS routes. Plan 02-02 SUMMARY documented `/login` (POST), `/act/GET_TIMETABLE` (GET), `/act/GET_STUDENT_DAIRY` (POST), `/act/GET_STUDENT_JOURNAL_DATA` (POST), `/act/GET_ATT_JOURNAL_DATA` (POST), `/act/get_sms` (POST). With the wrong URLs every EndpointsContractTest case would fail with "HAR fixture has no entry matching POST /journal/getMarks…".
- **Fix:** `AversEndpoints` object in `AversApi.kt` now references the verified Plan 02-02 URLs as documented constants with KDoc per route. The plan itself flagged this contingency at lines 1356-1358 ("Updates AversEndpoints constants to match observed URLs").
- **Files modified:** `core/api-avers-v4/src/commonMain/kotlin/.../AversApi.kt`
- **Verification:** All 12 EndpointsContractTest cases pass.
- **Committed in:** `cb5efd4` (Task 2 commit).

**2. [Rule 1 - Bug] DTOs use positional KSerializer instead of object/SerialName decoders**
- **Found during:** Task 1 source authoring
- **Issue:** Plan supplied DTOs as `@Serializable data class GradeDto(val id: String?, ...)` with `@SerialName` annotations expecting object envelopes. AVERS responses are JSON arrays of JSON arrays (Plan 02-02 SUMMARY: `[[mark_id, pupil_id, value, date, ...], ...]`). With `ignoreUnknownKeys = true` an object decoder produces `GradeDto(id=null, ...)` for every row — i.e. all-null DTOs, technically `Success` but useless. Worse, `decodeFromJsonElement(ListSerializer(GradeDto.serializer()), arrayElement)` would actually fail because the elements are `JsonArray`, not `JsonObject`.
- **Fix:** Each DTO now declares `@Serializable(with = XxxDtoSerializer::class)` and ships a custom `internal object XxxDtoSerializer : KSerializer<XxxDto>` that decodes from `JsonArray` via positional accessors (`PositionalRowReader.longAt(row, idx)` / `stringAt` / `intAt` / `booleanAt`). This satisfies the plan's `<must_haves>` line 51 ("EndpointsContractTest asserts ApiResult.Success") against the real fixture shapes.
- **Files modified:** All 6 DTO files plus new `dto/PositionalRowReader.kt`. `AversApi.kt` uses `ListSerializer(XxxDtoSerializer)` directly because `@Serializable(with = …)` blocks the auto-generated `XxxDto.serializer()` companion accessor.
- **Verification:** EndpointsContractTest passes; the deserialised DTO instances would be readable in a debugger if instrumented.
- **Committed in:** `09b8e50` (Task 1 commit).

**3. [Rule 2 - Missing critical functionality] ExtJsArrayPreprocessor for `new Date(…)` literals**
- **Found during:** Task 1 source authoring (anticipated from Plan 02-02 SUMMARY "Response shape — NOT strict JSON" section)
- **Issue:** AVERS body for `/act/GET_STUDENT_JOURNAL_DATA` and `/act/GET_STUDENT_DAIRY` includes `new Date(YYYY, M_minus_1, DD, h, m, s, ms)` JS literals (e.g. `new Date(2026,3,27,0,0,0,0)`). Strict `kotlinx.serialization` rejects this as malformed JSON, so without preprocessing every grades/homework call would yield `ApiResult.Mismatch`.
- **Fix:** Added `ExtJsArrayPreprocessor.toStrictJson(body)` — single regex pass `new\s+Date\s*\(\s*(\d+),\s*(\d+),\s*(\d+)(?:,[^)]*)?\)` → `"YYYY-MM-DD"` (ISO). Adds 1 to month before formatting because JS Date is 0-indexed. `AversApi.executeListEndpoint` calls it before `parseToJsonElement`. Tested by `ExtJsArrayPreprocessorTest` (5 cases including December and multi-line array preservation).
- **Files created:** `core/api-avers-v4/src/commonMain/kotlin/.../extjs/ExtJsArrayPreprocessor.kt` + test.
- **Verification:** EndpointsContractTest grades/homework cases pass.
- **Committed in:** `09b8e50` (Task 1 commit) and `2f6f057` (test).

**4. [Rule 1 - Bug] KDoc nested-comment trap with `/act/*` and `/account-{A,B}/*.har` substrings**
- **Found during:** Task 1 first build, recurred in Task 3 first build
- **Issue:** Compiler error `Syntax error: Unclosed comment` reported at the END of files. Root cause: KDoc text containing `/act/*` (Plan 02-02 SUMMARY phrasing) opens a nested block comment because Kotlin's KDoc parser treats `/*` as a nested-block opener. The next `*/` then closes the inner block, leaving the outer KDoc open until EOF.
- **Fix:** All KDoc references rewritten as `/act/...` (no asterisk after slash). Affected: `PositionalRowReader.kt`, `LoginDto.kt`, `AversEnvelope.kt`, `EndpointsContractTest.kt` (where `account-{A,B}/*.har` was rephrased to `account-{A,B}/<endpoint>.har`).
- **Files modified:** four files via Edit calls.
- **Verification:** `assemble` and `compileTestKotlin*` both pass.
- **Committed in:** rolled into `09b8e50` and `2f6f057`.

**5. [Rule 3 - Blocking] `kotlinx-coroutines-test` not in commonTest deps**
- **Found during:** Task 3 first commonTest compile
- **Issue:** `runTest { }` import unresolved. Convention plugin `lintech-test` ships `kotlin.test` + `kotest-assertions` + `turbine`, NOT `kotlinx-coroutines-test`. Plan 02-04 hit the identical issue and added the dep at `:core:network` build.gradle.kts level — Plan 02-06 follows the same pattern.
- **Fix:** Added `implementation(libs.kotlinx.coroutines.test)` to `commonTest.dependencies` block in `core/api-avers-v4/build.gradle.kts`. NOT promoted to convention plugin (CLAUDE.md §Module structure principle: keep convention plugin minimal; only add to consumer that needs it).
- **Files modified:** `core/api-avers-v4/build.gradle.kts`
- **Verification:** Test compile + run both pass.
- **Committed in:** `2f6f057` (Task 3 commit).

**6. [Rule 3 - Blocking] `@Serializable(with = …)` blocks auto-generated `serializer()` companion**
- **Found during:** Task 2 first build
- **Issue:** `Unresolved reference 'serializer'` × 6 in `AversApi.kt` (`ListSerializer(GradeDto.serializer())` etc.) and 1 in `AversAuthApi.kt`. The kotlinx-serialization compiler plugin only synthesises `Foo.serializer()` companion accessor when there is no explicit `with =` on `@Serializable`; with positional decoders, every DTO uses `@Serializable(with = FooDtoSerializer::class)` so the companion is not generated.
- **Fix:** All call sites changed to reference the internal serializer object directly: `ListSerializer(GradeDtoSerializer)` / `decodeFromJsonElement(LoginDtoSerializer, row)`. Internal scope is module-wide so cross-package access from `AversApi.kt` to `dto/GradeDto.kt` is fine.
- **Files modified:** `AversApi.kt`, `AversAuthApi.kt`
- **Verification:** Build green.
- **Committed in:** `cb5efd4` (Task 2 commit) — fix happened before commit.

**7. [Rule 1 - Bug] EndpointsContractTest relocated from commonTest to androidUnitTest**
- **Found during:** Task 3 — after first commonTest run succeeded on Android JVM I noticed the iosTest path would invoke `loadHarFile.ios.kt` (which errors by design) at iOS Native test runtime.
- **Issue:** Keeping EndpointsContractTest in commonTest forces every iOS-side test runner (Plan 02-08 macos-15 CI) to either pass through that error or be silenced via Spec/Wrapper plumbing. Cleaner: scope the contract test to androidUnitTest only. Plan 02-09 owns iOS Native HAR fixture bundling and will promote the test to commonTest once that lands.
- **Fix:** Moved file via `mv` from `commonTest/.../EndpointsContractTest.kt` to `androidUnitTest/.../EndpointsContractTest.kt`. Updated KDoc to document the rationale.
- **Files modified:** moved file; KDoc adjusted in place.
- **Verification:** `./gradlew :core:api-avers-v4:test :core:api-avers-v4:compileTestKotlinIosX64` both green; iOS Native does NOT see the test.
- **Committed in:** `2f6f057` (Task 3 commit) — single commit captures the final test layout.

---

**Total deviations:** 7 auto-fixed (3 × Rule 1, 1 × Rule 2, 3 × Rule 3). All necessary for the plan's stated success criteria. No scope creep — the deliverable set matches `<must_haves>` and the verify automation block. The principal change visible to downstream plans is the `AversEndpoints` URL constants matching reality and the positional-KSerializer approach for DTOs; both are forward-compatible refinements (Phase 4 can extend without breaking Phase 2 callers).

## Authentication Gates

None — no external service authentication required for Plan 02-06. AVERS auth flow itself is encoded in the contract surface (login/logout) but no live calls are made.

## Issues Encountered

- **Kotlin KDoc nested-comment trap** — first time encountered in this project; documented in deviations as a recurring class of issue worth flagging in CLAUDE.md's §Conventions if it recurs in Phase 4+ KDoc-heavy modules. Workaround: use `/act/...` instead of `/act/*` everywhere KDoc text references AVERS routes.
- **Build artefacts** under `build/test-results/` and `build/reports/` get cleaned on next `assemble`; counts and timestamps captured here are point-in-time evidence.

## Threat Surface Scan

No new threat surface beyond `<threat_model>` (T-02-32 through T-02-37) — all addressed in the implementation. No new external endpoints, no auth path changes (the existing AVERS login flow is reflected, not modified), no schema changes at trust boundaries.

## Known Stubs

None — all DTOs decode against real fixtures. AttendanceDto and MessageDto have placeholder column orderings for future enrichment because Plan 02-02 fixtures return `[]`, but the positional-decoder skeleton tolerates this (the DTO list is empty after decode → `ApiResult.Success(emptyList())`). Phase 4 mappers extend the column ordering without breaking the API surface.

## User Setup Required

None — no external service configuration required. (Phase 6 TestFlight setup deferred per ROADMAP §Phase 2 success criteria.)

## Next Phase Readiness

- **Plan 02-07 (kill-switch wiring):** independent — can proceed in parallel.
- **Plan 02-08 (CI iosX64Test runtime):** ready — `:core:api-avers-v4` commonTest cases (ApiResult, AversApiError, AversEnvelope, AntiBotDetector, ExtJsArrayPreprocessor) will run on macos-15. EndpointsContractTest stays Android-only.
- **Plan 02-09 (final integration smoke):** owns iOS Native HAR fixture bundling. When that lands, EndpointsContractTest promotes to commonTest (replace `androidUnitTest` location with commonTest, wire iOS resource loading in `loadHarFile.ios.kt` actual).
- **Phase 4 :core:data:** ready to consume `AversApi.fetchGrades/Schedule/Homework/Attendance/Messages` + `AversAuthApi.login/logout`. Phase 4 will add: bootstrap chain orchestrator (`get_user_data` → `get_uch_year` → `GET_STUDENT_CLASS` → `GET_STUDENT_PARALLEL`), JS-escape() polyfill helper for ys-* cookie writing, SHA-1 password hashing, /auth two-step completion, mapper DTO→domain.
- **Phase 3 Auth UI:** `AversApiError.AntiBotChallenge.rawHtml` is the WebView fallback payload (D-06).
- **No blockers for the remaining Wave 5 plan (02-09).**

## Self-Check: PASSED

Verified before signing off:

```bash
# Created files (16 main + 9 tests + 1 .gitkeep = 26 files)
[ -f core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/result/ApiResult.kt ] && echo FOUND
[ -f core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/result/AversApiError.kt ] && echo FOUND
[ -f core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/dto/envelope/AversEnvelope.kt ] && echo FOUND
[ -f core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/antibot/AntiBotDetector.kt ] && echo FOUND
[ -f core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/extjs/ExtJsArrayPreprocessor.kt ] && echo FOUND
[ -f core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/AversApi.kt ] && echo FOUND
[ -f core/api-avers-v4/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/AversAuthApi.kt ] && echo FOUND
[ -f core/api-avers-v4/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/EndpointsContractTest.kt ] && echo FOUND

# Commits
git log --oneline | grep -q 09b8e50 && echo COMMIT-1-FOUND
git log --oneline | grep -q cb5efd4 && echo COMMIT-2-FOUND
git log --oneline | grep -q 2f6f057 && echo COMMIT-3-FOUND
```

All expected outputs returned. Build and test verifications green:

- `./gradlew :core:api-avers-v4:assemble` — BUILD SUCCESSFUL
- `./gradlew :core:api-avers-v4:test` — BUILD SUCCESSFUL (36 cases, both Debug + Release variants)
- `./gradlew :core:api-avers-v4:compileKotlinIosX64` — BUILD SUCCESSFUL
- `./gradlew :core:api-avers-v4:compileTestKotlinIosX64` — BUILD SUCCESSFUL
- `bash tests/sanitize-har-canary.sh` — PASS (no Cyrillic surnames in fixtures)
- `bash tests/log-redactor-canary.sh` — PASS (`kanareyka_PASSWORD_DO_NOT_LEAK_42` absent from debug-build logs)

---

*Phase: 02-api-reverse-engineering-network-layer*
*Plan: 06*
*Completed: 2026-04-29*
