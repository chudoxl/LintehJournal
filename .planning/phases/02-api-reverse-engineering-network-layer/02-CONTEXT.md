# Phase 2: API Reverse-Engineering & Network Layer - Context

**Gathered:** 2026-04-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Задокументированный contract закрытого ExtJS-API АВЕРС (`journal.school28-kirov.ru`) для билда v4.1 build 23813 + работающий Ktor-стек, который умеет логиниться и тянуть JSON для всех 6 read-эндпоинтов (login, оценки, расписание, ДЗ, посещаемость, сообщения). Phase 2 поднимает базовую инфраструктуру `:core:database` (Room schema v1, только cookies-таблица), `:core:network` (HttpClientFactory + plugin chain + RoomCookiesStorage + AVERS-specific UA mimic / retry / sanitizers), новый модуль `:core:api-avers-v4` (versioned API contract: типизированные DTO + suspend functions + sealed `AversApiError` + sealed `ApiResult`), а также wipe-API `AccountDataPurger.purge(accountId)` для будущего Phase 3 logout. Все integration-тесты гоняются ТОЛЬКО против sanitized HAR-fixtures + Ktor MockEngine — никаких живых вызовов АВЕРС в CI. Закрывает success criteria #1-5 из ROADMAP §Phase 2 и pitfalls #2 (API fragility), #5 (logging hygiene), #6 (per-account architecture invariant), #11 (anti-bot), #19 (SSL — system trust, no pinning).

**В scope не входит:** auth UI и interactive login (Phase 3); KVault + secure credential storage (Phase 3); domain-models grades/schedule/homework и их UI (Phase 4); полное multi-account scope с переключением аккаунтов (Phase 5); WebView CAPTCHA fallback (Phase 3, ловится как `AversApiError.AntiBotChallenge` в Phase 2); WorkManager/BGAppRefreshTask для polling (Phase 6); pagination clientside (Phase 4 если потребуется); active anti-bot probing (Phase 5/v2 hardening); живой smoke в CI (исключено навсегда).

</domain>

<decisions>
## Implementation Decisions

### Capture Mechanics (Area 1: Реверс)

- **D-01:** Test-аккаунты — **2+ личных аккаунта** (минимум двух разных детей в школе). Даёт ранний валидатор multi-account-паттерна: HAR-snapshots от двух разных сессий проверяют, что cookie-isolation работает на уровне reverse-engineered контракта, не только на уровне Phase 5 архитектуры. Cross-account test fixtures = D-22 (см. Endpoint Scope).
- **D-02:** Primary capture tool — **Chrome DevTools HAR-export** на desktop. Открыть `journal.school28-kirov.ru` в Chrome → F12 → Network → Save all as HAR. ⚠ **ROADMAP success #1 требует «mitmproxy» — нужно скорректировать в Phase 2 plan** (см. Deferred Ideas → ROADMAP edits). Известный risk: mobile UA divergence (АВЕРС может отдавать другой ExtJS-вариант для desktop UA). Митигация — UA mimic параметризован, легко переключим mobile-UA через config; первый реальный Android run в Phase 4 проверит совпадение.
- **D-03:** Sanitization — **скрипт `tools/sanitize-har.py`** (Python). Берёт raw HAR, проходит по response bodies, заменяет ФИО на детерминированные fakes (Иванов И.И., Петров П.П., Сидоров С.С.), оценки оставляет (не PII), URLs фотографий заменяет на placeholders, в cookies оставляет структуру но рандомизирует значения. CI lint-job грепает PII-паттерны (test-canary-фамилии, чтобы убедиться что не попало) в `fixtures/sanitized/` — regression-protect.
- **D-04:** Storage layout — `fixtures/raw/` в `.gitignore` (живые HAR с реальными ФИО), `fixtures/sanitized/` в git (committable обезличенные fixtures), `.env.local` в `.gitignore` (login/password test-аккаунтов, читаемые dotenv-ом локальными скриптами при manual capture). README документирует setup.
- **D-05:** Anti-bot research — **только пассивное документирование**. Что увидели в captures (cookies, headers, CSRF-токены, anti-CAPTCHA) — записываем в `aversApiV4_23813.md`. Никаких целенаправленных «ввод неправильного пароля 5 раз» — уважительно к серверу школы. Throttling/retry-after берём по общепринятым значениям (1 login per 5s, exponential backoff на ошибки, retry-after header honored).
- **D-06:** WebView CAPTCHA fallback — **отложен в Phase 3 (auth UI)**. Phase 2 ловит challenge как `AversApiError.AntiBotChallenge` (typed sealed-error из D-13) с raw HTML body в payload. Phase 3 (Auth UI) ловит этот error и открывает WebView. Соблюдает D-07 dependency rules из 01-CONTEXT.md (UI не появляется в `:core:network`).

