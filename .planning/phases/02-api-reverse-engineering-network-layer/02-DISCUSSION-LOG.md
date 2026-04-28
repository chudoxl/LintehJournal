# Phase 2: API Reverse-Engineering & Network Layer - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in 02-CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-28
**Phase:** 2-api-reverse-engineering-network-layer
**Areas discussed:** Механика реверса, Форма API-модуля, Куки и Room, Скоп endpoints

---

## Механика реверса (Area 1)

### Q1.1 — Test-аккаунт

| Option | Description | Selected |
|--------|-------------|----------|
| Личный аккаунт ребёнка | Быстрый старт, но HAR содержат реальные ФИО — обязательно sanitization | |
| Два+ аккаунта (личных) | Cross-account proof валидирует cookie-isolation на уровне contract раньше Phase 5 | ✓ |
| Отдельный test-аккаунт от школы | Formal blessing, но требует диалога; v2 deferred | |

**User's choice:** Два+ аккаунта (личных)
**Notes:** В семье двое детей в школе №28 — даёт ранний multi-account validator. Cross-account HAR-fixtures (D-22 в CONTEXT) доказывают «разные cookies → разные данные».

---

### Q1.2 — Инструмент захвата

| Option | Description | Selected |
|--------|-------------|----------|
| mitmproxy + Android-устройство (Recommended) | Mobile-репрезентативный capture, ROADMAP-aligned | |
| Chrome DevTools HAR-export на desktop | Быстрый старт, но desktop UA divergence risk | ✓ |
| Оба (mitmproxy + DevTools cross-check) | Maximum coverage, дольше | |
| iOS Safari + remote inspect c Mac | Не подходит — Linux Mint dev-host | |

**User's choice:** Chrome DevTools HAR-export на desktop
**Notes:** Конфликт с ROADMAP success #1 («mitmproxy») — разрешён следующим вопросом.

---

### Q1.2b — Резолюция конфликта c ROADMAP

| Option | Description | Selected |
|--------|-------------|----------|
| Chrome DevTools старт, mitmproxy позже (Recommended) | Hybrid; обновить ROADMAP на «mitmproxy + DevTools cross-check» | |
| Только Chrome DevTools, ROADMAP смягчить | Phase 2 plan содержит ROADMAP edit | ✓ |
| Остаёмся на mitmproxy по ROADMAP | Дольше setup, no surprises | |

**User's choice:** Только Chrome DevTools, ROADMAP смягчить
**Notes:** Принят risk mobile UA divergence — митигация через параметризованный UA mimic, валидация при первом Android run в Phase 4.

---

### Q1.3 — HAR sanitization

| Option | Description | Selected |
|--------|-------------|----------|
| Скрипт `tools/sanitize-har.py` (Recommended) | Детерминированная замена ФИО на fakes + CI lint canary | ✓ |
| Hand-edit перед commit | Ненадёжно, риск пропуска | |
| HAR не в git — внешний трип | Теряем regression-protect, нарушает ROADMAP success #1 | |
| Synthetic fixtures (реконструируем) | Без production-edge cases | |

**User's choice:** Скрипт `tools/sanitize-har.py`

---

### Q1.4 — Raw HAR + credentials storage

| Option | Description | Selected |
|--------|-------------|----------|
| `fixtures/raw/` в .gitignore + `.env.local` (Recommended) | Стандарт для local dev, CI gates через sanitized only | ✓ |
| Encrypted git-crypt в приватном репо | Overkill для solo-проекта | |
| 1Password / Bitwarden + локальные fixtures | Manual sync overhead | |

**User's choice:** `fixtures/raw/` в .gitignore + `.env.local`

---

### Q1.5 — Anti-bot research depth (follow-up)

| Option | Description | Selected |
|--------|-------------|----------|
| Только документируем видимое | Уважительно к серверу школы; thresholds — generic defaults | ✓ |
| Active probing (Recommended) | Empirical thresholds, но риск IP/account block | |
| Active probing отложить в Phase 5 | Hybrid | |

**User's choice:** Только документируем видимое

---

### Q1.6 — WebView CAPTCHA fallback

