---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: "Phase 02 Wave 5 complete (parallel worktree execution). Plan 02-07 (KillSwitchClient + docs/api-config.json with D-13 fail-open + D-14 cold-start cache + T-02-38 hardening) and Plan 02-09 (CI iOS jobs for :core:database/:core:network/:core:api-avers-v4 on macos-15 + canary gates wired into Android job + EndpointsContractTest promoted to commonTest + iOS Native HAR loading via NSFileManager+getenv) both merged. Build green: ci.yml valid YAML, api-config.json valid JSON, both canary scripts pass. Plan 02-07 docs commit was orchestrator-finalized after agent hit rate-limit just before the wrap-up commit (3 task commits + 1 orchestrator docs commit). Next: Wave 6 plan 02-08 — docs/aversApiV4_23813.md + tools/manual-smoke.sh (final phase deliverable)."
last_updated: "2026-04-29T13:30:00.000Z"
last_activity: 2026-04-29 -- Phase 02 Wave 5 complete (plans 02-07, 02-09 merged from worktrees)
progress:
  total_phases: 6
  completed_phases: 1
  total_plans: 15
  completed_plans: 14
  percent: 93
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-27)

**Core value:** Удобный, быстрый и оффлайн‑доступный мобильный доступ к оценкам, расписанию и домашним заданиям с поддержкой нескольких учеников в одном приложении
**Current focus:** Phase 02 — api-reverse-engineering-network-layer (HIGHEST uncertainty)

## Current Position

Phase: 02 (api-reverse-engineering-network-layer) — EXECUTING (Wave 5 done; 02-08 pending)
Plans: 8 of 9 (02-01 ✓, 02-02 ✓, 02-03 ✓, 02-04 ✓, 02-05 ✓, 02-06 ✓, 02-07 ✓, 02-09 ✓; 02-08 pending)
Status: Ready for Wave 6 (Plan 02-08 docs/aversApiV4_23813.md + tools/manual-smoke.sh — final plan)
Last activity: 2026-04-29 -- Phase 02 Wave 5 complete (plans 02-07, 02-09 merged from worktrees)

Progress: [████████░░] 73% (1/6 phases shipped, Phase 02 8/9 plans done)

## Performance Metrics

**Velocity:**

- Total plans completed: 6
- Average duration: ~30 min (executor agents)
- Total execution time: ~4.5 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1 | 1 | 15 min | 15 min |
| 2 | 5 | ~165 min | ~33 min |

**Recent Trend:**

