# Phase 2: API Reverse-Engineering & Network Layer - Research

**Researched:** 2026-04-28
**Domain:** Closed ExtJS-API reverse-engineering + Ktor 3.4.3 multi-account network layer + Room 2.8.x KMP cookie persistence + sanitization/redaction discipline
**Confidence:** HIGH (Ktor / Room / kotlinx.serialization — Context7-equivalent official docs verified), MEDIUM (HAR sanitization tooling — multiple community options, no canonical), LOW (exact AVERS contract — unknown until live capture)

## Summary

Phase 2 is the highest-uncertainty phase in the project and consists of two intertwined work-streams:

1. **Reverse-engineering** the closed ExtJS-API of АВЕРС (build 23813) via Chrome DevTools HAR-export (D-02 corrects ROADMAP success #1 from "mitmproxy" to "Chrome DevTools"), sanitizing captured fixtures via a Python script (`tools/sanitize-har.py`), and authoring the human-readable contract (`docs/aversApiV4_23813.md`). Cross-account proof lands via captures from two real test accounts (D-01).

2. **Building three new Gradle modules** that codify the contract behind a stable Kotlin surface:
   - `:core:database` — Room 2.8.x KMP, schema v1 (cookies-only), expect/actual `DatabaseFactory`, iOS `NSFileProtectionComplete`, Android `allowBackup="false"`
   - `:core:network` (filling Phase 1 empty skeleton) — `HttpClientFactory.forAccount(id)`, plugin chain (Logging+sanitizer, ContentNegotiation, HttpCookies+RoomCookiesStorage, DefaultRequest, HttpTimeout, HttpRequestRetry), custom body-redactor, auto-relogin interceptor, `AccountDataPurger`, `CredentialProvider`
   - `:core:api-avers-v4` — versioned API contract, six endpoint suspend functions returning sealed `ApiResult<T>`, sealed `AversApiError`, kill-switch fetcher, anti-bot detector

All twenty-eight CONTEXT decisions are LOCKED. This research is **prescriptive about HOW** — exact module layouts, plugin pseudocode, libs.versions.toml diffs, threat models, validation maps — not exploratory.

**Primary recommendation:** Stage Phase 2 as 7-9 atomic plans aligned to bottom-up dependency order: (1) HAR capture + sanitization tooling + ROADMAP edit; (2) `:core:database` Room bootstrap; (3) `:core:network` HttpClientFactory + plugins + redactor + canary tests; (4) RoomCookiesStorage + AccountDataPurger + CredentialProvider; (5) `:core:api-avers-v4` module + DTOs + endpoint functions + sealed errors; (6) kill-switch fetcher + canary endpoint comparison; (7) end-to-end live smoke test (manual on dev-machine, NOT in CI); (8) docs (`aversApiV4_23813.md`); (9) CI extensions (sanitize-har-canary + log-redactor-canary). The planner may merge or split based on task atomicity rules.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| HAR capture (manual, dev-host) | Tooling/dev workflow | — | Chrome DevTools — out-of-band; outputs persist into repo as fixtures |
| HAR sanitization (PII removal) | Build/dev tooling (`tools/`) | CI lint | Python script idempotent; CI lint guards regression |
| Cookie persistence | Database (`:core:database`) | Network (`:core:network` storage adapter) | Source of truth = SQLite; network reads/writes via DAO |
| HTTP transport (engine, plugins) | Network (`:core:network`) | — | All HTTP concerns hidden from API/data layer |
| Body-/header-redaction | Network (`:core:network`) | — | Logging is a network-layer concern; redactor sits in plugin chain |
| API endpoint signatures (DTO + suspend fun) | API contract (`:core:api-avers-v4`) | — | Versioned to АВЕРС build; `:core:network` provides only transport |
| Anti-bot detection | API contract (`:core:api-avers-v4`) | — | AVERS-specific: HTML page vs JSON envelope is endpoint-knowledge |
| Kill-switch fetch + parse | API contract (`:core:api-avers-v4`) | — | Versioned alongside AVERS contract — same upgrade cycle |
| Auto-relogin (401 → CredentialProvider → retry) | Network (`:core:network` plugin) | API contract (defines `CredentialProvider`) | Universal HTTP behavior; CredentialProvider is contract-agnostic interface |
| Account data wipe | Database (`:core:database`) | Network (close HttpClient instances) | DB owns file deletion; network owns client lifecycle |
| Per-account isolation invariant | Architecture (cross-cutting) | All three modules | Phase 5 multi-account scope depends on this — the invariant exists from Phase 2 |

## User Constraints

> Copied verbatim from `02-CONTEXT.md`. Do not reword. Locked decisions take precedence over any
> recommendation in this research that contradicts them.

### Locked Decisions

#### Capture Mechanics (Area 1: Реверс)

- **D-01:** Test-аккаунты — **2+ личных аккаунта** (минимум двух разных детей в школе). Даёт ранний валидатор multi-account-паттерна: HAR-snapshots от двух разных сессий проверяют, что cookie-isolation работает на уровне reverse-engineered контракта, не только на уровне Phase 5 архитектуры. Cross-account test fixtures = D-22 (см. Endpoint Scope).
- **D-02:** Primary capture tool — **Chrome DevTools HAR-export** на desktop. Открыть `journal.school28-kirov.ru` в Chrome → F12 → Network → Save all as HAR. ⚠ **ROADMAP success #1 требует «mitmproxy» — нужно скорректировать в Phase 2 plan** (см. Deferred Ideas → ROADMAP edits). Известный risk: mobile UA divergence (АВЕРС может отдавать другой ExtJS-вариант для desktop UA). Митигация — UA mimic параметризован, легко переключим mobile-UA через config; первый реальный Android run в Phase 4 проверит совпадение.
- **D-03:** Sanitization — **скрипт `tools/sanitize-har.py`** (Python). Берёт raw HAR, проходит по response bodies, заменяет ФИО на детерминированные fakes (Иванов И.И., Петров П.П., Сидоров С.С.), оценки оставляет (не PII), URLs фотографий заменяет на placeholders, в cookies оставляет структуру но рандомизирует значения. CI lint-job грепает PII-паттерны (test-canary-фамилии, чтобы убедиться что не попало) в `fixtures/sanitized/` — regression-protect.
- **D-04:** Storage layout — `fixtures/raw/` в `.gitignore` (живые HAR с реальными ФИО), `fixtures/sanitized/` в git (committable обезличенные fixtures), `.env.local` в `.gitignore` (login/password test-аккаунтов, читаемые dotenv-ом локальными скриптами при manual capture). README документирует setup.
- **D-05:** Anti-bot research — **только пассивное документирование**. Что увидели в captures (cookies, headers, CSRF-токены, anti-CAPTCHA) — записываем в `aversApiV4_23813.md`. Никаких целенаправленных «ввод неправильного пароля 5 раз» — уважительно к серверу школы. Throttling/retry-after берём по общепринятым значениям (1 login per 5s, exponential backoff на ошибки, retry-after header honored).
- **D-06:** WebView CAPTCHA fallback — **отложен в Phase 3 (auth UI)**. Phase 2 ловит challenge как `AversApiError.AntiBotChallenge` (typed sealed-error из D-13) с raw HTML body в payload. Phase 3 (Auth UI) ловит этот error и открывает WebView. Соблюдает D-07 dependency rules из 01-CONTEXT.md (UI не появляется в `:core:network`).

#### API Module Shape (Area 2: Форма API-модуля)

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

#### Cookies & Database (Area 3: Куки и Room)

- **D-15:** **Bootstrap `:core:database`** в Phase 2 (раньше чем планировалось в 01-CONTEXT.md `:core:database появится в Phase 3`). Минимальная Room-схема v1: одна таблица `cookies` (`id`, `name`, `value`, `domain`, `path`, `expiresAt`, `httpOnly`, `secure`). KSP-сетап, schema-version 1. Phase 3 (Auth) добавит `accounts` таблицу через Room migration v1→v2. Phase 4+ добавят entity-плацдармы (grades, lessons, homework, attendance, messages) через дальнейшие migrations. **ROADMAP success #3 NOT requires edit — формулировка `persistent cookies в Room-таблице` уже выполнена.**
- **D-16:** DB filename — **`journal_default.db`** в Phase 2. Соблюдает invariant `journal_${accountId}.db` из ARCHITECTURE.md. В Phase 2 `accountId = "default"` (placeholder). Phase 3 при login «перевяжет» default на реальный accountId (rename file через AccountDataPurger или migration helper). Phase 5 включает multi-account scope — файлы уже по паттерну, никаких special-cases.
- **D-17:** Schema v1 — **только `cookies`**. Никаких other tables в Phase 2. Researcher напишет KSP-конфиг + Room migration template для будущих фаз.
- **D-18:** Ktor `CookiesStorage` integration — **custom `RoomCookiesStorage : CookiesStorage`** в `:core:network`. Реализует Ktor `CookiesStorage` interface через Room DAO. При каждом `addCookie`/`get` — suspend запись/чтение в БД. Стандартное решение для Ktor.
- **D-19:** Room initialization — **expect/actual `DatabaseFactory`**. expect/actual factory pattern из 01-CONTEXT D-31. Точное место expect (`:core:platform` vs `:core:database/iosMain`) уточняет researcher по KMP Room standard pattern (вероятно — `:core:database/{androidMain,iosMain}` через `Room.databaseBuilder<JournalDatabase>(...)`). В Phase 2 wiring ручной (без Koin); Koin DI вводится позже когда дерево зависимостей действительно требует scope-management (вероятно Phase 3-5).
- **D-20:** iOS file protection — **`completeFileProtection`** (`NSFileProtectionComplete`) в Phase 2. .db-файл недоступен до первого unlock + после lock-screen. ⚠ **Phase 6 background polling потребует переключения на `completeUntilFirstUserAuthentication`** — иначе BGAppRefreshTask не сможет читать БД когда устройство locked. Явно зафиксировать в Phase 6 plan как обязательную задачу. Помечено в Deferred Ideas → Phase 6 prerequisites.
- **D-21:** Android `allowBackup="false"` в AndroidManifest. ADB backup и Auto Backup to Cloud выключены. Cookies/credentials никуда не утекут через backup. Соответствует PROJECT.md on-device-only семантике.
- **D-22:** Wipe-API — **`interface AccountDataPurger { suspend fun purge(accountId: String) }`** реализуется в Phase 2. Удаляет cookies из Room для аккаунта, закрывает HttpClient (если открыт), удаляет файл `journal_${accountId}.db`. Phase 3 оркестрирует logout: `KVault.removeCreds(accountId) + AccountDataPurger.purge(accountId)`. Готовит per-account-scope invariant раньше чем Phase 3.

#### Endpoint Scope (Area 4: Скоп endpoints)

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

### Deferred Ideas (OUT OF SCOPE)

#### ROADMAP edits required by Phase 2 planner

- **ROADMAP success criterion #1 нужно скорректировать**: «HAR-snapshots... захваченные через mitmproxy для journal.school28-kirov.ru» → «HAR-snapshots... захваченные через **Chrome DevTools** для journal.school28-kirov.ru» (см. D-02). Planner должен включить ROADMAP-edit как явную задачу в PLAN.md. Известный risk (mobile UA divergence) задокументировать.
- **Note про cookies — НЕ нужен ROADMAP edit**: success #3 формулировка «persistent cookies в Room-таблице» соответствует D-15 (`:core:database` bootstrap в Phase 2 со схемой v1 cookies-only). Без edits.

#### Phase 3+ ideas

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

## Phase Requirements

Phase 2 has **no direct REQ-IDs** — it is pure infrastructure that enables Phase 3+ feature work.
The CONTEXT phase boundary explicitly notes: *"Phase 2 = infrastructure для всех последующих фаз;
самая высокая неопределённость в проекте — закрытый ExtJS-API без публичной документации."*

Indirect coverage of REQUIREMENTS.md items:

| REQUIREMENTS.md ID | Indirectly Enabled By Phase 2 | Research Support |
|---|---|---|
| AUTH-01 / AUTH-02 / AUTH-03 (Phase 3) | `CredentialProvider` interface + `AccountDataPurger` ready in Phase 2 | D-22, D-26 covered in Code Examples + Sealed AversApiError |
| ACCT-03 (Phase 5) — отдельный файл БД на аккаунт | DB filename pattern `journal_${accountId}.db` already in Phase 2 | D-16, D-22 + Per-account scope invariant section |
| GRAD-* / SCHED-* / HW-* / ATT-* / MSG-* (Phases 4-5) | Six AVERS endpoints typed + sealed error contract | D-08, D-23 + AVERS API Endpoint Signatures |
| OFFL-* (Phase 4) | Room-as-truth pattern set up; kill-switch enables "обновите приложение" banner | D-12, D-13, D-14 |
| COMP-* (already shipped Phase 1) | Privacy Policy URL reused for kill-switch JSON | D-12 |

## Project Constraints (from CLAUDE.md)

| Directive | Source | Impact on Phase 2 |
|-----------|--------|-------------------|
| Package root `io.github.chudoxl.linteh.journal.*` (transliteration `linteh` not `lintech`) | §Conventions/Package root | All new packages: `io.github.chudoxl.linteh.journal.core.{network,database,api.avers.v4}.*` |
| Single source of truth versions in `gradle/libs.versions.toml` | §Conventions/Versioning | All Ktor / Room / KSP versions added to catalog; no literal versions in build.gradle.kts |
| `expect/actual` only in `:core:platform` (or new platform-bordered modules); `commonMain` declares `expect`, `androidMain` + `iosMain` provide `actual` | §Conventions/expect-actual pattern | `DatabaseFactory` (Phase 2) uses this pattern; CredentialProvider may be pure interface (no expect) |
| UI tests: `runComposeUiTest`, selectors via `Modifier.testTag` | §Conventions/Tests | Phase 2 has no UI — N/A; unit tests in `commonTest` use Mokkery + kotlin.test + kotest assertions + Turbine |
| Convention plugins applied via `lintech-kmp` + `lintech-test` (NO compose deps in non-UI modules — BLOCKER 1 fix iter 1) | §Conventions/Module structure | `:core:database` and `:core:api-avers-v4` apply only `lintech-kmp + lintech-test`; no `lintech-compose` |
| Worktree mode: `git commit --no-verify`, stage files individually, atomic per-task commits | §Commit hygiene | Each Phase 2 task = one commit; planner must split work to atomic units |
| AndroidX `:core:network` etc. apply convention plugins; `:composeApp` applies raw plugins | §Build infrastructure | New Phase 2 modules apply `lintech-kmp` like `:core:network` already does |
| Compose Gradle Plugin 1.10.3 + Kotlin 2.2.20 + AGP 8.7.3 + JDK 17 toolchain | §Build infrastructure | Locked — must work with Ktor 3.4.3 + Room 2.8.x KMP + KSP 2.2.20-2.0.4 |
| iOS Privacy Manifest CI lint chain (`plutil -lint` + grep CA92.1, C617.1, NSPrivacyTracking, NSPrivacyCollectedDataTypes) | §Privacy & compliance | If Room/Ktor introduce required-reason API usage, may need to add reason codes (Phase 2 risk) |
| Per-account scope (отдельный файл БД на аккаунт) — invariant from PROJECT.md | §Architecture/Privacy | Phase 2 implements DB filename pattern `journal_${accountId}.db` (D-16) |
| Phase 1 BLOCKER 1 fix iter 1: non-UI модули НЕ должны тянуть compose deps через convention plugin | §Module structure | Phase 2 verifies `:core:database` / `:core:network` / `:core:api-avers-v4` build.gradle.kts have no `lintech-compose` |

## Standard Stack

### Core (verified)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| **Ktor Client** | **3.4.3** | HTTP client + plugin chain (Cookies, Logging, ContentNegotiation, HttpTimeout, HttpRequestRetry) | KMP-first; HttpCookies with custom `CookiesStorage`; Darwin engine on iOS = native NSURLSession; OkHttp engine on Android (HTTP/2). [VERIFIED: STACK.md locked + ktor.io docs] |
| **kotlinx.serialization-json** | **1.9.0** (bump from 1.7.3) | JSON parsing | Required by Ktor 3.4 ContentNegotiation; supports `ignoreUnknownKeys = true` (D-09); compatible with kotlinx-datetime 0.8.x Instant serialization. [VERIFIED: STACK.md] |
| **kotlinx-datetime** | **0.8.0-rc01** (bump from 0.6.2) | LocalDate/Instant for grade dates, schedule, homework deadlines | Required by kotlinx.serialization 1.9 for Instant. [CITED: STACK.md compatibility table] |
| **kotlinx-coroutines-core** | **1.10.2** (already in catalog) | suspend functions, Flow | Already pinned in Phase 1 catalog (Phase 1 baseline confirmed compatible with Ktor 3.4.x). [VERIFIED: libs.versions.toml] |
| **AndroidX Room (KMP)** | **2.8.4** (latest stable) | Schema v1 (cookies table); future migrations Phase 3+ | KMP-first since 2.7; KSP-driven; supports `kspIosX64`/`kspIosArm64`/`kspIosSimulatorArm64`/`kspAndroid`. [VERIFIED: developer.android.com/kotlin/multiplatform/room] |
| **androidx.sqlite-bundled** | **2.6.2** (matches Room 2.8.4) | Bundled SQLite for KMP (replaces native iOS sqlite linkage) | Required for Room KMP; `BundledSQLiteDriver` in commonMain. [CITED: developer.android.com KMP Room guide] |
| **androidx.room.gradle.plugin** | **2.8.4** | Schema directory + Room build hooks | Required for Room KMP. Apply via `alias(libs.plugins.androidx.room)`. [CITED: same guide] |
| **KSP** | **2.2.20-2.0.4** (already in catalog) | Compile-time annotation processing for Room | Must match Kotlin major.minor (2.2.20). Already pinned in Phase 1 catalog. [VERIFIED: libs.versions.toml line 3] |
| **Kermit** | **2.0.4** (already in catalog) | Logging facade — Logcat (Android) / OSLog (iOS) | Pin used by Ktor Logging plugin's custom `Logger` interface to write through Kermit. [VERIFIED: STACK.md + libs.versions.toml line 17] |

### Supporting (verified)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **ktor-client-core** | 3.4.3 | Common HttpClient API | All commonMain code — HttpClientFactory entry point |
| **ktor-client-darwin** | 3.4.3 | Native iOS engine (NSURLSession) | iosMain only — actual engine binding |
| **ktor-client-okhttp** | 3.4.3 | Android engine (HTTP/2-capable) | androidMain only — actual engine binding |
| **ktor-client-content-negotiation** | 3.4.3 | Plugin: JSON ContentType handling | All HttpClient instances |
| **ktor-serialization-kotlinx-json** | 3.4.3 | Bridge between Ktor + kotlinx.serialization | Required by ContentNegotiation |
| **ktor-client-logging** | 3.4.3 | Logging plugin with `sanitizeHeader { ... }` | Debug + release (with redactor); pinned to LogLevel.NONE in release per D-28 |
| **ktor-client-mock** | 3.4.3 | MockEngine for tests | commonTest — replaces Darwin/OkHttp; replays sanitized HAR fixtures (D-25) |
| **androidx-room-runtime** | 2.8.4 | Common Room API | commonMain |
| **androidx-room-compiler** | 2.8.4 | KSP processor | Added to root project KSP configurations (kspAndroid, kspIosX64, kspIosArm64, kspIosSimulatorArm64) |
| **androidx-room-sqlite-wrapper** | 2.8.4 | androidMain wrapper | androidMain only — needed because Android target uses different SQLite path resolution |

### Alternatives Considered (locked out by CONTEXT)

| Instead of | Could Use | Tradeoff | Phase 2 verdict |
|------------|-----------|----------|-----------------|
| Room 2.8.x KMP | SQLDelight 2.x (used in ARCHITECTURE.md examples) | Schema-first SQL files, no annotation processing — historically more KMP-native | **REJECTED** — STACK.md locks Room. Note: `:core:database/sqldelight` references in ARCHITECTURE.md are pre-Phase-1 historical; ignore. |
| Ktor Auth plugin (`bearer { loadTokens / refreshTokens }`) | Custom HttpSend interceptor | Bearer plugin is for token-based auth; AVERS uses cookies, not bearer tokens. Bearer's `refreshTokens` ergonomics don't fit cookie-session refresh. | **CUSTOM HttpSend INTERCEPTOR** — recommended for D-26 auto-relogin (см. Code Example #5 below). |
| MockK | Mokkery (already locked) | MockK does not work on Kotlin/Native iOS — confirmed STACK.md What NOT to Use. | **MOKKERY** — already in lintech-test convention plugin. |
| LogLevel.HEADERS in debug | LogLevel.ALL (D-28 chosen) | LogLevel.ALL exposes bodies — addendum mandates body-redactor must run too. ALL is safe IFF redactor active. | **ALL + REDACTOR** — D-28 locked; verify canary-test runs in debug-build CI. |
| Single mega-module `:core:api` | `:core:network` + `:core:api-avers-v4` (D-07) | Mega-module conflates transport + contract; can't sunset old build versions in parallel. | **TWO MODULES** — D-07 locked. |

**Installation diff for `gradle/libs.versions.toml`:**

```toml
[versions]
# Bumped (currently registered, NOT yet consumed in Phase 1):
kotlinxSerialization = "1.9.0"        # was 1.7.3
kotlinxDatetime = "0.8.0-rc01"        # was 0.6.2

# New additions for Phase 2:
ktor = "3.4.3"
room = "2.8.4"
sqlite = "2.6.2"

[libraries]
# Ktor (commonMain)
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }

# Ktor (engines — platform-specific)
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }

# Ktor (commonTest)
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }

# Room KMP (commonMain)
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }

# Room (androidMain — sqlite wrapper, only needed if Android target uses different SQLite resolution; verify with Room 2.8 docs)
androidx-room-sqlite-wrapper = { module = "androidx.room:room-sqlite-wrapper", version.ref = "room" }

[plugins]
androidxRoom = { id = "androidx.room", version.ref = "room" }
```

Note: `ksp` plugin alias already exists in Phase 1 catalog (line 57); do not re-declare.

**Version verification:**
- Ktor 3.4.3 — verified current stable as of 2026-04 (CONTEXT canonical refs cites Kotlin Blog 2026-01 release of 3.4.0; STACK.md verifies 3.4.3 stable) [VERIFIED]
- Room 2.8.4 — verified via developer.android.com/kotlin/multiplatform/room (page returned `room = "2.8.4"` and `sqlite = "2.6.2"` in current sample) [VERIFIED via WebFetch]
- kotlinx-serialization 1.9.0 — locked in STACK.md; required by Ktor 3.4 + kotlinx-datetime 0.8 Instant serialization [VERIFIED: STACK.md compatibility table]
- KSP 2.2.20-2.0.4 — already pinned in Phase 1 catalog; matches Kotlin 2.2.20 major.minor [VERIFIED]

## Architecture Patterns

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Phase 2 Network Layer                             │
│                                                                              │
│   ┌────────────────────────────┐                                             │
│   │  :core:api-avers-v4        │  ← versioned to AVERS build 23813           │
│   │                            │                                             │
│   │  AversApi.fetchGrades(...) │  → suspend fun → ApiResult<List<GradeDto>>  │
│   │  AversApi.fetchSchedule(...)                                             │
│   │  AversApi.fetchHomework(...)                                             │
│   │  AversApi.fetchAttendance(...)                                           │
│   │  AversApi.fetchMessages(...)                                             │
│   │  AversAuthApi.login(login, pwd)                                          │
│   │                            │                                             │
│   │  ApiResult<T>:             │ KillSwitchClient (D-12, D-13, D-14)         │
│   │   Success | Mismatch       │   ↓ fetch GitHub Pages api-config.json      │
│   │   Failure(AversApiError)   │   ↓ fail-open on timeout/404                │
│   │                            │                                             │
│   │  AntiBotDetector           │  → if HTML response detected →              │
│   │                            │     emit AversApiError.AntiBotChallenge     │
│   └─────────┬──────────────────┘                                             │
│             │ depends on                                                      │
│             ▼                                                                │
│   ┌────────────────────────────┐                                             │
│   │  :core:network             │  ← AVERS-agnostic Ktor infrastructure        │
│   │                            │                                             │
│   │  HttpClientFactory         │   .forAccount(accountId): HttpClient        │
│   │   ↓ caches per accountId   │   .evict(accountId)                         │
│   │   ↓ closes on AccountDataPurger.purge()                                  │
│   │                            │                                             │
│   │  Plugin chain (Ktor 3.4.3):                                              │
│   │    Logging (sanitizeHeader + custom HttpRequestRedactor for bodies)      │
│   │    ContentNegotiation(Json { ignoreUnknownKeys = true })                 │
│   │    HttpCookies(storage = RoomCookiesStorage(db, accountId))              │
│   │    DefaultRequest(baseUrl, Accept-Language, Accept, X-Requested-With)    │
│   │    HttpTimeout(connect=10s, request=30s, socket=15s)                     │
│   │    HttpRequestRetry(exponential 1s..30s, maxRetries=5, retry-after-aware)│
│   │    HttpSend (custom 401-interceptor calling CredentialProvider.get +     │
│   │              re-login + retry; emits Unauthorized if re-login fails)     │
│   │                            │                                             │
│   │  RoomCookiesStorage : CookiesStorage  (custom Ktor adapter)              │
│   │                            │                                             │
│   │  AccountDataPurger         │  .purge(accountId) — close client + DAO     │
│   │                            │     wipe + db file delete                   │
│   │  CredentialProvider (interface — Phase 3 supplies KVault impl)           │
│   │                            │                                             │
│   │  HttpRequestRedactor       │  masks password/pwd/pass/cookie/auth/token  │
│   │                            │  in bodies (regex, case-insensitive)        │
│   └─────────┬──────────────────┘                                             │
│             │ depends on                                                      │
│             ▼                                                                │
│   ┌────────────────────────────┐                                             │
│   │  :core:database            │  ← Room schema v1 (cookies-only)             │
│   │                            │                                             │
│   │  JournalDatabase (RoomDatabase)                                          │
│   │   @Entity CookieEntity (id, name, value, domain, path,                   │
│   │                          expiresAt, httpOnly, secure)                    │
│   │   @Dao CookieDao  (suspend insert / get / clear / clearByDomain)         │
│   │                            │                                             │
│   │  expect/actual DatabaseFactory                                           │
│   │   androidMain: Room.databaseBuilder<JournalDatabase>(ctx, file)          │
│   │   iosMain: Room.databaseBuilder<JournalDatabase>(name = path)            │
│   │     (path = NSDocumentDirectory + "/journal_${accountId}.db")            │
│   │     (path attribute NSFileProtectionComplete — D-20)                     │
│   │                            │                                             │
│   │  AndroidManifest: allowBackup="false" (D-21)                             │
│   │  expect object JournalDatabaseConstructor : RoomDatabaseConstructor<…>   │
│   └────────────────────────────┘                                             │
│             │ depends on                                                      │
│             ▼                                                                │
│   ┌────────────────────────────┐                                             │
│   │  :core:platform (Phase 1)  │  ← already exists with UrlOpener            │
│   │                            │                                             │
│   │  Phase 2 may extend with NetworkMonitor — DEFERRED to Phase 4 per D-19   │
│   │  Phase 2 may extend with CredentialProvider — recommendation: keep as    │
│   │     pure interface in :core:network, no expect/actual needed             │
│   └────────────────────────────┘                                             │
│                                                                              │
│   External / out-of-band:                                                    │
│   ┌────────────────────────────┐  ┌────────────────────────────────────────┐ │
│   │  fixtures/sanitized/*.har  │  │ docs/aversApiV4_23813.md               │ │
│   │  (replayed by MockEngine)  │  │ (human-readable contract documentation)│ │
│   └────────────────────────────┘  └────────────────────────────────────────┘ │
│   ┌────────────────────────────┐  ┌────────────────────────────────────────┐ │
│   │  tools/sanitize-har.py     │  │ docs/api-config.json (kill-switch)     │ │
│   │  (Python, manual + CI lint)│  │ deployed via existing pages.yml        │ │
│   └────────────────────────────┘  └────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘

Data flow on a typical fetchGrades(period) call:
  caller → AversApi.fetchGrades(period)
       → killSwitch.checkOrFailOpen()  (in-memory cached after first cold-start fetch)
       → factory.forAccount("default").get(URL, params)
            → Ktor plugin chain: HttpCookies reads from RoomCookiesStorage
            → DefaultRequest applies baseUrl + Accept-Language=ru-RU + UA mimic
            → If 401: HttpSend interceptor → CredentialProvider.get("default")
                      → if Some(login,pwd): AversAuthApi.login → retry
                      → if None or re-login 401: emit Unauthorized
            → On JSON: ContentNegotiation parses to JsonElement
       → AntiBotDetector: if response is HTML → AversApiError.AntiBotChallenge(rawDump)
       → strict parse: requireNotNull(success) + decode<List<GradeDto>>
            → on missing field → ApiResult.Mismatch(rawDump, missingFields, "grades")
       → on success → ApiResult.Success(grades)
```

### Recommended Module Structure

```
LintehJournal/
├── core/
│   ├── platform/                                  # Phase 1, unchanged
│   ├── ui/                                        # Phase 1, unchanged
│   ├── network/                                   # Phase 1 SKELETON → Phase 2 fills
│   │   ├── build.gradle.kts                       # +ktor + sqlite-bundled propagation; depends on :core:database
│   │   └── src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/
│   │       ├── HttpClientFactory.kt               # forAccount(id) cached map
│   │       ├── plugins/
│   │       │   ├── AversAuthInterceptor.kt        # HttpSend 401-interceptor (D-26)
│   │       │   └── HttpRequestRedactor.kt         # body-mask regex (D-28 addendum)
│   │       ├── cookies/
│   │       │   └── RoomCookiesStorage.kt          # CookiesStorage impl backed by :core:database
│   │       ├── credentials/
│   │       │   └── CredentialProvider.kt          # interface (Phase 3 supplies KVault impl)
│   │       └── lifecycle/
│   │           └── AccountDataPurger.kt           # purge(accountId): close client + drop DB
│   │
│   ├── database/                                  # NEW Phase 2 module
│   │   ├── build.gradle.kts                       # plugins: lintech-kmp + lintech-test + ksp + androidx.room
│   │   └── src/
│   │       ├── commonMain/kotlin/io/github/chudoxl/linteh/journal/core/database/
│   │       │   ├── JournalDatabase.kt             # @Database(version=1, entities=[CookieEntity::class])
│   │       │   ├── DatabaseFactory.kt             # expect — actuals in androidMain/iosMain
│   │       │   ├── JournalDatabaseConstructor.kt  # expect object : RoomDatabaseConstructor<JournalDatabase>
│   │       │   ├── entity/
│   │       │   │   └── CookieEntity.kt
│   │       │   ├── dao/
│   │       │   │   └── CookieDao.kt
│   │       │   └── migration/
│   │       │       └── MigrationTemplate.kt       # v1→v2 stub for Phase 3 (commented, NOT applied)
│   │       ├── commonMain/sqldelight/             # ABSENT — using Room, not SQLDelight
│   │       ├── androidMain/kotlin/.../database/
│   │       │   └── DatabaseFactory.android.kt     # actual; uses Context.getDatabasePath
│   │       ├── androidMain/AndroidManifest.xml    # allowBackup="false"
│   │       └── iosMain/kotlin/.../database/
│   │           └── DatabaseFactory.ios.kt         # actual; NSDocumentDirectory + NSFileProtectionComplete
│   │
│   └── api-avers-v4/                              # NEW Phase 2 module
│       ├── build.gradle.kts                       # plugins: lintech-kmp + lintech-test
│       └── src/
│           ├── commonMain/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/
│           │   ├── AversApi.kt                    # interface + DefaultAversApi impl (groups all 6 endpoints)
│           │   ├── AversAuthApi.kt                # login/logout (separate from data-fetch)
│           │   ├── result/
│           │   │   ├── ApiResult.kt               # sealed class
│           │   │   └── AversApiError.kt           # sealed class (D-11)
│           │   ├── dto/                           # @Serializable data classes
│           │   │   ├── envelope/
│           │   │   │   └── AversEnvelope.kt       # {success, data, msg?} unwrap helper
│           │   │   ├── GradeDto.kt
│           │   │   ├── LessonDto.kt
│           │   │   ├── HomeworkDto.kt
│           │   │   ├── AttendanceDto.kt
│           │   │   ├── MessageDto.kt
│           │   │   └── Period.kt                  # Phase 2 simple data class (LocalDate from..to)
│           │   ├── antibot/
│           │   │   └── AntiBotDetector.kt         # detect HTML CAPTCHA pages
│           │   └── killswitch/
│           │       ├── KillSwitchClient.kt        # fetch + parse + fail-open
│           │       └── KillSwitchConfig.kt        # @Serializable schema
│           └── commonTest/kotlin/.../api/avers/v4/
│               ├── fixtures/
│               │   └── HarReplayMockEngine.kt     # helper: load fixtures/sanitized/*.har, return MockEngine
│               └── (tests per endpoint, see Validation Architecture)
│
├── composeApp/                                    # unchanged in Phase 2 (no new screen wiring)
│
├── tools/                                         # NEW Phase 2 directory
│   ├── sanitize-har.py                            # Python 3.12 (system; no venv to keep dev workflow simple)
│   ├── sanitize-rules.yaml                        # PII patterns (configurable; tracked in git)
│   └── README.md                                  # How to capture + sanitize + verify
│
├── fixtures/                                      # NEW Phase 2 directory
│   ├── raw/                                       # GITIGNORED — real ФИО, real cookies
│   ├── sanitized/                                 # COMMITTED — for test replay
│   │   ├── account-A/
│   │   │   ├── login.har
│   │   │   ├── grades.har
│   │   │   ├── schedule.har
│   │   │   ├── homework.har
│   │   │   ├── attendance.har
│   │   │   └── messages.har
│   │   └── account-B/
│   │       └── (same six)
│   └── README.md
│
├── docs/                                          # GitHub Pages source (Phase 1 baseline)
│   ├── index.html                                 # unchanged
│   ├── privacy/                                   # unchanged
│   └── api-config.json                            # NEW — kill-switch endpoint
│       # { "latestSupportedAversBuild": "23813",
│       #   "message": "",
│       #   "severity": "info",
│       #   "minAppVersion": "0.2.0" }
│
└── docs/aversApiV4_23813.md                       # NEW — human-readable contract document
                                                    # (ALSO referenced from README + composeApp About screen Phase 4)
```

**Module dependency graph (D-07 enforces top-to-bottom):**

```
:composeApp (will not import :core:api-avers-v4 directly in Phase 2 — that's Phase 4 wiring)
    ↓
:core:ui (Phase 1)         :core:api-avers-v4 (NEW — Phase 4+ feature modules import this)
    ↓                            ↓
:core:network (Phase 1 → Phase 2 fills)
    ↓
:core:database (NEW Phase 2)
    ↓
:core:platform (Phase 1)
```

**Phase 2 wiring rule:** `:composeApp` does NOT import `:core:api-avers-v4` or `:core:network` in Phase 2 — those will be wired into Phase 3 Auth UI / Phase 4 Grades feature. Phase 2 ends with all three new modules built, tested, and isolated.

### Pattern 1: HttpClientFactory.forAccount(accountId)

**What:** Per-account `HttpClient` cached in a map. On first call for a new accountId, builds a new client with that account's `RoomCookiesStorage`. On `purge(accountId)`, closes the client and removes from cache.

**When to use:** Every fetch goes through this factory; never instantiate `HttpClient {}` ad-hoc.

**Why:** Cookie isolation between accounts (Pattern 3 of ARCHITECTURE.md). Phase 2 has only `accountId="default"`, but the API shape locks the invariant.

```kotlin
// :core:network/src/commonMain/kotlin/.../HttpClientFactory.kt
package io.github.chudoxl.linteh.journal.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HttpClientFactory(
    private val engine: HttpClientEngine,
    private val cookiesStorageProvider: (accountId: String) -> CookiesStorage,
    private val credentialProvider: CredentialProvider,
    private val redactor: HttpRequestRedactor,
    private val baseUrl: String = "https://journal.school28-kirov.ru",
    private val isDebug: Boolean,
) {
    private val mutex = Mutex()
    private val clients = mutableMapOf<String, HttpClient>()

    suspend fun forAccount(accountId: String): HttpClient = mutex.withLock {
        clients.getOrPut(accountId) {
            buildClient(accountId)
        }
    }

    suspend fun evict(accountId: String): Unit = mutex.withLock {
        clients.remove(accountId)?.close()
    }

    private fun buildClient(accountId: String): HttpClient = HttpClient(engine) {
        install(HttpCookies) {
            storage = cookiesStorageProvider(accountId)
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            })
        }
        install(Logging) {
            logger = KermitKtorLogger
            level = if (isDebug) LogLevel.ALL else LogLevel.NONE
            sanitizeHeader { name ->
                name.equals(HttpHeaders.Authorization, ignoreCase = true)
                    || name.equals(HttpHeaders.Cookie, ignoreCase = true)
                    || name.equals(HttpHeaders.SetCookie, ignoreCase = true)
            }
            // D-28 addendum: body redaction MUST run in debug too
            filter { _ -> true }
            // body redaction implemented via wrapping the logger.write call
            // (see HttpRequestRedactor in pseudocode below)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 15_000
        }
        install(HttpRequestRetry) {
            // D-05 throttling — exponential backoff with retry-after honored
            maxRetries = 5
            retryOnExceptionIf { _, cause -> cause is IOException }
            retryIf { _, response ->
                response.status.value in listOf(429, 502, 503, 504)
            }
            exponentialDelay(base = 2.0, baseDelayMs = 1_000, maxDelayMs = 30_000)
            // honor Retry-After
            modifyRequest { request ->
                // request modification hook — used by API layer if needed
            }
        }
        install(DefaultRequest) {
            url(baseUrl)
            header(HttpHeaders.AcceptLanguage, "ru-RU,ru;q=0.9,en;q=0.8")
            header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
            header("X-Requested-With", "XMLHttpRequest")  // ExtJS convention
            header(HttpHeaders.UserAgent,
                // Phase 2 starting choice: desktop UA matching capture (D-02 mobile-UA divergence risk)
                // Mobile UA can be swapped here in a single line when Phase 4 first Android run validates parity
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/17.4 Safari/605.1.15"
            )
        }
        // 401 auto re-login (D-26)
        plugin(HttpSend).intercept { request ->
            val originalCall = execute(request)
            if (originalCall.response.status == HttpStatusCode.Unauthorized) {
                val creds = credentialProvider.get(accountId)
                if (creds != null) {
                    // call AversAuthApi.login(creds) — interface available via DI
                    // re-execute originalCall.request
                    val retryCall = execute(request)
                    if (retryCall.response.status == HttpStatusCode.Unauthorized) {
                        // emit AversApiError.Unauthorized via response transformation
                    }
                    retryCall
                } else originalCall
            } else originalCall
        }
    }
}
```

**Source:** Synthesized from [Ktor Cookies docs](https://ktor.io/docs/client-cookies.html), [Ktor HttpRequestRetry docs](https://ktor.io/docs/client-request-retry.html), [Ktor HttpSend docs](https://ktor.io/docs/client-auth.html), and ARCHITECTURE.md Pattern 3.

### Pattern 2: RoomCookiesStorage as Ktor CookiesStorage adapter

**What:** Implement `io.ktor.client.plugins.cookies.CookiesStorage` interface against a Room DAO.

**Methods to implement (per Ktor 3.4 API):**
```kotlin
interface CookiesStorage : Closeable {
    suspend fun get(requestUrl: Url): List<Cookie>
    suspend fun addCookie(requestUrl: Url, cookie: Cookie)
}
```

**Cookie matching logic:** `requestUrl.host` must match cookie `domain` (with leading-dot subdomain rule); `requestUrl.encodedPath` must start with cookie `path`; cookie `expiresAt` must be in the future (or null = session cookie, kept for the lifetime of HttpClient — Phase 2 keeps these in DB anyway since session is multi-app-launch).

```kotlin
// :core:network/src/commonMain/kotlin/.../cookies/RoomCookiesStorage.kt
class RoomCookiesStorage(
    private val dao: CookieDao,
    private val accountId: String,  // Phase 2 = "default"; Phase 5 = real ID
    private val clock: Clock = Clock.System,
) : CookiesStorage {
    override suspend fun get(requestUrl: Url): List<Cookie> {
        val now = clock.now()
        return dao.findFor(host = requestUrl.host, path = requestUrl.encodedPath)
            .filter { it.expiresAtEpochMillis == null || it.expiresAtEpochMillis > now.toEpochMilliseconds() }
            .map { it.toKtorCookie() }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        val entity = cookie.toEntity(accountId, defaultDomain = requestUrl.host)
        dao.upsert(entity)
    }

    override fun close() { /* DAO does not own the database */ }
}