| Option | Description | Selected |
|--------|-------------|----------|
| Отложить в Phase 3 или позже (Recommended) | Phase 2 ловит как `AversApiError.AntiBotChallenge`, Phase 3 UI handles | ✓ |
| Реализуем в Phase 2 за feature flag | UI-код в network-модуле нарушает D-07 dependency rules | |
| Выкинуть из v1, показать явную ошибку | UX хуже, в семейном кругу может быть OK | |

**User's choice:** Отложить в Phase 3 или позже

---

## Форма API-модуля (Area 2)

### Q2.1 — Размещение versioned API

| Option | Description | Selected |
|--------|-------------|----------|
| Отдельный модуль `:core:api-avers-v4` (Recommended) | Чистый boundary, parallel-versioning support | ✓ |
| Package внутри `:core:network` | Быстрее старт, хуже изоляция | |
| Новый модуль, расширить в Phase 5 | Combo с узким endpoint scope | |

**User's choice:** Отдельный модуль `:core:api-avers-v4`

---

### Q2.2 — API surface

| Option | Description | Selected |
|--------|-------------|----------|
| Типизированные DTO + suspend functions (Recommended) | Type-safe, mapper DTO→domain в Phase 4 | ✓ |
| Сырые `JsonElement` + suspend functions | Гибко для reverse-engineering, теряем versioned isolation | |
| DTO + внутренний фолбэк на JsonElement | Hybrid | |

**User's choice:** Типизированные DTO + suspend functions

---

### Q2.3 — Parsing strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Strict + sealed `ApiResult` (Recommended) | ignoreUnknownKeys + explicit requireNotNull + Mismatch case | ✓ |
| Strict only — throw при расхождении | Падает на любом новом поле | |
| Lenient везде (без mismatch detection) | Противоречит ROADMAP success #4 | |

**User's choice:** Strict + sealed `ApiResult`

---

### Q2.4 — Kill-switch hosting

| Option | Description | Selected |
|--------|-------------|----------|
| GitHub Pages (тот же docs/) (Recommended) | Бесплатно, GitHub-native, auto-deploy | ✓ |
| raw.githubusercontent.com в main | Меньше overhead, но менее «professional» | |
| Cloudflare R2 / собственный CDN | Overkill для семейного круга | |
| Не нужен — canary вылавливает локально | Не покрывает ROADMAP success #4 | |

**User's choice:** GitHub Pages (тот же docs/)

---

### Q2.5 — Kill-switch failure mode

| Option | Description | Selected |
|--------|-------------|----------|
| Fail-open (Recommended) | Никогда не блокируем offline-пользователя | ✓ |
| Fail-open + cached state | Robust но сложнее | |
| Fail-closed | Ломает offline-first инвариант | |

**User's choice:** Fail-open

---

### Q2.6 — Kill-switch cadence

| Option | Description | Selected |
|--------|-------------|----------|
| При каждом app-launch (Recommended) | Простой baseline, cached на session | ✓ |
| При launch + 1р/сутки в background | Phase 6 BGAppRefreshTask, ещё не реализован | |
| На каждый API call | Overkill, бьёт rate-limits | |

**User's choice:** При каждом app-launch

---

### Q2.7 — Plugin chain location

| Option | Description | Selected |
|--------|-------------|----------|
| `:core:network/HttpClientFactory` — всё общее (Recommended) | Чёткое разделение «общий HTTP» / «AVERS-особенности» | ✓ |
| Всё в `:core:api-avers-v4` | Плохо для будущих API | |
| Plugins в :core:network, retry/UA в :core:api-avers-v4 | Самый pedantic split | |

**User's choice:** `:core:network/HttpClientFactory` — всё общее

---

### Q2.8 — Error hierarchy

| Option | Description | Selected |
|--------|-------------|----------|
| Плоская sealed `AversApiError` (Recommended) | Sealed → exhaustive when, Swift-friendly | ✓ |
| `Result<T, Throwable>` | Хуже Swift interop | |
| `Either<AversApiError, T>` (Arrow) | Overengineering, доп зависимость | |

**User's choice:** Плоская sealed `AversApiError`

---

## Куки и Room (Area 3)

### Q3.1 — Bootstrap timing