### API Module Shape (Area 2: Форма API-модуля)

- **D-07:** Новый отдельный Gradle-модуль **`:core:api-avers-v4`** для versioned API contract. `:core:network` остаётся общей Ktor-инфрой (HttpClientFactory, plugin chain, sanitizers); `:core:api-avers-v4` зависит от `:core:network` и инкапсулирует endpoint-функции + DTO + AVERS-specific UA mimic / retry / anti-bot detection. При обновлении АВЕРС → новый модуль `:core:api-avers-v4-23901` в parallel, старый остаётся fallback. Чистый boundary.
- **D-08:** API surface — **типизированные DTO + suspend functions**. `GradeDto`, `LessonDto`, `HomeworkDto`, `AttendanceDto`, `MessageDto` + suspend `fun fetchGrades(period: Period): ApiResult<List<GradeDto>>` etc. Schema-driven парсер работает внутри модуля. Снаружи — жёсткие Kotlin-типы. Mapper DTO→domain пишется в `:core:data` в Phase 4 (не Phase 2). DTOs могут расширяться в Phase 5 без breaking-change для Phase 4 mapper.
- **D-09:** Parsing strategy — **strict + sealed `ApiResult`**. kotlinx.serialization c `ignoreUnknownKeys = true` (толерантно к новым полям АВЕРС, не падаем на минорных добавлениях) + явные `requireNotNull(json["success"])` перед маппингом критичных полей. Возвращает `sealed class ApiResult<out T> { data class Success<T>(value: T); data class Mismatch(rawDump: String, missingFields: List<String>, endpoint: String); ... }`. Domain (`:core:data` в Phase 4) решает «показать banner про обновление приложения» по `Mismatch`.
- **D-10:** Plugin chain — **в `:core:network/HttpClientFactory`** (общее). `:core:network` даёт фабрику с pre-configured plugins: `Logging` (с sanitizeHeader для Authorization/Cookie/Set-Cookie), `ContentNegotiation(Json)`, `HttpCookies(RoomCookiesStorage)`, `DefaultRequest(baseUrl, Accept-Language)`, `HttpTimeout`, `HttpRequestRetry`. `:core:api-avers-v4` поверх добавляет UA mimic Mobile Safari (или desktop UA — параметризован) + AVERS-specific anti-bot retry policy (retry-after honored, exponential backoff). Разделение «общий HTTP» / «AVERS-особенности» чёткое.
- **D-11:** Error hierarchy — **плоская sealed `AversApiError`**:
  ```kotlin
  sealed class AversApiError {
    object Unauthorized : AversApiError()
    object AntiBotChallenge : AversApiError()
    object Network : AversApiError()
    data class ContractMismatch(val rawDump: String, val missingFields: List<String>, val endpoint: String) : AversApiError()
    data class Server(val httpCode: Int) : AversApiError()
    object KillSwitchTriggered : AversApiError()
  }
  ```
  Используется в `ApiResult.Failure(AversApiError)`. Sealed → exhaustive when, корректно мапится в Swift enums через KMP.
