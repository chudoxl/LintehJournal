# AVERS API contract changelog

> Diff-журнал для `docs/aversApiV4_23813.md` и параллельных future-build модулей
> (`docs/aversApiV4_23901.md`, `docs/aversApiV4_24001.md`, ...). Каждая АВЕРС-обновлённая
> версия добавляет новый module + новую markdown-доку рядом, и одну запись сюда.

## Format

```
## YYYY-MM-DD — build NNNNN ({module-name})
- {bullet} change vs предыдущий build
- ...
**Capturer:** developer notes (Chrome DevTools session date, account scope)
**Module:** path к новому Gradle-модулю
**Doc:** path к новому MD
```

---

## 2026-04-28 — build 23813 (`:core:api-avers-v4`, baseline)

- **Initial baseline.** Phase 2 reverse-engineering session — нет «предыдущего» build-а
  для diff-а, фиксируем текущее состояние contract-а как нулевой commit.
- **Auth-механизм:** клиентские cookies `ys-user` / `ys-password` / `ys-userId` (сервер
  `Set-Cookie` НЕ отдаёт), JS `escape()` polyfill для Cyrillic-логинов.
- **6 endpoints captured:** login (`POST /login`), grades (`POST /act/GET_STUDENT_JOURNAL_DATA`),
  schedule (`GET /act/GET_TIMETABLE`), homework (`POST /act/GET_STUDENT_DAIRY`),
  attendance (`POST /act/GET_ATT_JOURNAL_DATA`), messages (`POST /act/get_sms`).
- **2 test accounts** (D-01) — cross-account proof в HAR fixtures: account A (user_id 3020 /
  pupil_id 4028 / classId 1013) ≠ account B (user_id 2684 / pupil_id 3661 / classId 1015).
- **Response format:** прямой `JsonArray` (НЕ ExtJS-envelope `{success, data}`) +
  `new Date(Y, M-1, D, ...)` литералы, обрабатываемые через `ExtJsArrayPreprocessor`.
- **Anti-bot signals (D-05):** только passive observation. Никаких anti-CAPTCHA /
  CSRF-tokens / rate-limit headers / 429 responses не наблюдалось в 28 capture-запросах.
- **Pagination (D-27):** documented but not implemented. Никаких `start`/`limit`/`page`/`size`
  параметров не обнаружено; диапазонные date-параметры (`begin_dt`/`end_dt`,
  `period_begin`/`period_end`) — семантическая фильтрация, не пагинация.
- **Coverage gaps deferred к Phase 5 расширения:** итоговые оценки (`/act/GET_STUDENT_DIRECTOR_DATA`
  упомянут в `Login.js`, не captured), история по периодам, замены в расписании,
  прикреплённые файлы ДЗ, веса оценок.
- **Kill-switch (D-12):** `docs/api-config.json` опубликован с `latestSupportedAversBuild=23813`
  / `severity=info` / `minAppVersion=0.2.0` (см. `KillSwitchConfig` в `:core:api-avers-v4`).
- **Logging hygiene (D-28):** sensitive-key list + canary `kanareyka_PASSWORD_DO_NOT_LEAK_42`
  закреплены в `:core:network` `HttpRequestRedactor` + CI gate `tests/log-redactor-canary.sh`.

**Capturer:** developer dev-host (Linux Mint), Chrome DevTools HAR-export +
`tools/capture-avers-fixtures.py` programmatic capture (Plan 02-02).
**Module:** `core/api-avers-v4/`
**Doc:** `docs/aversApiV4_23813.md`

---

> **При следующем АВЕРС-апгрейде:**
>
> 1. Capture свежие HAR против нового build (через `tools/capture-avers-fixtures.py` либо
>    Chrome DevTools fallback). Title тега `<title>АИАС АВЕРС: ... 4.1 (NNNNN)</title>` на
>    корне `/` подскажет новый номер сборки.
> 2. Создать `core/api-avers-v4-NNNNN/` (параллельный Gradle-модуль; копия 23813 как
>    стартовая точка, затем delta).
> 3. Создать `docs/aversApiV4_NNNNN.md` (на основе шаблона `aversApiV4_23813.md`).
> 4. Добавить запись сюда: что изменилось vs 23813 (новые поля, удалённые поля, новые
>    endpoints, изменения auth-механизма).
> 5. Обновить `docs/api-config.json` — поднять `latestSupportedAversBuild` до `NNNNN` ПОСЛЕ
>    того как новый module выпущен в TestFlight / Play Internal track. До этого момента
>    `latestSupportedAversBuild=23813` остаётся как safety-net для старых клиентов.
> 6. Старый модуль / доку оставить как fallback на decommission window (1-2 релиз-цикла).