| Option | Description | Selected |
|--------|-------------|----------|
| Bootstrap `:core:database` в Phase 2 (Recommended) | Schema v1 cookies-only, ROADMAP success #3 fulfilled | ✓ |
| File-based в Phase 2, миграция в Room в Phase 3 | Работа пишется дважды, требует ROADMAP edit | |
| Ephemeral in-memory в Phase 2 | Требует ROADMAP edit | |

**User's choice:** Bootstrap `:core:database` в Phase 2

---

### Q3.2 — DB filename

| Option | Description | Selected |
|--------|-------------|----------|
| `journal_default.db` — совместимо с Phase 5 (Recommended) | Pattern из ARCHITECTURE.md, никаких special-cases | ✓ |
| `journal.db` — без accountId | Нарушает per-account scope invariant | |

**User's choice:** `journal_default.db`

---

### Q3.3 — Schema v1 scope

| Option | Description | Selected |
|--------|-------------|----------|
| Только cookies (Recommended) | Минимальный surface, ROADMAP-aligned | ✓ |
| Cookies + accounts (захлёстываем Phase 3) | Phase 3 может захотеть другую схему | |
| Cookies + grades (захлёстываем Phase 4) | Overkill для Phase 2 goals | |

**User's choice:** Только cookies

---

### Q3.4 — Ktor CookiesStorage integration

| Option | Description | Selected |
|--------|-------------|----------|
| Custom `RoomCookiesStorage : CookiesStorage` (Recommended) | Стандартное Ktor решение | ✓ |
| In-memory cache + Room write-through | Сложнее, race conditions | |
| Готовая KMP-библиотека | Нет зрелых на 2026 | |

**User's choice:** Custom `RoomCookiesStorage : CookiesStorage`

---

### Q3.5 — Room init pattern

| Option | Description | Selected |
|--------|-------------|----------|
| expect/actual `DatabaseFactory` в :core:platform (Recommended) | Следует D-31 паттерну | ✓ |
| Koin module с platform module регистрацией | Раньше чем нужно | |
| Оба (expect/actual + Koin wiring) | Standard combo | |

**User's choice:** expect/actual `DatabaseFactory` в :core:platform
**Notes:** Researcher уточнит точное место expect/actual (`:core:platform` vs `:core:database/iosMain`) — KMP Room standard может потребовать второе.

---

### Q3.6 — iOS file protection

| Option | Description | Selected |
|--------|-------------|----------|
| `completeFileProtection` (Recommended) | Максимальная защита, но Phase 6 потребует переключения | ✓ |
| `completeUntilFirstUserAuthentication` | Сразу совместимо с Phase 6 background | |
| Default (none) | Нарушает PROJECT.md security инварианты | |

**User's choice:** `completeFileProtection`
**Notes:** ⚠ Mandatory reminder: Phase 6 plan должен переключить на `completeUntilFirstUserAuthentication` для BGAppRefreshTask.

---

### Q3.7 — Android allowBackup

| Option | Description | Selected |
|--------|-------------|----------|
| `allowBackup="false"` в Phase 2 (Recommended) | Cookies/credentials никуда не утекут | ✓ |
| `allowBackup="true"` + dataExtractionRules | Overkill для Phase 2 prefs | |

**User's choice:** `allowBackup="false"`

---

### Q3.8 — Wipe API readiness

| Option | Description | Selected |
|--------|-------------|----------|
| Да — `AccountDataPurger.purge(accountId)` (Recommended) | Готов к Phase 3 logout, per-account invariant раньше | ✓ |
| Нет — Phase 3 разберётся | Откладывает invariant | |

**User's choice:** Да — `AccountDataPurger.purge(accountId)`

---

## Скоп endpoints (Area 4)

### Q4.1 — Endpoint scope

| Option | Description | Selected |
|--------|-------------|----------|
| Только login + grades (вертикальный срез) (Recommended) | Минимальный slice, остальные в Phase 5 | |
| Все 6 endpoints (login + 5 read) | Full ROADMAP-aligned contract | ✓ |
| Login + 3 endpoints | Срединный вариант | |

**User's choice:** Все 6 endpoints (login + 5 read)
**Notes:** Phase 2 будет крупнее, но типизированные DTO снижают риск «не подойдут UI».

---

### Q4.2 — Endpoint variations depth