- **D-12:** Канарейка + remote kill-switch — **GitHub Pages `https://chudoxl.github.io/LintehJournal/api-config.json`** (тот же `docs/` источник, что и Privacy Policy). Бесплатно, GitHub-native, auto-deploy через docs/. JSON schema: `{ latestSupportedAversBuild: string, message: string, severity: "info" | "warning" | "block", minAppVersion: string }`. CDN-cache до 5min Fastly TTL — для kill-switch достаточно.
- **D-13:** Kill-switch недоступен — **fail-open**. Если GitHub Pages не отвечает / 404 / timeout — приложение работает нормально (как «всё ок»). Никогда не блокируем offline-пользователя из-за GitHub Pages downtime. Соблюдает offline-first инвариант из PROJECT.md.
- **D-14:** Kill-switch cadence — **при каждом app-launch** (cold-start). Один запрос к GitHub Pages, результат cached на весь session (in-memory). Phase 6 BGAppRefreshTask может расширить до «1 раз / 24ч в background» — но это Phase 6 detail.

### Cookies & Database (Area 3: Куки и Room)

- **D-15:** **Bootstrap `:core:database`** в Phase 2 (раньше чем планировалось в 01-CONTEXT.md `:core:database появится в Phase 3`). Минимальная Room-схема v1: одна таблица `cookies` (`id`, `name`, `value`, `domain`, `path`, `expiresAt`, `httpOnly`, `secure`). KSP-сетап, schema-version 1. Phase 3 (Auth) добавит `accounts` таблицу через Room migration v1→v2. Phase 4+ добавят entity-плацдармы (grades, lessons, homework, attendance, messages) через дальнейшие migrations. **ROADMAP success #3 NOT requires edit — формулировка `persistent cookies в Room-таблице` уже выполнена.**
- **D-16:** DB filename — **`journal_default.db`** в Phase 2. Соблюдает invariant `journal_${accountId}.db` из ARCHITECTURE.md. В Phase 2 `accountId = "default"` (placeholder). Phase 3 при login «перевяжет» default на реальный accountId (rename file через AccountDataPurger или migration helper). Phase 5 включает multi-account scope — файлы уже по паттерну, никаких special-cases.
- **D-17:** Schema v1 — **только `cookies`**. Никаких other tables в Phase 2. Researcher напишет KSP-конфиг + Room migration template для будущих фаз.
- **D-18:** Ktor `CookiesStorage` integration — **custom `RoomCookiesStorage : CookiesStorage`** в `:core:network`. Реализует Ktor `CookiesStorage` interface через Room DAO. При каждом `addCookie`/`get` — suspend запись/чтение в БД. Стандартное решение для Ktor.
- **D-19:** Room initialization — **expect/actual `DatabaseFactory`**. expect/actual factory pattern из 01-CONTEXT D-31. Точное место expect (`:core:platform` vs `:core:database/iosMain`) уточняет researcher по KMP Room standard pattern (вероятно — `:core:database/{androidMain,iosMain}` через `Room.databaseBuilder<JournalDatabase>(...)`). В Phase 2 wiring ручной (без Koin); Koin DI вводится позже когда дерево зависимостей действительно требует scope-management (вероятно Phase 3-5).
- **D-20:** iOS file protection — **`completeFileProtection`** (`NSFileProtectionComplete`) в Phase 2. .db-файл недоступен до первого unlock + после lock-screen. ⚠ **Phase 6 background polling потребует переключения на `completeUntilFirstUserAuthentication`** — иначе BGAppRefreshTask не сможет читать БД когда устройство locked. Явно зафиксировать в Phase 6 plan как обязательную задачу. Помечено в Deferred Ideas → Phase 6 prerequisites.
- **D-21:** Android `allowBackup="false"` в AndroidManifest. ADB backup и Auto Backup to Cloud выключены. Cookies/credentials никуда не утекут через backup. Соответствует PROJECT.md on-device-only семантике.
- **D-22:** Wipe-API — **`interface AccountDataPurger { suspend fun purge(accountId: String) }`** реализуется в Phase 2. Удаляет cookies из Room для аккаунта, закрывает HttpClient (если открыт), удаляет файл `journal_${accountId}.db`. Phase 3 оркестрирует logout: `KVault.removeCreds(accountId) + AccountDataPurger.purge(accountId)`. Готовит per-account-scope invariant раньше чем Phase 3.