- 02-01-skeleton (15 min) → 02-02-har-capture (45 min programmatic) → Wave 2 parallel: 02-03-room (50 min) + 02-04-network (21 min) → Wave 3: 02-05-cookies (13 min) → Wave 4: 02-06-api-contract (38 min, largest plan)
- Trend: 02-06 fastest large-plan execution — clear contract from 02-02-SUMMARY API spec + 02-04/02-05 wiring made HarReplayMockEngine straightforward; deviation count (7) reflects integration discovery, not blockers

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- v1 = личное/семейное использование через TestFlight + Google Play Internal track; public submission и formal legal compliance (РКН, согласие школы) отложены в v2
- Биометрическая разблокировка отложена в v2 — KeyChain/Keystore + системный пароль устройства уже обеспечивают защиту кредов
- Compose Multiplatform 1.10.3 + Kotlin 2.2.20 как осознанный технический выбор (см. STACK.md)
- Per-account scope (отдельный файл БД на аккаунт) — архитектурный инвариант с самого начала (см. ARCHITECTURE.md → Pattern 3+4)
- Gradle wrapper baseline = 8.13 (поднят с 8.10 в Plan 01-01 — kotlin-dsl Kotlin 2.0.21 совместим с Compose Gradle Plugin 1.10.3 binary metadata)
- Root build.gradle.kts применяет ВСЕ used plugins через alias(...) apply false — Now in Android pattern; convention plugins вызывают pluginManager.apply(...) без classpath гимнастики
- composeApp применяет raw plugins (alias) вместо lintech-kmp — это application module, а lintech-kmp применяет com.android.library
- AVERS auth = client-set cookies (`ys-user`/`ys-password`/`ys-userId`) via Ext.state.CookieProvider — server never issues Set-Cookie; cookie values use JS escape() with `%uXXXX` for codepoints ≥256 (mandatory for Cyrillic logins)
- AVERS responses are NOT strict JSON (contain `new Date(YYYY,MM,DD,...)` literals) — Plan 06 client must pre-process response text or implement tolerant parser
- Plan 02-02 retroactively flipped `autonomous: true` — programmatic capture replaces manual Chrome DevTools (12 sanitized fixtures committed; auth + 6 endpoint contracts derived from `site/client/*.js`)
- Ktor pinned at 3.3.3 (downgraded from 3.4.3 in Plan 02-04 merge) — 3.4.x requires Kotlin 2.3.0 which breaks Compose Multiplatform 1.10.3 binary metadata. Plans 02-05/02-06 must target 3.3.3.
- :core:platform Android `applicationContextHolder` visibility lifted private→internal so `ApplicationContextProvider.kt` (also in androidMain) can read it — preserves BL-02 mutability discipline at module level (Plan 02-03 D-19)
- AVERS DTOs use POSITIONAL KSerializer (responses are arrays-of-arrays, not objects). All DTO files in :core:api-avers-v4 carry custom serializers; do not switch to @Serializable data class with named fields (would break parsing).
- AversApiError.AntiBotChallenge is a `data class(rawHtml: String)`, not an object — Phase 3 WebView fallback receives the captcha page payload (D-06 reconciliation in Plan 02-06)
- AVERS endpoint URL constants live in `AversEndpoints` object in :core:api-avers-v4 — single source of truth, captured from Plan 02-02 HAR fixtures: `/login`, `/auth/logout`, `/act/GET_STUDENT_JOURNAL_DATA`, `/act/GET_TIMETABLE`, `/act/GET_STUDENT_DAIRY`, `/act/GET_ATT_JOURNAL_DATA`, `/act/get_sms`
- `EndpointsContractTest` now lives in `commonTest/` (Plan 02-09 promoted from androidUnitTest); iOS Native HAR loader uses `getenv("FIXTURES_DIR")` set by Gradle via `tasks.withType<KotlinNativeTest>().configureEach`
- Kill-switch infrastructure (Plan 02-07): `docs/api-config.json` deployed via existing pages.yml; `KillSwitchClient.checkOrFailOpen()` uses Mutex-guarded double-checked in-memory cache (D-14); fail-open arm collapses HttpRequestTimeoutException + non-2xx + malformed JSON + DNS failure into Success(defaultConfig); T-02-38 hardening: `severity=block` honored ONLY when `latestSupportedAversBuild != currentAversBuild`

### Pending Todos

[From .planning/todos/pending/ — ideas captured during sessions]

None yet.

### Blockers/Concerns

[Issues that affect future work]

- **Phase 2 (API Reverse-Engineering)** — самая высокая неопределённость в проекте: закрытый ExtJS-API АВЕРС не документирован, все feature-планы фаз 3+ — гипотезы до завершения mitmproxy-захвата
- **Phase 6 (iOS BGAppRefreshTask)** — реальная частота недетерминирована, требует measurements на физических устройствах в течение недели
- **Phase 1 UAT-2 deferred (v2 hardening)** — branch protection «Require conversation resolution» переехала в новый GitHub UI: Settings → Rules → Rulesets. Текущий main без server-side защиты, опираемся на solo-developer workflow + green CI checks
- **Phase 1 UAT-5 deferred** — Privacy Policy URL HTTP 200 + content checks failed (отдельная проблема, не покрыто quick task 260428-h55)

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260428-h55 | fix(01): disable NullSafeMutableLiveData lint detector + close UAT items | 2026-04-28 | 37fc2c2 | [260428-h55-fix-01-disable-nullsafemutablelivedata-l](./quick/260428-h55-fix-01-disable-nullsafemutablelivedata-l/) |

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| *(none — fresh project)* | | | |

## Session Continuity

Last session: 2026-04-29
Stopped at: Wave 5 complete (parallel worktree). Plans 02-07 (kill-switch + docs/api-config.json) and 02-09 (CI iOS jobs + commonTest promote) both merged. EndpointsContractTest now in commonTest, iOS HAR loader real, ci.yml runs :core:database/:core:network/:core:api-avers-v4 iosX64Test on macos-15 with fixtures.dir env-var, Android job runs both canaries. Kill-switch contract live with D-13 fail-open + D-14 cache + T-02-38 hardening.
Resume file: .planning/phases/02-api-reverse-engineering-network-layer/02-07-SUMMARY.md + 02-09-SUMMARY.md (Plan 02-08 docs reads both for AVERS API documentation + manual-smoke wiring)