| Option | Description | Selected |
|--------|-------------|----------|
| Базовый contract каждого (Recommended) | Один сценарий per endpoint, ~6 fixtures | |
| Base + все ROADMAP variants | Итоговые/история/замены/файлы/веса, ~12-15 fixtures | |
| Base + cross-account proof (2 user fixtures) | 2 sets per endpoint, ~12 fixtures, multi-account validator | ✓ |

**User's choice:** Base + cross-account proof (2 user fixtures)

---

### Q4.3 — CI testing strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Только MockEngine + sanitized HAR (Recommended) | CI никогда не стучится в АВЕРС, детерминировано | ✓ |
| MockEngine + опциональный живой smoke | Manual-trigger workflow с GitHub Secrets | |
| Живой smoke на каждый PR | Отклонить — anti-bot, secrets leakage | |

**User's choice:** Только MockEngine + sanitized HAR

---

### Q4.4 — Cookie expiry / 401 handling

| Option | Description | Selected |
|--------|-------------|----------|
| Auto re-login в :core:network (Recommended) | Ktor Auth plugin + CredentialProvider interface | |
| Пробрасывать `AversApiError.Unauthorized` наверх | Чистый boundary, UX хуже | |
| Auto re-login + проброс при fail | Combo: best UX | ✓ |

**User's choice:** Auto re-login + проброс при fail

---

### Q4.5 — Pagination

| Option | Description | Selected |
|--------|-------------|----------|
| Документируем, не реализуем (Recommended) | Phase 4 решит при необходимости | ✓ |
| Реализуем paging-aware DTO API | Overengineering для неизвестного size | |
| Предполагаем «всё за раз» | Risk неполных данных | |

**User's choice:** Документируем, не реализуем

---

### Q4.6 — Debug logging level

| Option | Description | Selected |
|--------|-------------|----------|
| Kermit `Info` + Ktor `HEADERS` (Recommended) | Безопасно: bodies не дампятся | |
| Kermit `Verbose` + Ktor `ALL` | Maximum visibility, требует aggressive redactor | ✓ |
| Kermit only, Ktor `NONE` | Безопасно но слепо | |

**User's choice:** Kermit `Verbose` + Ktor `ALL`
**Notes:** ⚠ Mandatory: redactor + canary-test обязаны работать и в debug, не только release. CI canary grep'ает Logcat/Console.app debug-build output на `kanareyka_PASSWORD_DO_NOT_LEAK_42`.

---

## Claude's Discretion

См. раздел "Claude's Discretion" в 02-CONTEXT.md → researcher и planner свободны в:
- Точная структура `aversApiV4_23813.md` markdown
- Точная схема `tools/sanitize-har.py`
- Конкретный layout `:core:api-avers-v4` (per-endpoint files vs grouped)
- Конкретный wiring DatabaseFactory (точное место expect/actual)
- HttpClient lifecycle (singleton vs factory + cache)
- Точный JSON-schema kill-switch (имена полей)
- Koin DI introduction timing
- Timeout values, retry policy parameters, exponential backoff
- Versions: точный bump kotlinx.serialization 1.9.0, kotlinxDatetime 0.8.x, Room 2.8.x KMP, KSP alignment к Kotlin 2.2.20

## Deferred Ideas

- ROADMAP success #1 edit: «mitmproxy» → «Chrome DevTools» (planner Phase 2 включит как явную задачу)
- Phase 6 reminder: переключить iOS `completeFileProtection` → `completeUntilFirstUserAuthentication` для BGAppRefreshTask
- Active anti-bot probing — Phase 5 hardening / v2
- WebView CAPTCHA fallback — Phase 3
- Pagination clientside — Phase 4 если нужно
- NetworkMonitor expect/actual — Phase 4 (offline-first), если researcher не обнаружит ранней необходимости
- Performance budget bench — Phase 6 / v2
- Live smoke в CI — отклонено навсегда

## Empty Multi-select (final close)

Question: «Какие дополнительные грей-зоны обсудить?»
- Network monitor / offline detection
- Accept-Language / localization headers
- Performance budget
- CI build-time budget

**User's choice:** (Empty / no selection)
**Follow-up via plain text:** User chose «6 — Закрываем, пиши CONTEXT.md»
**Notes:** Дополнительных грей-зон нет. Network monitor, headers details, performance budget, CI build-time — отложены к researcher (research-time discretion) или к Phase 4+ когда станут актуальными.