### Endpoint Scope (Area 4: Скоп endpoints)

- **D-23:** Scope — **все 6 endpoints в полный contract в Phase 2**: login (POST), grades (GET), schedule (GET), homework (GET), attendance (GET), messages (GET). Соответствует ROADMAP success criterion #1 без edits. Risk «DTO won't fit UI later» митигирован типизированной DTO surface (mapper DTO→domain в Phase 4 — DTOs можно расширять без breaking changes).
- **D-24:** Глубина variations — **базовый contract каждого endpoint + cross-account proof**. Базовые сценарии: grades = текущий период, schedule = текущая неделя, homework = все предметы, attendance = текущий период, messages = последние N. Захвачены **от двух разных аккаунтов** (см. D-01) — каждый endpoint имеет 2 HAR-fixtures, доказывает «разные cookies → разные данные». Итоговые оценки / история по периодам / замены / прикреплённые файлы ДЗ / веса оценок — отложены в Phase 5 расширения. ~12 HAR-fixtures total.
- **D-25:** Integration tests в CI — **только Ktor MockEngine + sanitized HAR**. CI никогда не стучится в `journal.school28-kirov.ru`. MockEngine подменяет ответы из `fixtures/sanitized/`. Детерминировано, быстро, не вызывает anti-bot. Реальные запросы — только локальный dev (manual smoke-test руками). Никакого «live smoke» workflow в CI.
- **D-26:** Cookie-expiry / 401 handling — **auto re-login в `:core:network` + проброс `AversApiError.Unauthorized` при fail re-login**. Ktor Auth plugin / interceptor: при 401 → вызвать login(credentials из `CredentialProvider`) → перевыполнить исходный запрос. Если re-login тоже 401 (пароль неправильный) — пробрасывает `AversApiError.Unauthorized` → Phase 3 Auth UI ловит и показывает login-экран. **Phase 2 НЕ имеет KVault** (Phase 3) — поэтому в Phase 2 вводится `interface CredentialProvider { suspend fun get(accountId: String): Pair<String, String>? }` с noop/test-only реализацией. Phase 3 поставит KVault-backed implementation.
- **D-27:** Pagination — **документируем, не реализуем** в Phase 2. Reverse покажет offset/limit или page/size params АВЕРС → запишем в `aversApiV4_23813.md`. Сами функции отдают full result (`fetchGrades(period)` возвращает все за период). Для school-журнала record-counts малые (~hundreds), пагинация может быть избыточной. Phase 4 UI решает добавить ли paging.
- **D-28:** Debug logging — **Kermit `LogLevel.Verbose` + Ktor `LogLevel.ALL`** в debug-build (выбор пользователя для maximum visibility). Release — `LogLevel.NONE`. ⚠ **Mandatory addendum (security-critical):** sanitizeHeader + custom `HttpRequestRedactor` для bodies (mask `password`, `pwd`, `pass`, `cookie`, `authorization`, `set-cookie`, `token`) **обязаны работать и в debug, не только release**. Canary-test (`kanareyka_PASSWORD_DO_NOT_LEAK_42` грепает Logcat/Console.app output) гоняется в CI против **debug-build тоже**. Иначе пароль попадёт в локальные логи и может выкинуться в скриншот / paste / shared screen.

### Claude's Discretion

