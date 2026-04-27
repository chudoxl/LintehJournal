# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-27)

**Core value:** Удобный, быстрый и оффлайн‑доступный мобильный доступ к оценкам, расписанию и домашним заданиям с поддержкой нескольких учеников в одном приложении
**Current focus:** Phase 1 — Foundation & Compliance Infrastructure

## Current Position

Phase: 1 of 6 (Foundation & Compliance Infrastructure)
Plan: 0 of TBD in current phase
Status: Context gathered, ready to plan
Last activity: 2026-04-27 — Phase 1 CONTEXT.md captured (4 areas discussed: module skeleton, CI matrix, Privacy Policy, Hello LinTech stub; 31 implementation decisions locked)

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: —
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**
- Last 5 plans: —
- Trend: —

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- v1 = личное/семейное использование через TestFlight + Google Play Internal track; public submission и formal legal compliance (РКН, согласие школы) отложены в v2
- Биометрическая разблокировка отложена в v2 — KeyChain/Keystore + системный пароль устройства уже обеспечивают защиту кредов
- Compose Multiplatform 1.10.3 + Kotlin 2.2.20 как осознанный технический выбор (см. STACK.md)
- Per-account scope (отдельный файл БД на аккаунт) — архитектурный инвариант с самого начала (см. ARCHITECTURE.md → Pattern 3+4)

### Pending Todos

[From .planning/todos/pending/ — ideas captured during sessions]

None yet.

### Blockers/Concerns

[Issues that affect future work]

- **Phase 2 (API Reverse-Engineering)** — самая высокая неопределённость в проекте: закрытый ExtJS-API АВЕРС не документирован, все feature-планы фаз 3+ — гипотезы до завершения mitmproxy-захвата
- **Phase 6 (iOS BGAppRefreshTask)** — реальная частота недетерминирована, требует measurements на физических устройствах в течение недели

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| *(none — fresh project)* | | | |

## Session Continuity

Last session: 2026-04-27
Stopped at: Phase 1 context gathered (4 areas, 31 decisions)
Resume file: .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md