@Entity(tableName = "cookies", primaryKeys = ["accountId", "name", "domain", "path"])
data class CookieEntity(
    val accountId: String,
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAtEpochMillis: Long?,
    val httpOnly: Boolean,
    val secure: Boolean,
)

@Dao
interface CookieDao {
    @Query("SELECT * FROM cookies WHERE accountId = :accountId AND :host LIKE '%' || domain AND :path LIKE path || '%'")
    suspend fun findFor(accountId: String, host: String, path: String): List<CookieEntity>

    @Upsert
    suspend fun upsert(cookie: CookieEntity)

    @Query("DELETE FROM cookies WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: String)
}
```

**Source:** Pattern adapted from [Ktor Cookies docs](https://ktor.io/docs/client-cookies.html) + [Ktor API CookiesStorage reference](https://api.ktor.io/ktor-client/ktor-client-core/io.ktor.client.plugins.cookies/-cookies-storage/index.html). The `LIKE`-pattern matching for cookie domain/path is a standard simplification — for sub-domain-strictness Phase 5 may upgrade to exact-match logic.

### Pattern 3: Strict-but-Tolerant JSON Parsing (D-09)

**What:** `Json { ignoreUnknownKeys = true }` for forward compatibility, but every endpoint wraps decoding in a strict validator that calls `requireNotNull` on critical fields and maps any failure to `ApiResult.Mismatch`.

```kotlin
// :core:api-avers-v4/.../AversApi.kt
suspend fun fetchGrades(accountId: String, period: Period): ApiResult<List<GradeDto>> = runCatching {
    val raw: JsonElement = httpClient.get("/journal/getMarks") {
        parameter("from", period.from.toString())
        parameter("to", period.to.toString())
    }.body()

    // 1) Anti-bot check — if HTML page, raw is a JsonPrimitive containing the HTML or fails decoding
    if (antiBotDetector.isCaptcha(raw)) {
        return ApiResult.Failure(AversApiError.AntiBotChallenge(raw.toString()))
    }

    // 2) Strict envelope check
    val envelope = raw.jsonObject
    val success = envelope["success"]?.jsonPrimitive?.boolean
        ?: return ApiResult.Mismatch(raw.toString(), listOf("success"), "grades")

    if (!success) {
        val msg = envelope["msg"]?.jsonPrimitive?.contentOrNull
        return ApiResult.Failure(AversApiError.Server(httpCode = 200))  // ExtJS server-error in 200 envelope
    }

    val data = envelope["data"]
        ?: return ApiResult.Mismatch(raw.toString(), listOf("data"), "grades")

    // 3) Tolerant parse of data array
    val grades: List<GradeDto> = json.decodeFromJsonElement(ListSerializer(GradeDto.serializer()), data)
    ApiResult.Success(grades)
}.getOrElse { e ->
    when (e) {
        is HttpRequestTimeoutException, is IOException ->
            ApiResult.Failure(AversApiError.Network)
        is SerializationException ->
            ApiResult.Mismatch(rawDump = "(serialization-failed)", missingFields = listOf(e.message ?: "?"), endpoint = "grades")
        is ResponseException ->
            ApiResult.Failure(AversApiError.Server(e.response.status.value))
        else -> ApiResult.Failure(AversApiError.Network)
    }
}
```

### Anti-Patterns to Avoid

- **In-memory `AcceptAllCookiesStorage`:** loses cookies between app launches → forces re-login on every cold-start → triggers anti-bot. Use RoomCookiesStorage from day 1 (D-18).
- **Logging headers without `sanitizeHeader`:** Ktor Logging plugin's default does NOT redact Authorization/Cookie/Set-Cookie. Always configure `sanitizeHeader { ... }` (D-28).
- **Using Ktor Auth `bearer { ... }` plugin:** AVERS uses cookie-session, not bearer tokens; Auth plugin's `loadTokens`/`refreshTokens` ergonomics fight the model. Use a custom `HttpSend` interceptor for D-26.
- **Top-level `val httpClient = HttpClient { ... }`:** singleton across accounts → cross-account cookie leak (PITFALLS.md #6). All clients must come from the factory.
- **`expect class JournalDatabase`:** difficult to test, fights Koin/DI. Use `expect object JournalDatabaseConstructor : RoomDatabaseConstructor<JournalDatabase>` (Room 2.8 pattern) + plain class `JournalDatabase`.
- **Catching exceptions inside endpoint functions and rethrowing as `Throwable`:** swallows the typed `AversApiError` taxonomy. Always map to `ApiResult.Failure` at the boundary.
- **Hardcoded base URL in API module:** Phase 2 has only one school, but reuse later (`avers-v4-school28`, `avers-v4-school42`) requires baseUrl as constructor parameter.
- **Logging plugin enabled with LogLevel.ALL in release:** D-28 mandates `LogLevel.NONE` in release; if redactor were buggy, ALL would expose passwords. Defense in depth — release just emits nothing.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP retry with exponential backoff | Custom retry-loop wrapping every call | Ktor `HttpRequestRetry` plugin | Plugin handles `Retry-After` header + idempotency + concurrency safely. [VERIFIED: ktor.io/docs/client-request-retry.html] |
| Cookie persistence | Custom file-based serialization | Ktor `HttpCookies` + custom `CookiesStorage` impl on Room | Plugin handles parsing/expiry/domain matching against requestUrl. We only implement the storage interface. |
| JSON tolerant decoding | Hand-written field walking | `kotlinx.serialization` `Json { ignoreUnknownKeys = true }` | Catches all minor schema additions automatically; `@SerialName` aliases for ExtJS field names. |
| HAR file parsing in tests | Custom parser | Standard JSON parsing — HAR is JSON | HAR 1.2 is documented JSON-schema; can be loaded via kotlinx.serialization or Python's `json` module. |
| HAR sanitization | Custom regex script (in-house from scratch) | Adapt patterns from public tools (`Edgio/har-tools`, `solentlabs/har-capture`) but keep our own minimal Python script — domain-specific PII (Russian ФИО transliteration, AVERS-specific cookie names) is custom. | Don't pull a heavy dependency for one-off dev tooling; reuse pattern lists. |
| Local notification scheduling | We're not using it in Phase 2 | (Phase 6: Alarmee) | Out of scope. |
| Cross-platform secure storage | We're not implementing in Phase 2 | (Phase 3: KVault per STACK.md) | Phase 2 only declares the `CredentialProvider` interface. |
| KMP coroutine scope management | Custom `CoroutineScope` factory per HttpClient | Ktor's built-in HttpClient owns its scope; close() cancels children | Don't fight the framework. |

**Key insight:** Phase 2 sits on a stack where every "interesting" concern (HTTP plumbing, cookies, JSON, retries, mocking) is a solved problem in Ktor 3.4 + kotlinx.serialization 1.9 + Room 2.8 KMP. Custom code is needed only at three boundaries: (a) Room-as-CookieStorage adapter (~50 LoC), (b) HttpRequestRedactor for body-mask (~30 LoC), (c) AVERS-contract DTO + sealed errors + canary (driven by capture content, not pluggable). Keep custom code minimal.

## Common Pitfalls

### Pitfall 1: Mobile UA divergence (D-02 known risk)

**What goes wrong:** Capture is on Chrome desktop. AVERS may serve a mobile-optimized ExtJS variant when the User-Agent matches mobile Safari. The DTO contract built from desktop captures won't match mobile responses → all endpoints return `ApiResult.Mismatch` → first Android run in Phase 4 finds nothing works.

**Why it happens:** AVERS frontend likely uses `userAgent.match(/iphone|android/i)` server-side branching (typical for Java/Tomcat ExtJS in 2010s).

**How to avoid:** Phase 2 captures are with desktop UA — but the HttpClient sends a desktop Safari UA in `DefaultRequest`. The first task in Phase 4 (real Android device run) **must** be: open the app, attempt login, log the response shape, compare against `aversApiV4_23813.md` desktop contract. If divergence found — switch UA to mobile Safari, possibly re-capture & re-sanitize a mobile-fixture set.

**Warning signs:** Phase 4 sees `ApiResult.Mismatch` on every endpoint while Phase 2 unit tests pass against fixtures.

### Pitfall 2: Body redactor regex over-matches benign substrings

**What goes wrong:** Redactor masks any body containing `password=` — but a homework body might contain "You forgot your password to the website" → that benign Russian text gets redacted in logs → harder to debug.

**Why it happens:** Naive `body.replace(/password.*?[\s&]/, "***")` is too greedy.

**How to avoid:** Restrict to known sensitive contexts: form-encoded keys (`^|&password=...&|$`), JSON keys (`"password":\s*"..."`), header pattern (`Authorization: ...`). Test redactor with both sensitive and benign canary strings.

**Warning signs:** Test bodies with non-secret "password"-as-content text get incorrectly redacted in test output.

### Pitfall 3: Room 2.8 KMP requires KSP per-target declaration in ROOT build.gradle.kts (not in module's)

**What goes wrong:** Adding `ksp(libs.androidx.room.compiler)` to module-level `build.gradle.kts` works for Android but does NOT process iOS targets → Room generates Android implementations only → iOS link fails or runtime crash.

**Why it happens:** KSP for KMP requires explicit per-target configuration: `kspAndroid`, `kspIosX64`, `kspIosArm64`, `kspIosSimulatorArm64`. The Room KMP guide [VERIFIED: developer.android.com/kotlin/multiplatform/room] places these in the `dependencies { add("kspAndroid", ...) }` block at the **root project** level (or module-level using `dependencies` block, not `commonMain.dependencies`).

**How to avoid:** Follow the official Room KMP setup guide exactly — include all 4 ksp* targets. CI must run `./gradlew :core:database:compileKotlinIosX64` (already in Phase 1 toolkit) to verify iOS compilation.

**Warning signs:** Build succeeds locally on Android but fails in CI's iOS macos-15 job at `linkDebugFrameworkIosX64`.

### Pitfall 4: HttpSend interceptor recursion on re-login (D-26)

**What goes wrong:** Auto-relogin interceptor catches 401 → calls `aversAuth.login(creds)` → that login itself returns 401 (wrong password) → interceptor catches that 401 → tries to login again → infinite loop.

**Why it happens:** Interceptor is global; any 401 (including from login endpoint) triggers it.

**How to avoid:** Mark login requests with a header (e.g., `X-Avers-Auth-Request: true`) and skip the interceptor for them; OR check `request.url.encodedPath == "/api/login"` and bypass.

**Warning signs:** Test "login with wrong password" hangs indefinitely; logs show repeated `POST /api/login` 401s.

### Pitfall 5: kotlinx-datetime 0.8.0-rc01 Instant serialization breaking change

**What goes wrong:** Pattern from STACK.md: 0.7.x → 0.8.x changed `Instant` serialization behavior. If `GradeDto.dateAdded: Instant` is annotated naively, parsing fails on AVERS-formatted timestamps.

**Why it happens:** Release notes specify exact format expected (ISO 8601 strict). AVERS may emit `"2026-04-28 12:34:56"` (space-separated) or epoch-seconds.

**How to avoid:** Use `LocalDateTime` + explicit timezone for AVERS school-clock timestamps; keep `Instant` only for fields where AVERS emits ISO 8601 with `Z` or offset. Always test deserialization against real fixture content.

**Warning signs:** `SerializationException: expected Instant got '2026-04-28 12:34:56'`.

### Pitfall 6: Capturing manual HAR while ad-blocker / extension is active

**What goes wrong:** Captured HAR contains junk requests to ad-block update endpoints, telemetry blockers, etc. Sanitization script missing those patterns leaks meta-PII (extension IDs reveal browser config).

**How to avoid:** Capture in Chrome incognito + clean profile + extensions disabled. Document in `tools/README.md`.

### Pitfall 7: Cookie domain matching strict-vs-loose (RoomCookiesStorage)

**What goes wrong:** AVERS may set `Domain=.school28-kirov.ru` (with leading dot) for a cookie, our Storage saves it as `domain="school28-kirov.ru"` (without dot), then `get()` is called for `journal.school28-kirov.ru` → strict-equal match fails → cookie not sent → server treats as anonymous → 401 storm.

**How to avoid:** RFC 6265 says: a cookie with `Domain=.example.com` matches `*.example.com` (subdomains). Implement subdomain-suffix matching via `requestUrl.host.endsWith(cookie.domain)`. The `LIKE '%' || domain` pattern in the SQL example above does this.

**Warning signs:** First login succeeds, immediately followed by 401 on subsequent calls; cookies are in DB but not sent.

### Pitfall 8: Phase 1 BLOCKER 1 fix — non-UI modules must NOT pull compose deps

**What goes wrong:** Apply `lintech-compose` plugin to `:core:database` "for consistency" → drags `compose.runtime` etc. into a non-UI module → bloats iOS framework + hides real test errors.

**How to avoid:** New Phase 2 modules apply ONLY `lintech-kmp` + `lintech-test`. Verify by checking the `build.gradle.kts` plugins block has no `lintech-compose`. (Same convention as `:core:network` in Phase 1.)

## Code Examples

### Example 1: `:core:database/build.gradle.kts`

```kotlin
plugins {
    id("lintech-kmp")
    id("lintech-test")
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidxRoom)
}