Researcher и planner свободны в выборе:
- Точная структура `aversApiV4_23813.md` документа (markdown sections / tables / headers)
- Точная схема `tools/sanitize-har.py` (CLI args, конфиг-файл с регулярками для PII patterns) и какой Python — system или venv
- Конкретный layout `:core:api-avers-v4` (per-endpoint files vs grouped)
- Конкретный wiring DatabaseFactory (где expect/actual — `:core:platform` vs `:core:database/{android,ios}Main`) с учётом KMP Room standard
- HttpClient lifecycle (singleton vs per-request via factory) — обычно factory.forAccount(id) возвращает cached client, researcher решит cache strategy
- Точный JSON-schema kill-switch (имена полей, optional fields)
- Запланировать ли Koin интеграцию в Phase 2 (по STACK.md `Koin 4.x` — locked стек, но timing не зафиксирован) — рекомендация: отложить если ручного wiring достаточно для Phase 2 scope
- Конкретные timeout values, retry policy parameters, exponential backoff формула — разумные defaults на baseline
- Versions: `kotlinxSerialization` 1.7.3 → 1.9.0, `kotlinxDatetime` 0.6.2 → 0.8.x, `kotlinxCoroutines` 1.10.2 (актуальна) — researcher проверит совместимость с Ktor 3.4.3
- Room version (2.8.x KMP from STACK.md) + соответствующая KSP version

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project decisions (LOCKED — do not relitigate)

- `.planning/PROJECT.md` — core value, on-device-only архитектура, security инварианты, personal-use scope, biometry deferred
- `.planning/REQUIREMENTS.md` — нет direct AUTH/GRAD/SCHED requirements в Phase 2 scope (Phase 2 = infrastructure для всех последующих фаз), но Phase 2 готовит `AccountDataPurger` для AUTH-03 (logout с очисткой данных)
- `.planning/ROADMAP.md` §Phase 2 — goal, success criteria #1-5, depends-on Phase 1, closes-pitfalls #2/#11/#19/#5
- `.planning/STATE.md` — current position, blockers (Phase 2 highest uncertainty, Phase 1 UAT-2/UAT-5 deferred в v2)
- `.planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md` — Phase 1 decisions (D-01..D-31), особенно: D-07 dependency rules, D-08 package root `io.github.chudoxl.linteh.journal.*`, D-31 expect/actual pattern для UrlOpener (template для DatabaseFactory)
- `.planning/phases/01-foundation-compliance-infrastructure/01-VERIFICATION.md` — Phase 1 verified state baseline
- `CLAUDE.md` §Conventions / §Architecture / §Build infrastructure — locked Phase 1 patterns

### Research outputs (LOCKED для Phase 2)

- `.planning/research/STACK.md` — Recommended Stack table:
  - Ktor 3.4.3 (Darwin/OkHttp engines) — locked
  - kotlinx.serialization 1.9.0 (bump from current 1.7.3 в Phase 2) — locked
  - kotlinx-datetime 0.8.x (bump from 0.6.2) — locked
  - Room 2.8.x KMP — locked
  - Kermit 2.0.4+ — locked (logger)
  - Ktor MockEngine для тестирования — bundled
  - kotest 5.9+ assertions + Turbine 1.2+ + Mokkery (locked в Phase 1)
  - **What NOT to Use:** Ktor CIO engine, Gson/Moshi, java.time, Realm/Realm Kotlin, OkHttp напрямую, MockK на iOS — все locked
  - Version Compatibility table — Kotlin 2.2.20 + KSP 2.2.20-x.x.x + kotlinxSerialization 1.9 + Ktor 3.4.x
- `.planning/research/ARCHITECTURE.md` — feature-by-layer multi-module pattern, per-account scope как архитектурный инвариант, AccountStore + SecureCredentialStore + SyncCoordinator (Phase 3+)
- `.planning/research/PITFALLS.md`:
  - **Pitfall #2** (API fragility) — versioned `aversApiV4_23813` модуль + canary + remote kill-switch + HAR-snapshot тесты — closes в Phase 2
  - **Pitfall #5** (logging hygiene) — sanitizeHeader + redactor + canary-test — closes в Phase 2
  - **Pitfall #6** (per-account leak) — per-account architecture invariant с самого начала — Phase 2 готовит wipe-API + DB filename pattern
  - **Pitfall #11** (anti-bot) — UA mimic, throttling, retry-after — Phase 2 inactive observation; WebView fallback в Phase 3
  - **Pitfall #19** (SSL) — system trust, no pinning — locked