kotlin {
    androidTarget()
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "database"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(project(":core:platform"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.room.sqlite.wrapper)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Per-target KSP — required for Room KMP
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosX64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
}

android {
    namespace = "io.github.chudoxl.linteh.journal.core.database"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}
```

### Example 2: `:core:database/src/androidMain/AndroidManifest.xml` (D-21)

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest>
    <application
        android:allowBackup="false"
        android:fullBackupContent="false"
        tools:replace="android:allowBackup"
        xmlns:tools="http://schemas.android.com/tools" />
</manifest>
```

(This manifest gets merged into the final app via Android manifest merger.)

### Example 3: iOS DatabaseFactory with NSFileProtectionComplete (D-20)

```kotlin
// :core:database/src/iosMain/kotlin/.../DatabaseFactory.ios.kt
package io.github.chudoxl.linteh.journal.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual object DatabaseFactory {
    actual fun create(accountId: String): JournalDatabase {
        val docsDir = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )!!

        // D-20: ensure parent directory has NSFileProtectionComplete attribute
        // (file inherits parent's protection class on iOS 17+ when created)
        NSFileManager.defaultManager.setAttributes(
            mapOf(NSFileProtectionKey to NSFileProtectionComplete),
            ofItemAtPath = docsDir.path!!,
            error = null,
        )

        val dbPath = "${docsDir.path}/journal_${accountId}.db"

        return Room.databaseBuilder<JournalDatabase>(name = dbPath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}
```

**Source:** [VERIFIED: developer.android.com/kotlin/multiplatform/room iOS sample] + [CITED: developer.apple.com NSFileProtectionComplete docs] + the FileManager attribute-set pattern from "Securing Data in iOS — Chariot Solutions" via web search.

### Example 4: HttpRequestRedactor (D-28 mandatory addendum)

```kotlin
// :core:network/.../plugins/HttpRequestRedactor.kt
package io.github.chudoxl.linteh.journal.core.network.plugins

class HttpRequestRedactor {
    private val sensitiveKeys = listOf("password", "pwd", "pass", "cookie", "authorization", "set-cookie", "token")

    /**
     * Redacts sensitive values in form-encoded bodies, JSON bodies, and URL-encoded bodies.
     * Idempotent — applying twice is safe.
     */
    fun redact(body: String, contentType: String?): String {
        var result = body
        sensitiveKeys.forEach { key ->
            // Form-encoded: name=value
            result = result.replace(
                Regex("(?i)(\\b$key=)[^&\\s]*"),
                "$1***REDACTED***"
            )
            // JSON: "name": "value"
            result = result.replace(
                Regex("(?i)(\"$key\"\\s*:\\s*\")[^\"]*"),
                "$1***REDACTED***"
            )
        }
        return result
    }

    fun redactHeaderValue(headerName: String, headerValue: String): String =
        if (sensitiveKeys.any { it.equals(headerName, ignoreCase = true) }) "***REDACTED***"
        else headerValue
}
```

### Example 5: Custom Ktor HttpSend interceptor for 401 auto-relogin (D-26)

```kotlin
// :core:network/.../plugins/AversAuthInterceptor.kt
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpStatusCode

fun HttpClient.installAversAuthInterceptor(
    accountId: String,
    credentialProvider: CredentialProvider,
    loginCall: suspend (login: String, password: String) -> Boolean,  // returns true on success
) {
    plugin(HttpSend).intercept { request ->
        // Skip interceptor for the login endpoint itself (Pitfall #4 mitigation)
        if (request.url.encodedPath.contains("/login", ignoreCase = true)) {
            return@intercept execute(request)
        }

        val originalCall = execute(request)
        if (originalCall.response.status != HttpStatusCode.Unauthorized) {
            return@intercept originalCall
        }

        val creds = credentialProvider.get(accountId)
            ?: return@intercept originalCall  // no creds → caller will see 401 → maps to AversApiError.Unauthorized

        val ok = loginCall(creds.first, creds.second)
        if (!ok) return@intercept originalCall  // re-login failed → caller sees 401 → Unauthorized

        // Replay original request (HttpCookies plugin will use the new session cookies)
        execute(request)
    }
}
```

**Source:** Adapted from [Ktor HttpSend / Auth docs](https://ktor.io/docs/client-auth.html) + [Handling Token Expiration in Ktor — droidcon (2025-03)](https://www.droidcon.com/2025/03/06/handling-token-expiration-in-ktor-automatic-token-refresh-for-api-calls/).

### Example 6: AversApiError + ApiResult sealed hierarchy (D-11 verbatim)

```kotlin
// :core:api-avers-v4/.../result/ApiResult.kt
sealed class ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>()
    data class Mismatch(val rawDump: String, val missingFields: List<String>, val endpoint: String) : ApiResult<Nothing>()
    data class Failure(val error: AversApiError) : ApiResult<Nothing>()
}

// :core:api-avers-v4/.../result/AversApiError.kt
sealed class AversApiError {
    object Unauthorized : AversApiError()
    object AntiBotChallenge : AversApiError()  // NB: D-11 says object; AntiBotChallenge with rawDump payload would be data class
    object Network : AversApiError()
    data class ContractMismatch(val rawDump: String, val missingFields: List<String>, val endpoint: String) : AversApiError()
    data class Server(val httpCode: Int) : AversApiError()
    object KillSwitchTriggered : AversApiError()
}
```

**NOTE on D-11 vs D-06 tension:** D-11 declares `AntiBotChallenge` as `object` (no payload). D-06 says Phase 3 receives "raw HTML body in payload." If raw HTML is needed for the WebView fallback, planner should change `object AntiBotChallenge` → `data class AntiBotChallenge(val rawHtml: String)` — this is a strict superset of D-11 (no API breakage; sealed hierarchy still exhaustive). Recommend the planner make this small adjustment AND record it as a clarification, not a contradiction. **If user wants strict literal D-11**, the rawHtml stays in `Mismatch.rawDump` style on a parallel field — but that conflates two error categories. Plan for the `data class` form.

### Example 7: Kill-switch fetch + parse (D-12, D-13, D-14)

```kotlin
// :core:api-avers-v4/.../killswitch/KillSwitchClient.kt
@Serializable
data class KillSwitchConfig(
    val latestSupportedAversBuild: String,
    val message: String = "",
    val severity: Severity = Severity.info,
    val minAppVersion: String = "0.0.0",
)

@Serializable
enum class Severity { info, warning, block }

class KillSwitchClient(
    private val httpClient: HttpClient,  // can use the same factory client; kill-switch URL is non-AVERS
    private val killSwitchUrl: String = "https://chudoxl.github.io/LintehJournal/api-config.json",
    private val currentAversBuild: String = "23813",
    private val currentAppVersion: String,
) {
    private var cached: KillSwitchConfig? = null

    suspend fun checkOrFailOpen(): ApiResult<KillSwitchConfig> {
        // D-14: cold-start fetch, in-memory cache
        cached?.let { return ApiResult.Success(it) }

        return runCatching {
            val cfg: KillSwitchConfig = httpClient.get(killSwitchUrl) {
                timeout { requestTimeoutMillis = 5_000 }
            }.body()
            cached = cfg
            // D-12: severity == "block" → kill switch active
            if (cfg.severity == Severity.block) ApiResult.Failure(AversApiError.KillSwitchTriggered)
            else ApiResult.Success(cfg)
        }.getOrElse {
            // D-13: fail-open
            ApiResult.Success(KillSwitchConfig(latestSupportedAversBuild = currentAversBuild))
        }
    }
}
```

### Example 8: tools/sanitize-har.py skeleton (D-03)

```python
#!/usr/bin/env python3
# tools/sanitize-har.py
# Usage: python3 tools/sanitize-har.py fixtures/raw/login.har fixtures/sanitized/account-A/login.har
"""
Sanitizer for HAR files captured against journal.school28-kirov.ru.

Replaces:
- ФИО (Russian full names) — deterministic fakes (Иванов И.И., Петров П.П., Сидоров С.С.)
- Cookie values — random hex same length
- Photo URLs — placeholder-X.jpg
- Avatar/file URLs — preserve path shape, replace filename
Preserves:
- Endpoint paths, query parameter NAMES, JSON structure (keys + non-PII values)
- Grades (numeric + textual marks like '5', 'н', 'зач')
- Subject names, lesson times, room numbers
"""
import json
import re
import sys
import hashlib
import yaml
from pathlib import Path

# Load configurable rules from sanitize-rules.yaml (allows updates without code changes)
RULES_PATH = Path(__file__).parent / "sanitize-rules.yaml"
with open(RULES_PATH) as f:
    rules = yaml.safe_load(f)

# Russian name regex — Surname Initial.Initial. or full name
RU_FULLNAME_RE = re.compile(r'\b[А-ЯЁ][а-яё]{2,}\s+[А-ЯЁ][а-яё]{2,}(?:\s+[А-ЯЁ][а-яё]{2,})?\b')
RU_INITIAL_RE = re.compile(r'\b[А-ЯЁ][а-яё]{2,}\s+[А-ЯЁ]\.\s*[А-ЯЁ]\.\b')

NAME_POOL = [
    "Иванов И.И.", "Петров П.П.", "Сидоров С.С.",
    "Кузнецов К.К.", "Новиков Н.Н.",
]

def deterministic_fake(real_name: str) -> str:
    """Same real name → same fake (so cross-references stay consistent)."""
    h = int(hashlib.sha256(real_name.encode()).hexdigest(), 16)
    return NAME_POOL[h % len(NAME_POOL)]

def sanitize_text(text: str) -> str:
    text = RU_FULLNAME_RE.sub(lambda m: deterministic_fake(m.group(0)), text)
    text = RU_INITIAL_RE.sub(lambda m: deterministic_fake(m.group(0)), text)
    # Cookie values
    for cookie_name in rules.get("cookie_names", []):
        text = re.sub(
            f'({cookie_name}=)[^;\\s]+',
            lambda m: m.group(1) + 'X' * 32,
            text,
        )
    # Photo URLs
    text = re.sub(r'/photos/\w+\.(jpg|png|jpeg)', '/photos/placeholder.\\1', text, flags=re.I)
    return text

def walk_har(har: dict) -> dict:
    """Recursively descend into HAR entries, applying sanitization to text bodies."""
    for entry in har.get("log", {}).get("entries", []):
        # Request body
        if "postData" in entry["request"] and "text" in entry["request"]["postData"]:
            entry["request"]["postData"]["text"] = sanitize_text(entry["request"]["postData"]["text"])
        # Response body
        content = entry.get("response", {}).get("content", {})
        if "text" in content:
            content["text"] = sanitize_text(content["text"])
        # Cookies
        for cookies_field in [entry["request"].get("cookies", []), entry["response"].get("cookies", [])]:
            for cookie in cookies_field:
                if "value" in cookie:
                    cookie["value"] = "X" * len(cookie["value"])
    return har

def main():
    if len(sys.argv) != 3:
        print("Usage: sanitize-har.py <input.har> <output.har>", file=sys.stderr)
        sys.exit(1)
    with open(sys.argv[1], encoding="utf-8") as f:
        har = json.load(f)
    sanitized = walk_har(har)
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        json.dump(sanitized, f, ensure_ascii=False, indent=2)

if __name__ == "__main__":
    main()
```

### Example 9: HAR replay MockEngine helper

```kotlin
// :core:api-avers-v4/src/commonTest/kotlin/.../fixtures/HarReplayMockEngine.kt
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.*

/**
 * Loads a sanitized HAR file from test resources and returns a MockEngine that
 * matches request URL+method against HAR entries and returns the corresponding
 * response (status + headers + body).
 */
fun harMockEngine(harResourcePath: String): MockEngine {
    val har = loadResourceAsString(harResourcePath)
    val log = Json.parseToJsonElement(har).jsonObject["log"]!!.jsonObject
    val entries = log["entries"]!!.jsonArray.map { it.jsonObject }

    return MockEngine { request ->
        val match = entries.firstOrNull { e ->
            val req = e["request"]!!.jsonObject
            req["method"]!!.jsonPrimitive.content == request.method.value
                && request.url.toString().endsWith(req["url"]!!.jsonPrimitive.content.substringAfter("//").substringAfter("/"))
        } ?: error("HAR fixture has no entry matching ${request.method} ${request.url}")

        val resp = match["response"]!!.jsonObject
        respond(
            content = ByteReadChannel(resp["content"]!!.jsonObject["text"]!!.jsonPrimitive.content),
            status = HttpStatusCode.fromValue(resp["status"]!!.jsonPrimitive.int),
            headers = headersOf(
                HttpHeaders.ContentType,
                resp["content"]!!.jsonObject["mimeType"]!!.jsonPrimitive.content,
            ),
        )
    }
}
```

## Runtime State Inventory

Phase 2 introduces new state and persistence; not a rename/refactor phase. Categories:

| Category | Items | Action Required |
|----------|-------|------------------|
| Stored data | New: `journal_default.db` SQLite file (Phase 2). Cookies table, schema v1. | Created on first launch; lifecycle managed by `DatabaseFactory.create("default")`. **Phase 3 transition** will rename to `journal_${realAccountId}.db` via `AccountDataPurger` + re-create. |
| Live service config | None — kill-switch JSON lives in git via `docs/api-config.json`, deployed by existing pages.yml. | Verify `pages.yml` re-deploys when `docs/api-config.json` changes (already true — Phase 1 wildcard). |
| OS-registered state | None new in Phase 2. | — |
| Secrets/env vars | `.env.local` (gitignored) for local dev test-account credentials (D-04). NOT in CI. | Document in `tools/README.md`; add to `.gitignore`. |
| Build artifacts / installed packages | Room KSP-generated code under `build/generated/ksp/`. | Standard build artifact; gitignored already. New gradle plugin `androidx.room` adds `:core:database/schemas/` directory (committed — schema export for migrations). |

**Verified empty:** No platform-specific runtime registrations (Tasks Scheduler, launchd, pm2, etc.) introduced in Phase 2.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Python 3.10+ | `tools/sanitize-har.py` (D-03) | ✓ | 3.12.3 (system on dev host) | None — core to fixture sanitization |
| `pyyaml` (Python) | `tools/sanitize-rules.yaml` loading | TBD on dev host | — | `pip3 install --user pyyaml` (no venv per D-19 discretion: "system Python or venv — researcher decides"). Recommend system + user install for simplicity. |
| Chrome / Chromium | HAR capture (D-02) | ✓ (assumed; standard dev tool) | — | Firefox DevTools also exports HAR — workable fallback |
| Java 17 (for Gradle) | All builds | ✓ | (already verified Phase 1) | — |
| KSP / Room compiler | `:core:database` build | ✓ via Gradle | KSP 2.2.20-2.0.4, Room 2.8.4 | — |
| GitHub Pages | Kill-switch JSON hosting (D-12) | ✓ | (already operational from Phase 1 Privacy Policy) | None — fail-open (D-13) covers GitHub Pages downtime |
| GitHub Actions macos-15 runner | iOS CI tests for new modules | ✓ (existing CI infra) | (Phase 1 baseline) | — |
| Test AVERS account credentials (×2) | Manual capture (D-01) | DEV-ONLY responsibility — not in CI | n/a | None — capture is one-time-per-build manual work |

**Missing dependencies with no fallback:** None blocking.
**Missing dependencies with fallback:** `pyyaml` may need install — install command in `tools/README.md`.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | kotlin.test (matches Kotlin 2.2.20) + Kotest 5.9.1 (assertions DSL) + Turbine 1.2.1 (Flow) + Mokkery 2.10.2 (mocks) — all already in `lintech-test` convention plugin |
| Config file | `build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt` (no separate config) |
| Quick run command | `./gradlew :core:network:allTests :core:database:allTests :core:api-avers-v4:allTests` |
| Full suite command | `./gradlew assembleDebug lint test` (Android) + `./gradlew :composeApp:iosX64Test :core:network:iosX64Test :core:database:iosX64Test :core:api-avers-v4:iosX64Test` (iOS macos-15) |

### Phase Requirements → Test Map

Phase 2 has no direct REQ-IDs; mapping is to **CONTEXT decisions and ROADMAP success criteria**:

| Source | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| Success #1 + D-01..D-03 | HAR fixtures sanitized; PII canary names absent | shell canary | `tests/sanitize-har-canary.sh` (greps `fixtures/sanitized/` for canary names) | ❌ Wave 0 |
| Success #2 + D-23 | All 6 AVERS endpoints return correct DTOs from sanitized HAR | unit (commonTest) | `./gradlew :core:api-avers-v4:allTests` (HarReplayMockEngine + parser tests, ×6 endpoints × 2 accounts = 12 cases) | ❌ Wave 0 |
| Success #3 + D-15..D-22 | RoomCookiesStorage persists Set-Cookie across HttpClient close+reopen | unit (commonTest) | `./gradlew :core:network:allTests` (test: addCookie, recreate factory, get, assert match) | ❌ Wave 0 |
| Success #3 + D-15..D-22 | DatabaseFactory.create produces functional DB on Android (Robolectric) and iOS (iosX64Test) | integration | `./gradlew :core:database:test` (Android) + `:core:database:iosX64Test` | ❌ Wave 0 |
| Success #4 + D-12..D-14 | Kill-switch fail-open returns Success on timeout/404 | unit | `./gradlew :core:api-avers-v4:allTests` (KillSwitchClient with MockEngine returning 404 / timeout) | ❌ Wave 0 |
| Success #5 + D-28 | `kanareyka_PASSWORD_DO_NOT_LEAK_42` not present in any test output (debug + release configurations) | shell canary | `tests/log-redactor-canary.sh` (runs gradle test + greps stdout) | ❌ Wave 0 |
| D-26 | 401 → CredentialProvider.get → re-login → retry; if re-login 401 → AversApiError.Unauthorized | unit (commonTest, MockEngine sequence) | `./gradlew :core:network:allTests` (test: AversAuthInterceptor stateful MockEngine returning 401, then 200 on retry) | ❌ Wave 0 |
| D-22 | AccountDataPurger.purge closes client + drops cookies + deletes DB file | unit (per-platform — file deletion needs platform impl) | `./gradlew :core:network:allTests` (Android JVM via Robolectric file system) + iosX64Test for path-deletion | ❌ Wave 0 |
| Pitfall #4 (CRC anti-bot mitigation) | AntiBotDetector returns AntiBotChallenge for HTML response | unit | `./gradlew :core:api-avers-v4:allTests` (MockEngine returns HTML CAPTCHA page) | ❌ Wave 0 |
| Pitfall #5 (logging hygiene canary) | HttpRequestRedactor masks all sensitive keys in form-encoded + JSON bodies + headers | unit | `./gradlew :core:network:allTests` (parameterized test on each sensitive key in each format) | ❌ Wave 0 |
| Pitfall #6 (per-account leak) | Two HttpClient instances from factory.forAccount("A") and ("B") have isolated CookiesStorage | unit | `./gradlew :core:network:allTests` (assert no cookie cross-talk between accountId scopes in DAO queries) | ❌ Wave 0 |

### Sampling Rate

Production behaviors → test sample rates (Nyquist principle: validate at least 2× the rate at which a behavior changes during a phase):

| Production behavior | Change rate | Test sample rate | Implication |
|---|---|---|---|
| AVERS contract format | Changes only on AVERS server upgrade (rare, per-vendor build) | Re-run on every commit (CI) — overkill but cheap | OK; if AVERS changes, ContractMismatch surfaces in fixture-replay tests |
| Cookie storage round-trip | Stable (Ktor Cookie spec) | One round-trip test per cookie scenario in commonTest | Sufficient |
| 401 retry behavior | Stable (server-driven) | One stateful MockEngine sequence per scenario | Sufficient |
| Logging redaction | Critical security; must run debug + release | Both build variants in CI canary script | Mandatory (D-28 addendum) |
| Anti-bot detection | Reactive — when AVERS triggers it | Synthetic HTML response in fixture | Sample sufficient — Phase 2 = passive observation only (D-05) |

- **Per task commit:** `./gradlew :core:network:allTests :core:database:allTests :core:api-avers-v4:allTests`
- **Per wave merge:** Full suite (Android + iOS jobs in CI) including `tests/sanitize-har-canary.sh` and `tests/log-redactor-canary.sh`
- **Phase gate:** All 6 AVERS endpoints have ≥1 fixture per account from D-01 (≥12 fixtures total); all canary scripts green; Privacy Manifest plutil-lint chain still green (regression check from Phase 1).

### Wave 0 Gaps

The following test infrastructure does NOT exist in Phase 1 baseline and must be created in early Phase 2 plans (Wave 0):

- [ ] `core/database/src/commonTest/kotlin/.../JournalDatabaseTest.kt` — Room schema v1 round-trip
- [ ] `core/database/src/androidUnitTest/kotlin/.../DatabaseFactoryAndroidTest.kt` — Android Robolectric DB file create/delete
- [ ] `core/database/src/iosTest/kotlin/.../DatabaseFactoryIosTest.kt` — iOS NSDocumentDirectory + protection attribute
- [ ] `core/network/src/commonTest/kotlin/.../HttpClientFactoryTest.kt` — factory caching, eviction, plugin chain wiring
- [ ] `core/network/src/commonTest/kotlin/.../RoomCookiesStorageTest.kt` — addCookie + get round-trip via Mokkery-mocked DAO
- [ ] `core/network/src/commonTest/kotlin/.../HttpRequestRedactorTest.kt` — sensitive-key masking parameterized test
- [ ] `core/network/src/commonTest/kotlin/.../AversAuthInterceptorTest.kt` — stateful MockEngine 401-then-200 sequence; AND interceptor-skip-on-login-endpoint (Pitfall #4)
- [ ] `core/api-avers-v4/src/commonTest/kotlin/.../fixtures/HarReplayMockEngine.kt` — fixture loader helper
- [ ] `core/api-avers-v4/src/commonTest/kotlin/.../endpoint/{Login,Grades,Schedule,Homework,Attendance,Messages}EndpointTest.kt` — one per endpoint, two account variants each (×6 = 6 test files, ~12 test cases)
- [ ] `core/api-avers-v4/src/commonTest/kotlin/.../result/AversApiErrorMappingTest.kt` — exhaustive sealed mapping
- [ ] `core/api-avers-v4/src/commonTest/kotlin/.../killswitch/KillSwitchClientTest.kt` — fail-open scenarios
- [ ] `core/api-avers-v4/src/commonTest/kotlin/.../antibot/AntiBotDetectorTest.kt` — HTML CAPTCHA page → AntiBotChallenge
- [ ] `tests/sanitize-har-canary.sh` — CI lint script grepping PII canary names in `fixtures/sanitized/`
- [ ] `tests/log-redactor-canary.sh` — CI lint script running gradle test + greppint stdout for `kanareyka_PASSWORD_DO_NOT_LEAK_42`
- [ ] `.github/workflows/ci.yml` — add new steps (sanitize-har-canary in Android job; log-redactor-canary in Android job for debug-build per D-28; new iOS X64 test invocations for the three new modules)
- [ ] `tools/README.md` — capture + sanitize workflow, dependencies install
- [ ] `fixtures/README.md` — fixture layout, regeneration procedure
- [ ] `docs/aversApiV4_23813.md` — human-readable contract documentation

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V1 Architecture | yes | Threat modeling per-asset (см. ниже); per-account scope as architectural invariant |
| V2 Authentication | partial | Phase 2 implements `CredentialProvider` interface only (Phase 3 supplies KVault impl). Phase 2 ensures auto-relogin loop cannot leak creds (Pitfall #4 mitigation) |
| V3 Session Management | yes | Cookie-session via RoomCookiesStorage; per-account isolation; expiry honored |
| V4 Access Control | partial | Per-account file separation (`journal_${accountId}.db`); cookie storage scoped to accountId |
| V5 Input Validation | yes | kotlinx.serialization with strict decoder + ApiResult.Mismatch for any unknown shape; URL allowlist (only `journal.school28-kirov.ru` + GitHub Pages kill-switch) |
| V6 Cryptography | partial | iOS NSFileProtectionComplete via Apple Crypto (system-level); Android allowBackup=false; HTTPS-only via Ktor (system trust, no pinning per D-19 PROJECT) |
| V7 Error Handling and Logging | yes (CRITICAL — D-28 addendum, Pitfall #5) | sanitizeHeader for Auth/Cookie/Set-Cookie; HttpRequestRedactor for bodies; canary tests in CI debug+release |
| V8 Data Protection | yes | Cookies at rest in encrypted (iOS) / non-backed-up (Android) SQLite; secrets-at-rest deferred to Phase 3 KVault |
| V9 Communication | yes | TLS 1.2+ via Darwin/OkHttp engines (system trust); no SSL pinning (D-19 PROJECT — Pitfall #19) |
| V10 Malicious Software | n/a | — |
| V11 Business Logic | partial | Anti-bot retry policy with exponential backoff respects server load (D-05 — passive observation only) |
| V12 Files and Resources | yes | Fixtures sanitized; raw fixtures in .gitignore; no user-supplied file processing in Phase 2 |
| V13 API and Web Service | yes | versioned `:core:api-avers-v4` module; sealed AversApiError taxonomy; ContractMismatch surfaces unexpected responses |
| V14 Configuration | yes | libs.versions.toml single source; no version literals in modules; pinned dependencies |

### Known Threat Patterns for {Ktor Mobile Client + Cookie-Session API}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Credential leak via logs (Pitfall #5) | I (Information disclosure) | sanitizeHeader + HttpRequestRedactor + LogLevel.NONE in release + canary CI test (D-28) |
| Cross-account data leak via shared HttpClient (Pitfall #6) | I + R (Repudiation) | factory.forAccount(id) + per-account RoomCookiesStorage scoped by accountId + per-account DB files |
| 401 retry infinite loop on bad password (Pitfall #4 above) | D (Denial of service) | Skip interceptor for login-endpoint; bail after one re-login attempt |
| MITM via school proxy with self-signed cert (Pitfall #19) | T (Tampering) | System trust store (no pinning); user-facing error message on SSL failure |
| Anti-bot CAPTCHA → user stuck (Pitfall #11) | D | Detect HTML response, emit AntiBotChallenge, Phase 3 WebView fallback |
| Server contract drift bricking app (Pitfall #2) | D | Versioned API module + ContractMismatch + remote kill-switch + canary endpoint |
| Cookie expiry race (RoomCookiesStorage `get` returning expired cookies) | T | Filter on `expiresAtEpochMillis > now` in DAO query |
| HAR fixture leaks real ФИО to git history | I | tools/sanitize-har.py + CI lint canary `tests/sanitize-har-canary.sh` |
| `.env.local` accidentally committed | I | `.gitignore` entry; `tools/README.md` warns; pre-commit hook check (deferred — out of scope Phase 2) |
| GitHub Pages kill-switch DoS (severity=block + GitHub Pages compromised) | T + D | fail-open semantics (D-13) means even if remote endpoint is malicious, worst case is severity=block — researcher should add: planner consider "block" only honored if `latestSupportedAversBuild` differs from current build (i.e. "block" requires a reason). Documented as discretionary refinement. |

### Threat Model Template (per-PLAN)

Planner should include this STRIDE-lite skeleton for any PLAN that touches network/database/credentials:

```
## Threat Model

| Asset | STRIDE | Mitigation in this PLAN |
|-------|--------|-------------------------|
| AVERS user credentials | Information disclosure | CredentialProvider interface only; no concrete cred storage in Phase 2 |
| Session cookies | I, Tampering | RoomCookiesStorage in encrypted-at-rest DB; allowBackup=false; per-account scope |
| HTTP request bodies (login form data) | I | HttpRequestRedactor masks password/pwd/pass/token in bodies; sanitizeHeader for Authorization/Cookie |
| HTTP request log output | I | LogLevel.NONE in release; debug-build redactor mandatory; CI canary test |
| HAR fixtures | I (PII) | tools/sanitize-har.py + CI lint; raw/ in .gitignore |
| Database file | I + Tampering | iOS NSFileProtectionComplete; Android allowBackup=false; per-account file separation |
| Kill-switch JSON endpoint | Tampering, DoS | fail-open semantics; only-block-if-build-differs refinement |
```

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | AVERS uses cookie-based session (not bearer tokens, not OAuth) — based on STACK.md mention "session-cookie auth" + ExtJS conventions | Pattern 1, Pattern 2 | If JWT/OAuth, RoomCookiesStorage + auto-relogin design changes; pivot to Auth `bearer{}` plugin. Discovered at Phase 2 first capture — refactor cost ~1 plan. [ASSUMED — verifies on first HAR capture] |
| A2 | AVERS responses are JSON (possibly wrapped in ExtJS `{success, data}` envelope) — based on PITFALLS.md #2 | Code Example #3, AversApi DTOs | If pure HTML/SOAP, all DTO design changes. Same first-capture verification. [ASSUMED] |
| A3 | Mobile UA divergence DOES exist on AVERS — based on common ExtJS-on-Java behavior | Pattern 1 (UA mimic), Pitfall #1 | If mobile and desktop return same payload, Phase 4 first-Android-run is uneventful — no harm. [ASSUMED] |
| A4 | AVERS does NOT enforce TLS pinning / client certificate auth | D-19 PROJECT, ASVS V9 | Verified out-of-band: school journals don't require client certs. [ASSUMED based on STATE.md] |
| A5 | Room 2.8.4 KMP is current stable as of 2026-04 (verified developer.android.com page citing room=2.8.4 sqlite=2.6.2) | Standard Stack | If Room 3.0.x stable arrives during Phase 2, planner may upgrade — minor risk. [VERIFIED via WebFetch] |
| A6 | Ktor 3.4.3 is current stable as of 2026-04 — STACK.md cites Kotlin Blog 2026-01 release of 3.4.0; STACK.md TL;DR says 3.4.3 | Standard Stack | If 3.5.x lands, would consider but no need to chase. [VERIFIED: STACK.md] |
| A7 | kotlinx-datetime 0.8.0-rc01 is acceptable for production use (rc-state) | Standard Stack | rc may have bugs; can fall back to 0.7.x stable if encountered. STACK.md notes "≥0.7.x stable" alternative. [CITED: STACK.md] |
| A8 | GitHub Pages serves `docs/api-config.json` with reasonable caching (Fastly TTL ≤5min) — D-12 cited | KillSwitchClient | If cache TTL is hours, kill-switch latency unacceptable for emergency block. Researcher cited D-12 — claim from CONTEXT, not independently verified. [CITED: D-12] |
| A9 | KSP 2.2.20-2.0.4 is compatible with Room 2.8.4 — based on KSP-must-match-Kotlin-major.minor rule from STACK.md | Standard Stack, Pitfall #3 | If Room 2.8.4 requires newer KSP patch, simple version bump in catalog. [ASSUMED — risk low; verified on first build] |
| A10 | The "AntiBotChallenge" sealed-error variant should carry an `rawHtml` payload despite D-11 declaring it `object` (D-06 Phase 3 needs payload) | D-06 vs D-11 reconciliation, Code Example #6 | If user wants strict D-11 literal, planner picks alternate placement (e.g. extra field on Mismatch); minor refactor. [ASSUMED — flagged in Code Example #6] |

**These assumptions are flagged for the planner to either confirm or refine via additional discuss-phase question. None block planning — fall-back paths exist for every one.**

## Open Questions

1. **Exact AVERS endpoint URLs and parameter names** — completely unknown until Phase 2 HAR capture (Plan task 1). Researcher cannot pre-write `httpClient.get("/api/grades")`-style code; final endpoint paths emerge from HAR replay.
   - What we know: hostname `journal.school28-kirov.ru`, build 23813
   - What's unclear: every URL path, every parameter name
   - Recommendation: Plan **Task 1** = HAR capture + `aversApiV4_23813.md` documentation. **Task N** (after capture) = endpoint code matching observed shapes. Don't try to write endpoint code before capture.

2. **Whether AVERS issues a CSRF token (form-field or header)** — common in ExtJS, but speculative.
   - Recommendation: First HAR capture answers this. If yes, add CSRF interceptor to plugin chain or hand-stitch in `:core:api-avers-v4`.

3. **Whether AVERS uses POST or GET for login** — passwords in GET would be a major security signal but not unheard of in legacy.
   - Recommendation: Capture answers this; redactor handles both query and body.

4. **Should Koin DI be wired in Phase 2 or deferred?** — STACK.md locks Koin 4.x but timing not zafiksirovan (CONTEXT discretion item).
   - Recommendation: **DEFER to Phase 3.** Phase 2 has only one factory + one dao. Manual constructor wiring (in tests + a `:composeApp` `Phase2WiringSample.kt`-stub) is sufficient. Koin scope-management value ramps with multi-account in Phase 3-5. Adding Koin in Phase 2 = build-time tax for unclear value.

5. **NetworkMonitor expect/actual — needed in Phase 2?** — D-19 says deferred to Phase 4.
   - Recommendation: **DEFER.** Phase 2 has no offline-first observation use-case. Ktor exception → AversApiError.Network already covers detection at request-time.

6. **D-11 vs D-06 AntiBotChallenge payload shape** (raised as A10 above)
   - Recommendation: planner adjusts D-11 from `object AntiBotChallenge` → `data class AntiBotChallenge(val rawHtml: String)` (sealed superset; no API breakage). Document in plan as clarification.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `AcceptAllCookiesStorage` (in-memory) | Custom `CookiesStorage` impl on persistent backend | Always — Ktor's default is dev-only convenience | We must implement this |
| `CocoaPods` for iOS interop | SwiftPM XCFramework | Phase 1 (D-05 in 01-CONTEXT) | Already done in Phase 1 |
| `SharedPreferences`/`NSUserDefaults` for cookies | Encrypted SQLite (Room with NSFileProtectionComplete + allowBackup=false) | Today | Phase 2 baseline |
| Ktor `CIO` engine on mobile | Darwin (iOS) + OkHttp (Android) | Always — STACK.md What NOT to Use | Phase 2 baseline |
| MockK for KMP iOS | Mokkery | Already adopted Phase 1 | Continue |
| Single `:shared` module | feature-by-layer multi-module | ARCHITECTURE.md rec, Phase 1 baseline | Continue |
| Hand-rolled retry loops | Ktor `HttpRequestRetry` plugin | Plugin matured Ktor 3.x | Use plugin |
| Jetpack Compose Navigation 2.x | Navigation 3 (deferred to Phase 4) | CMP 1.10 alpha-on-iOS | N/A in Phase 2 |
| Bearer-token auth | Cookie-session auth (this project) | (project-specific) | Custom HttpSend interceptor for D-26; not Auth `bearer{}` |

**Deprecated/outdated:**
- `Ktor 2.x` — superseded by 3.x; do not use
- `androidx.security-crypto 1.1.0-alpha07` — frozen, use KVault (Phase 3)
- `kotlinx-datetime 0.6.2` (current Phase 1 baseline) — bump to 0.8.0-rc01 (or 0.7.x stable) per STACK.md
- `kotlinx.serialization 1.7.3` (current Phase 1 baseline) — bump to 1.9.0 per STACK.md compatibility with Ktor 3.4

## Sources

### Primary (HIGH confidence)

- [Set up Room for KMP — Android Developers](https://developer.android.com/kotlin/multiplatform/room) — KMP setup, KSP per-target, expect/actual factory pattern, iOS DocumentDirectory path. **Verified 2026-04-28 via WebFetch:** sample shows `room = "2.8.4"`, `sqlite = "2.6.2"`.
- [Ktor Cookies docs](https://ktor.io/docs/client-cookies.html) — `CookiesStorage` interface, persistent storage pattern.
- [Ktor Client Engines](https://ktor.io/docs/client-engines.html) — Darwin / OkHttp engine config (locked in STACK.md).
- [Ktor Retry — HttpRequestRetry](https://ktor.io/docs/client-request-retry.html) — `exponentialDelay`, `retryOnExceptionIf`, `retryIf` API. **Verified via WebSearch 2026-04-28.**
- [Ktor MockEngine docs](https://ktor.io/docs/client-testing.html) — `MockEngine { request -> respond(...) }` pattern. **Verified via WebFetch.**
- [Ktor Auth / Bearer / HttpSend docs](https://ktor.io/docs/client-auth.html) — automatic 401-refresh, custom HttpSend interceptor pattern. **Verified via WebSearch 2026-04-28.**
- [.planning/research/STACK.md] — locked stack (Ktor 3.4.3, Room 2.8.x, kotlinx.serialization 1.9.0, kotlinx-datetime 0.8.x, Mokkery, Kermit, Coil 3, KSP-Kotlin alignment).
- [.planning/research/ARCHITECTURE.md] — Pattern 3 per-account HttpClient, Pattern 1 single-source-of-truth, Pattern 6 expect/actual platformModule, anti-patterns 6 and 7.
- [.planning/research/PITFALLS.md] — #2 API fragility, #5 logging hygiene, #6 cross-account leak, #11 anti-bot, #19 SSL.
- [.planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md] — D-07 dependency rules, D-08 package root, D-31 expect/actual UrlOpener template, BLOCKER 1 fix (no compose deps in non-UI modules).
- [CLAUDE.md] — package root convention, expect/actual rules, AppTest split, lintech convention plugins, build infrastructure pinned versions.

### Secondary (MEDIUM confidence)

- [Handling Token Expiration in Ktor — droidcon (2025-03)](https://www.droidcon.com/2025/03/06/handling-token-expiration-in-ktor-automatic-token-refresh-for-api-calls/) — HttpSend interceptor pattern adapted for cookie-session.
- [Ktor's CIO engine in 2026 — ITNEXT](https://itnext.io/ktors-cio-engine-in-2026-can-it-finally-replace-okhttp-and-darwin-8f7b7e7e1553) — confirms Darwin/OkHttp still default.
- [iOS Database builder for Room KMP/CMP — GitHub Gist](https://gist.github.com/binayshaw7777/1eadc9afaaaa3757c2ef44e0d8d34ca2) — KMP iOS Room builder example.
- [Room Database for Kotlin Multiplatform: 7 Powerful Async Facts](https://www.progressiverobot.com/2026/04/26/room-database-kmp/) — recent (2026-04) overview confirming Room 2.8 KMP stability.
- [How to use iOS Data Protection (updated for iOS 17) — Nutrient](https://www.nutrient.io/blog/how-to-use-ios-data-protection/) — directory-attribute inheritance pattern for NSFileProtectionComplete on iOS 17+.
- [Securing Data in iOS — Chariot Solutions](https://chariotsolutions.com/blog/post/securing-data-in-ios-2/) — additional NSFileProtectionComplete reference.
- [HAR 1.2 spec (softwareishard.com)](http://www.softwareishard.com/blog/har-12-spec/) — HAR fields cited in CONTEXT canonical refs.
- [Edgio/har-tools (GitHub)](https://github.com/Edgio/har-tools) — patterns for HAR PII removal (referenced for sanitizer regex inspiration).
- [solentlabs/har-capture (PyPI)](https://pypi.org/project/har-capture/) — HAR sanitization Python tooling reference.

### Tertiary (LOW confidence — flag for validation)

- Specific UA string for Mobile Safari mimic (Pattern 1 example) — chosen as reasonable starting baseline; researcher acknowledges actual UA-string sensitivity tested only after first AVERS capture.
- HttpRequestRedactor regex patterns (Code Example 4) — drafted; needs canary test verification across realistic AVERS body shapes (form-encoded, JSON, multipart) before locking.
- Kill-switch CDN cache TTL (D-12 cites 5min Fastly) — not independently verified by researcher; CITED from D-12 only.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Ktor 3.4.3 / Room 2.8.4 / KSP 2.2.20-2.0.4 / kotlinx.serialization 1.9.0 verified against current docs and STACK.md
- Architecture: HIGH — three-module decomposition (`:core:database`, `:core:network`, `:core:api-avers-v4`) follows D-07 dependency rules + ARCHITECTURE.md feature-by-layer
- Pitfalls: HIGH — directly inherited from PITFALLS.md #2/#5/#6/#11/#19 + Phase 2-specific additions (mobile UA divergence, redactor over-match, KSP per-target, 401 loop, datetime 0.8 break, capture hygiene, cookie-domain matching, no-compose-deps)
- AVERS contract specifics: LOW — entirely unknown until Phase 2 HAR capture; ten assumptions logged
- Validation Architecture: HIGH — concrete file paths, command lines, Wave 0 gap list
- Security Domain: HIGH — ASVS table per category + STRIDE-lite per asset, threat-model template ready for plan adoption

**Research date:** 2026-04-28
**Valid until:** 2026-05-28 (30 days, fast-moving Ktor/Room ecosystem; re-verify versions before Phase 2 plan-execution if delay > 30 days)

## RESEARCH COMPLETE