- `.planning/research/SUMMARY.md` — executive summary, risk ranking
- `.planning/research/FEATURES.md` — конкурентный анализ (фон, не блокер)

### External documentation (для researcher Phase 2)

- [Ktor 3.4.x Cookies docs](https://ktor.io/docs/client-cookies.html) — HttpCookies plugin, AcceptAllCookiesStorage, custom CookiesStorage implementation
- [Ktor Client Engines](https://ktor.io/docs/client-engines.html) — Darwin (iOS NSURLSession) / OkHttp (Android) configuration
- [Ktor MockEngine](https://ktor.io/docs/client-testing.html) — паттерн testEngine + responseHandler для HAR-fixture replay
- [Ktor Auth plugin](https://ktor.io/docs/client-auth.html) — для D-26 auto re-login (или custom interceptor)
- [Set up Room for KMP — Android Developers](https://developer.android.com/kotlin/multiplatform/room) — KMP setup, KSP config (kspIosArm64 + kspIosSimulatorArm64 + kspIosX64 + kspAndroid)
- [Room 2.8.x release notes](https://developer.android.com/jetpack/androidx/releases/room) — KMP-stable status, miration syntax
- [kotlinx.serialization 1.9.0 release notes](https://github.com/Kotlin/kotlinx.serialization/releases) — Instant/datetime serialization compatibility
- [iOS NSFileProtectionComplete docs](https://developer.apple.com/documentation/foundation/nsfileprotectionkey) — `completeFileProtection` setting on iOS file system
- [Apple HAR Inspector spec](http://www.softwareishard.com/blog/har-12-spec/) — HAR 1.2 schema (для sanitize-har.py reference)

### LinTech-internal references

- `gradle/libs.versions.toml` — текущий version catalog; Phase 2 добавит ktor, room, ksp-room, kotlinxSerializationJson 1.9.0 bump
- `core/network/` — empty Phase 1 module skeleton, Phase 2 заполняет
- `core/platform/src/commonMain/kotlin/.../UrlOpener.kt` — pattern для expect declaration (template для CredentialProvider, NetworkMonitor если потребуется)
- `core/platform/src/{androidMain,iosMain}/.../UrlOpener.{android,ios}.kt` — pattern для actual implementations
- `composeApp/PrivacyInfo.xcprivacy` — может потребовать дополнительных reason codes если Room/Ktor добавят required-reason API usage; CI lint chain в `.github/workflows/ci.yml` macos-job catches regression

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`:core:platform`** уже существует с UrlOpener (Phase 1 D-31). Phase 2 расширяет тем же expect/actual паттерном для CredentialProvider (D-26) и потенциально NetworkMonitor (deferred). expect declaration в commonMain → actual в androidMain/iosMain.
- **`:core:network`** — пустой skeleton модуль из Phase 1 (`core/network/build.gradle.kts` применяет `lintech-kmp` + опционально `lintech-test`). Phase 2 наполняет: `HttpClientFactory` + plugins + `RoomCookiesStorage` + sanitizers. Никаких prior commits — clean slate.
- **`gradle/libs.versions.toml`** — версии для ktor / room / ksp / kotlinxSerialization уже зарегистрированы как «Phase 2+ reserved» (см. CLAUDE.md §Stack). Phase 2 bump'нет: kotlinxSerialization 1.7.3 → 1.9.0, kotlinxDatetime 0.6.2 → 0.8.x. Добавит: ktor-client-core/cio/darwin/okhttp/content-negotiation/serialization-json/logging/auth, room-runtime/compiler/ksp, ksp plugin (KSP 2.2.20-x.x.x — alignment к Kotlin 2.2.20).
- **Convention plugins (`build-logic/`)** — `lintech-kmp`/`lintech-compose`/`lintech-test`. Phase 2 новые модули `:core:database` и `:core:api-avers-v4` применят `lintech-kmp + lintech-test` (NO compose deps — non-UI модули, см. CLAUDE.md §Module structure BLOCKER 1 fix iter 1).

### Established Patterns

- **expect/actual pattern** (D-31 из Phase 1) — Phase 2 добавит DatabaseFactory (D-19) и CredentialProvider (D-26). Один expect declaration в commonMain, два actuals в androidMain + iosMain.
- **Module deps rules (D-07)** — слои вниз: `:composeApp` → `:feature:*` → `:core:ui` → `:core:network` → `:core:platform`. Phase 2 расширит `:core:database` (на уровне `:core:network` или ниже) и `:core:api-avers-v4` (на уровне `:core:network` сверху). Точная dependency direction: `:core:api-avers-v4` зависит от `:core:network` (для HttpClientFactory). `:core:network` зависит от `:core:database` (для RoomCookiesStorage). `:core:database` зависит от `:core:platform` (для file paths / context). Researcher уточнит.
- **Single source of truth versions** (libs.versions.toml) — никаких version-литералов в build.gradle.kts модулей.
- **Per-account architecture invariant** (PROJECT.md → ARCHITECTURE.md → 01-CONTEXT) — DB filename `journal_${accountId}.db`, RoomCookiesStorage scoped per-account. Phase 2 готовит pattern; Phase 5 включает реальный multi-account.
- **AppTest split** (Phase 1) — Compose UI tests на двух runtime (iOS Native + Android Robolectric). Phase 2 unit-tests для :core:network / :core:database / :core:api-avers-v4 — кросс-платформенные `commonTest` через Mokkery + kotlin.test + kotest assertions + Turbine. UI-тесты Phase 2 не нужны (нет UI).
- **CI infrastructure** — `.github/workflows/ci.yml` Android (ubuntu-latest) + iOS (macos-15) jobs. Phase 2 расширяет: добавляет `tests/sanitize-har-canary.sh` lint step (грепает PII в `fixtures/sanitized/`), добавляет `tests/log-redactor-canary.sh` step (грепает `kanareyka_PASSWORD_DO_NOT_LEAK_42` в test output debug-build).

### Integration Points

- **`:core:network` ↔ `:core:database`** — RoomCookiesStorage — мост между Ktor HttpCookies и Room DAO. Custom class в `:core:network` использует Room DAO интерфейс из `:core:database`. Без circular deps.
- **`:core:api-avers-v4` ↔ `:core:network`** — versioned API получает HttpClient через `HttpClientFactory.forAccount(accountId)`. AVERS-specific UA mimic / retry policy надстраивается на верхнем уровне.
- **`:core:network` ↔ Phase 3 KVault** — `CredentialProvider` interface (D-26) экспортируется в Phase 2 как noop/test-only. Phase 3 поставит KVault-backed implementation. Чистый boundary.
- **`:core:database` ↔ Phase 3 accounts** — Schema v1 (cookies-only) → Schema v2 (cookies + accounts) через Room migration в Phase 3.
- **`:core:network` ↔ Phase 3 logout** — `AccountDataPurger.purge(accountId)` (D-22) реализуется в Phase 2 как public API. Phase 3 logout вызывает.
- **Future `:core:database` migrations** — Phase 4 (grades), Phase 5 (schedule, homework, attendance, messages, multi-account scope) добавят entity-плацдармы. Researcher Phase 2 пишет migration helper template.

</code_context>

<specifics>
## Specific Ideas

- **`aversApiV4_23813`** — точное имя versioned-модуля (соответствует ROADMAP success #2 формулировке). Build number `23813` — текущий, известный из ROADMAP. Будущие билды → новые модули с обновлённым number.
- **`kanareyka_PASSWORD_DO_NOT_LEAK_42`** — точная canary-строка из ROADMAP success #5 / Pitfall #5. CI grep'ает её в debug-build test output (расширение Phase 1 canary CI lint).
- **Test-аккаунты двух разных детей** — реальный context разработчика (двое детей в школе №28 с отдельными АВЕРС-аккаунтами). Cross-account proof в HAR-fixtures доказывает cookie-isolation на уровне contract.
- **Linux Mint dev-host** — критическая константа (из 01-CONTEXT). Capture happens на desktop через Chrome DevTools — нет необходимости в Android device для capture. Mobile-валидация — через `:composeApp:installDebug` локально на Android-телефоне разработчика (после реализации Phase 2).
- **`kanareyka` транслитерация** — уважение к D-08 transliteration policy (не «kanareyka», не «canary», именно так как в ROADMAP).

</specifics>

<deferred>
## Deferred Ideas

### Корректировки ROADMAP.md, требуемые planner-у Phase 2

- **ROADMAP success criterion #1 нужно скорректировать**: «HAR-snapshots... захваченные через mitmproxy для journal.school28-kirov.ru» → «HAR-snapshots... захваченные через **Chrome DevTools** для journal.school28-kirov.ru» (см. D-02). Planner должен включить ROADMAP-edit как явную задачу в PLAN.md. Известный risk (mobile UA divergence) задокументировать.
- **Note про cookies — НЕ нужен ROADMAP edit**: success #3 формулировка «persistent cookies в Room-таблице» соответствует D-15 (`:core:database` bootstrap в Phase 2 со схемой v1 cookies-only). Без edits.

### Идеи к будущим фазам

- **Phase 3 prerequisites из Phase 2:** `CredentialProvider` interface готов → KVault-backed implementation; `AccountDataPurger.purge(accountId)` готов → используется в logout; `AversApiError.AntiBotChallenge` ловится → WebView CAPTCHA fallback; Schema v2 migration (accounts table); rename DB файла `journal_default.db` → `journal_${realAccountId}.db` при login.
- **Phase 4 prerequisites:** Mapper DTO→domain (от `:core:api-avers-v4` к `:core:domain`) пишется здесь; offline-first паттерн через Room (Schema v3+ добавит grades entities); staleness indicators (когда Room запись была обновлена); kill-switch banner UI (Phase 4 показывает баннер, Phase 2 уже даёт `AversApiError.KillSwitchTriggered`).
- **Phase 5 prerequisites:** Multi-account scope активируется (real account switching, per-account HttpClient через factory.forAccount). DB filename pattern уже работает. Cross-account HAR-fixtures из Phase 2 валидируют.
- **Phase 6 critical reminder:** **iOS file protection переключить с `completeFileProtection` на `completeUntilFirstUserAuthentication`** (D-20) — иначе BGAppRefreshTask не сможет читать БД когда устройство locked после reboot. Явная задача в Phase 6 plan.
- **Phase 6 kill-switch расширение:** Phase 2 cadence «при app-launch». Phase 6 BGAppRefreshTask / WorkManager расширяет до «1 раз / 24ч в background» — нужен только если приложение редко открывается.
- **Active anti-bot probing** — отложено в Phase 5 hardening / v2 (см. D-05). Empirical thresholds через manual testing (не CI).
- **WebView CAPTCHA fallback** — Phase 3 (D-06).
- **Pagination clientside** — Phase 4 если потребуется (D-27 — документируем но не реализуем).
- **Live smoke в CI** — отклонено навсегда (D-25). Manual smoke-test разработчик гоняет локально.
- **Koin DI introduction** — researcher решит timing (Phase 2 vs 3 vs 4-5). По STACK.md Koin 4.x locked стек, но точный момент введения — Claude's discretion (D-19).
- **NetworkMonitor expect/actual** — отложено до Phase 4 (offline-first). Если researcher обнаружит, что в Phase 2 нужно — добавит.
- **Performance budget bench** — Phase 2 не имеет SLA на latency. Phase 6 hardening / v2 production pre-flight measure realistic numbers.
- **CI build-time budget** — Room+KSP добавит время к iOS Native compile. Researcher Phase 2 включит kotlin/native cache hooks (klib cache via gradle/actions/setup-gradle), proguard/R8 не нужно (это library modules).

### Не folded todos

None — `.planning/todos/pending/` директории не существует, todo backlog пуст.

</deferred>

---

*Phase: 2-api-reverse-engineering-network-layer*
*Context gathered: 2026-04-28*
