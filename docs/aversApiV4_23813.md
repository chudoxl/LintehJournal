# AVERS v4.1 build 23813 — API contract

> ИАС «АВЕРС: Электронный Классный Журнал» build **23813** на хосте `journal.school28-kirov.ru`.
> Документ описывает закрытый ExtJS-API, реверс-инженерный через **Chrome DevTools** (D-02) на
> dev-host Linux Mint и через программный capture (`tools/capture-avers-fixtures.py`,
> Plan 02-02 SUMMARY). Источник истины — sanitized HAR-fixtures в
> `fixtures/sanitized/account-{A,B}/`.

**Версионирование:** при upgrade АВЕРС до build 23901 рядом появится
`docs/aversApiV4_23901.md` и параллельный Gradle-модуль `:core:api-avers-v4-23901`.
Старый модуль/доку оставляем как fallback. Diff-журнал —
`docs/aversApiV4_23813-CHANGELOG.md`.

> **Замечание для будущих сборок:** этот документ привязан строго к build **23813**.
> Когда `<title>АИАС АВЕРС: ... 4.1 (NNNNN)</title>` показывает другой номер сборки, это
> сигнал к запуску reverse-engineering сессии (Plan 02-02 workflow) и созданию нового файла
> `docs/aversApiV4_NNNNN.md`. Документ-инвариант: **один файл на сборку**.

## Базовые координаты

| Параметр | Значение |
|---|---|
| Host | `journal.school28-kirov.ru` |
| Schema | HTTPS only (system trust, no pinning — Pitfall #19) |
| Charset | UTF-8 для `/act/*` и `/login` (Content-Type `text/plain; charset=UTF-8`); HTML-страница `/` отдаётся с `text/html; charset=windows-1251` (но фактические байты — корректный UTF-8) |
| Base path | `/` (production deployment) — все пути абсолютные от корня |
| API build | 23813 (виден в `<title>...4.1 (23813)</title>` HTML-страницы `/`) |
| Captured | 2026-04-29 via Chrome DevTools (D-02) и `tools/capture-avers-fixtures.py` |
| Server | nginx/1.18.0 (Ubuntu) — за реверс-прокси, HTTP/1.1, `Connection: close` |
| ExtJS engine | `/extjs/ext-all.js` + `/extjs/adapter/ext/ext-base.js` (классический Ext.js 3-4 era) |

## Login flow

> **Auth-механизм АВЕРС — клиентские cookies, сервер `Set-Cookie` НЕ отдаёт.**
> Все три auth-cookies клиентский SPA выставляет САМ через `Ext.state.CookieProvider`
> (вызов в `/client/Journal.js`). Это критическое отличие от типового JSESSIONID/PHPSESSID
> stack-а — никаких серверных сессий нет.

### Шаги auth flow

1. **`POST /login`** — `application/x-www-form-urlencoded`
   - **Body параметры:** `l=<login>&p=<sha1_hex(password)>` (SHA-1 хеш пароля в HEX, не plain-text)
   - **Без CSRF-токена** в headers и body — наблюдалось в 12 HAR fixtures
   - **Без JSESSIONID/PHPSESSID** в request cookies — у сервера нет понятия серверной сессии
   - **Response Content-Type:** `text/plain; charset=UTF-8`
   - **Response body** (массив-shape, `eval()`-able): `[[user_id, user_type, null, null, null, "ФИО full", id_a, null, id_pupil]]`
   - **Failure body:** `[error_symbol]` (text-литерал, не JSON) — ошибочные креды
2. **Клиент пишет 3 cookies локально** через `Ext.state.CookieProvider.set(...)`. Никакого
   `Set-Cookie` от сервера — браузер видит их только потому что JS их установил
   (`document.cookie = "ys-user=...; path=/"`).
3. **`POST /auth`** — `application/x-www-form-urlencoded`
   - **Body параметры:** `uId=<user_id>&act=1`
   - **Response body:** строковый литерал `ok` (НЕ JSON)
4. **Все последующие `POST /act/<NAME>`** запросы посылают 3 `ys-*` cookies в заголовке
   `Cookie:`. Сервер читает их на каждом запросе.
5. **401 → авто-релогин** (D-26): Ktor `AversAuthInterceptor` (Plan 02-04) ловит 401, дёргает
   `CredentialProvider.get(accountId)`, повторяет login + auth, ретранслирует исходный
   запрос. Если re-login сам 401 — пробрасывает `AversApiError.Unauthorized`.

### Cookies, выставляемые клиентом после login

> **Все три выставляются ИСКЛЮЧИТЕЛЬНО клиентским JS, сервер их никогда не отдаёт через
> `Set-Cookie`.** Приложение должно вручную инжектировать их в `RoomCookiesStorage` после
> успешного `/login` (Phase 4 wiring).

| Cookie name | Format | Значение пример (sanitized) | Назначение |
|-------------|--------|------------------------------|-----------|
| `ys-user` | `s:<login>` затем JS `escape()` | `s%3A%u0418%u0432%u0430%u043D%u043E%u0432` | Login учётной записи (Cyrillic surname) |
| `ys-password` | `s:<sha1_hex>` затем JS `escape()` | `s%3A1a0b20b21c48749c6e9dcbdcc25473e70355f38d` | SHA-1 hex пароля (НЕ plain-text) |
| `ys-userId` | `n:<user_id>` затем JS `escape()` | `n%3A3020` | Numeric user_id (поле `[0]` ответа `/login`) |

**Что НЕ обнаружено** (отсутствие важных классов cookies подтверждено в 12 HAR fixtures × 28
запросов на 2 аккаунта):
- ❌ `JSESSIONID` — никогда не присутствует в request/response headers
- ❌ `PHPSESSID` — никогда не присутствует
- ❌ Любой `csrf_token` / `XSRF-TOKEN` cookie — никогда
- ❌ `Set-Cookie` response header от сервера — не наблюдался ни на одном запросе
- ❌ Server-issued sticky-load-balancer cookies — не наблюдались

### Encoding gotcha (КРИТИЧНО для Kotlin клиента)

JS `escape()` ≠ `encodeURIComponent()` ≠ Kotlin `URLEncoder.encode()`. Для codepoint'ов
`≥ 256` (Cyrillic) JS `escape()` эмитит `%uXXXX` (4-hex codepoint), НЕ UTF-8 byte sequence
`%XX%XX`. Логины школы №28 — Cyrillic фамилии (например `Иванов`), поэтому Kotlin
client-у требуется **полифилл JS-`escape()` semantics** в `:core:platform`. `URLEncoder.encode()`
в Kotlin/Java не работает — отдаст `%D0%98%D0%B2%D0%B0%D0%BD%D0%BE%D0%B2`, а сервер ожидает
`%u0418%u0432%u0430%u043D%u043E%u0432` и в ответ на `/act/*` отдаёт пустой `[]`.

`tools/capture-avers-fixtures.py` уже реализует это в `js_escape()` (Plan 02-02). Phase 4
`:core:platform` повторит ту же логику в Kotlin (commonMain expect/actual).

### Worked example (sanitized)

См. `fixtures/sanitized/account-A/login.har` — полный HAR с фрагментом login flow:

```http
POST https://journal.school28-kirov.ru/login HTTP/1.1
Content-Type: application/x-www-form-urlencoded; charset=utf-8
Accept: */*
Accept-Language: ru-RU,ru;q=0.9,en;q=0.8
Accept-Encoding: identity

l=REDACTED&p=REDACTED
```

```http
HTTP/1.1 200 OK
Server: nginx/1.18.0 (Ubuntu)
Content-Type: text/plain; charset=UTF-8
Connection: close

[[3020,4,null,null,null,"Петров П.П.",1013,null,4028]]
```

После этого клиент делает второй запрос `POST /auth` с `uId=3020&act=1` и в ответ получает
строку `ok`. Cookies (`ys-user`, `ys-password`, `ys-userId`) выставляются клиентом локально.

**Cross-account proof:** `fixtures/sanitized/account-B/login.har` показывает идентичную
shape но другие numeric ids: `user_id=2684`, `id_pupil=3661`, `classId=1015` (см.
§Endpoint map → Cross-account).

## Response format

> АВЕРС использует **прямой JSON-array без ExtJS-envelope**. Это не стандартный
> `{success, data, msg?}`-stack, а array-of-arrays. Mock-engine, парсер и DTOs
> `:core:api-avers-v4` (Plan 02-06) рассчитаны именно на это.

### Что обнаружено

- **Доминирующий paттерн:** прямой `JsonArray` без обёртки. Например
  `/act/GET_STUDENT_JOURNAL_DATA` → `[[mark_id, pupil_id, value, date, ...], ...]`
- **`/login` и `/auth`:** тоже массив (login) или string-literal `ok` (auth). НЕТ
  `{success: true, data: ...}` envelope.
- **Bootstrap-цепочка** (`get_user_data`, `get_uch_year`, `GET_STUDENT_CLASS`,
  `GET_STUDENT_PARALLEL`) — всегда массив-of-array.
- **Failure / empty:** `[]` (пустой массив) или `[error_symbol]` (literal). Никаких
  `{success: false, msg: "..."}` не наблюдалось.

### `AversEnvelope.unwrapAversEnvelope` (Plan 02-06)

`core/api-avers-v4/.../dto/envelope/AversEnvelope.kt` — толерантный универсальный
unwrapper, рассчитанный на оба варианта (на случай если будущая сборка АВЕРС перейдёт
на envelope):

| Input | Output |
|-------|--------|
| `{"success": true, "data": [...]}` | `Success(UnwrappedEnvelope(data))` |
| `{"success": true}` (нет data) | `Mismatch(missingFields=["data"], endpoint, rawDump)` |
| `{"data": [...]}` (нет success) | `Mismatch(missingFields=["success"], …)` |
| `{"success": false, "msg": "..."}` | `Failure(Server(httpCode=200))` |
| `[1, 2, 3]` (raw array, без envelope) | `Success(UnwrappedEnvelope(rawArray))` — **доминирующий путь для AVERS `/act/...`** |
| primitive (например `"ok"`) | `Success(UnwrappedEnvelope(primitive))` |

### Response shape — НЕ строгий JSON (`new Date(...)` literals)

Сервер эмитит ExtJS-style код, который клиент пропускает через `eval()`. Например:

```
[
  [new Date(2026,3,27,0,0,0,0), 1379529, 3, "Родная страна и страны изучаемого языка", "", null, 1, null, null, 49, 0, null],
  ...
]
```

Обратите внимание на `new Date(YYYY, M_minus_1, DD, h, m, s, ms)` — JS `Date` constructor с
**0-индексным месяцем**. Strict JSON-парсеры (kotlinx.serialization) отвергают это.

**Решение** (`ExtJsArrayPreprocessor.toStrictJson`, Plan 02-06):

```kotlin
val isoBody = body.replace(
    Regex("""new\s+Date\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,[^)]*)?\)""")
) { m ->
    val year = m.groupValues[1]
    val month = m.groupValues[2].toInt() + 1  // JS Date 0-indexed → ISO 1-indexed
    val day = m.groupValues[3].toInt()
    "\"%s-%02d-%02d\"".format(year, month, day)
}
```

Затем `Json.parseToJsonElement(isoBody)`. Покрытие — `ExtJsArrayPreprocessorTest` (5 кейсов
включая декабрь и multi-line массивы).

### Worked example payload (sanitized)

Запрос `POST /act/GET_STUDENT_JOURNAL_DATA` для аккаунта A (`cls=1013`, `student=4028`):

```
HTTP/1.1 200 OK
Content-Type: text/plain; charset=UTF-8

[[1379529,4028,"5",new Date(2026,3,15,0,0,0,0),49,null,3,"Иванов И.И.",null]]
```

После `ExtJsArrayPreprocessor`:

```json
[[1379529,4028,"5","2026-04-15",49,null,3,"Иванов И.И.",null]]
```

Декодируется в `GradeDto(markId=1379529, pupilId=4028, value="5", dateString="2026-04-15", lessonId=49, ...)`.
Имена в фактическом sanitized fixture — детерминированные fakes из `tools/sanitize-har.py`
NAME_POOL (`Иванов И.И.`, `Петров П.П.`, `Сидоров С.С.`). Реальные ФИО учеников
не попадают в публичный документ — Phase 1 sanitize-canary (`tests/sanitize-har-canary.sh`)
гоняется в CI и блокирует регрессии.

### Strict-but-tolerant parsing (D-09)

`Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }` — толерантно
к новым полям АВЕРС, не падаем на минорных добавлениях. Положение полей в массиве (positional
KSerializers) tolerant к trailing-полям; обязательные позиции проверяются явно через
`PositionalRowReader.requireLong(row, idx)` или `Mismatch` если структура неожиданная.

### Other shape quirks (Plan 02-02 SUMMARY)

- **Content-Type `text/plain; charset=UTF-8`** для `/act/*` и `/login` (НЕ `application/json`)
- **Корневой `/`** отдаётся как `text/html; charset=windows-1251` — заголовок неправильный,
  фактические байты UTF-8. Клиент не должен полагаться на charset-объявление; для `/` это
  не критично (HTML парсится только клиентским HTML-парсером).
- **`Content-Length` mismatch на `/`** — сервер анонсирует 13693 байта, отдаёт 13651.
  Python urllib бросает `IncompleteRead`. Ktor аналогично должен принимать частичный
  body — `expectSuccess = false` + try/catch вокруг `bodyAsText()`. Ktor в большинстве
  engine-ов (Darwin/OkHttp) обрабатывает это автоматически.

## Endpoint map

> Все 6 endpoints + bootstrap chain. Path-ы и query/form params — verbatim из
> `fixtures/sanitized/account-{A,B}/*.har` и подтверждены в `core/api-avers-v4/.../AversApi.kt`
> (`internal object AversEndpoints`).

### Production endpoints (Phase 2 scope, ROADMAP success #1)

| # | Endpoint | HTTP | Path | Form/Query params | DTO | Suspend fn |
|---|----------|------|------|-------------------|-----|------------|
| 1 | login | POST | `/login` | `l` (login), `p` (sha1_hex) | `LoginDto` | `AversAuthApi.login(loginValue, sha1HexPassword)` |
| 2 | auth (Phase 4) | POST | `/auth` | `uId`, `act=1` | (string `ok`) | (Phase 4 wiring) |
| 3 | logout | GET | `/auth/logout` | — | — | `AversAuthApi.logout()` |
| 4 | grades | POST | `/act/GET_STUDENT_JOURNAL_DATA` | `cls`, `student` | `GradeDto` | `AversApi.fetchGrades(period)` |
| 5 | schedule | GET | `/act/GET_TIMETABLE` | — (cookie scope) | `LessonDto` | `AversApi.fetchSchedule(period)` |
| 6 | homework | POST | `/act/GET_STUDENT_DAIRY` | `student`, `cls`, `begin_dt`, `end_dt` (формат `DD.MM.YYYY`) | `HomeworkDto` | `AversApi.fetchHomework(period)` |
| 7 | attendance | POST | `/act/GET_ATT_JOURNAL_DATA` | `cls`, `period_begin`, `period_end` | `AttendanceDto` | `AversApi.fetchAttendance(period)` |
| 8 | messages | POST | `/act/get_sms` | `uchYear` | `MessageDto` | `AversApi.fetchMessages()` |

### Bootstrap chain (Phase 4 :core:data orchestrator)

Перед вызовами `grades`/`homework`/`attendance` нужно дёрнуть цепочку, чтобы получить
`classId` (`cls`), `studentId` (`student`), `academicYear` (`uchYear`):

| # | Action | HTTP | Form params | Возвращает |
|---|--------|------|-------------|-----------|
| B1 | `get_user_data` | POST | (нет) | `[["lastname","first_initial","patronymic_initial","school_full_name", uchId, max_mark]]` |
| B2 | `get_uch_year` | POST | `currentDate` (`DD.MM.YYYY`) | `[[year]]` (например `[[2025]]`) |
| B3 | `GET_STUDENT_CLASS` | POST | `currentDate, student, uchYear, uchId` | `[[classId, parallel, "letter"]]` |
| B4 | `GET_STUDENT_PARALLEL` | POST | `student, uchYear, uchId` | `[[parallelClassIds...]]` (часто `[]`) |
| B5 (только homework) | `GET_DAIRY_CLASS_SUBJECTS` | POST | `pClassesIds, cls` | список subjects |

Bootstrap chain пишется в Phase 4 (`:core:data` mapper), не в Phase 2. Phase 2 callers
populate `StudentScope(classId, studentId, academicYear)` вручную.

### Fixture-to-endpoint coverage

Все 6 production endpoints покрыты HAR-fixtures × 2 аккаунта = 12 файлов:

| Endpoint | Fixture A | Fixture B | DTO test (EndpointsContractTest) |
|----------|-----------|-----------|----------------------------------|
| login | `fixtures/sanitized/account-A/login.har` | `fixtures/sanitized/account-B/login.har` | `LoginDto` (positional decoder) |
| grades | `fixtures/sanitized/account-A/grades.har` | `fixtures/sanitized/account-B/grades.har` | `GradeDto` × N |
| schedule | `fixtures/sanitized/account-A/schedule.har` | `fixtures/sanitized/account-B/schedule.har` | `LessonDto` × N |
| homework | `fixtures/sanitized/account-A/homework.har` | `fixtures/sanitized/account-B/homework.har` | `HomeworkDto` × N |
| attendance | `fixtures/sanitized/account-A/attendance.har` | `fixtures/sanitized/account-B/attendance.har` | `AttendanceDto` (`[]` для UT_STUDENT) |
| messages | `fixtures/sanitized/account-A/messages.har` | `fixtures/sanitized/account-B/messages.har` | `MessageDto` (`[]` без активных SMS) |

**Cross-account proof** (D-01, D-23, D-24):

| Field | Account A | Account B |
|-------|-----------|-----------|
| user_id | 3020 | 2684 |
| user_type | 4 (UT_STUDENT) | 4 (UT_STUDENT) |
| pupil_id | 4028 | 3661 |
| classId | 1013 | 1015 |
| schedule.har размер | 80498 b | 80498 b |
| grades.har размер | 42002 b | 52836 b |

Distinct ids подтверждают per-account-scope invariant: `journal_${accountId}.db` не будет
кросс-контаминирован.

### DTO field shapes (Plan 02-06)

Все DTOs декодируют `JsonArray` положительно (positional KSerializer). Out-of-bounds /
null / missing колонки возвращают `null` через `PositionalRowReader`. Phase 4 mapper
расширяет без breaking-change.

| DTO | Source | Колонки (zero-indexed) |
|-----|--------|------------------------|
| `LoginDto` | `/login` | 0=user_id, 1=user_type, 5=fio, 8=id_pupil |
| `GradeDto` | `/act/GET_STUDENT_JOURNAL_DATA` | 0=mark_id, 1=pupil_id, 2=value (`"5"`/`"4"`/`"Н"`/`"Б"`/`"ОСВ"`), 3=date_iso, 4=lesson_id, 6=type_code, 8=comment |
| `LessonDto` | `/act/GET_TIMETABLE` | 0=id, 1=lesson_id, 2=subject_id, 5=weekday, 6=in_day, 7=shift, 8=room |
| `HomeworkDto` | `/act/GET_STUDENT_DAIRY` | 0=date_iso, 1=lesson_id, 2=subject_id, 3=theme, 4=homework_text, 5=mark, 6=in_day, 9=teacher_id, 11=comment |
| `AttendanceDto` | `/act/GET_ATT_JOURNAL_DATA` | placeholder — endpoint возвращает `[]` для student role; live shape TBD при teacher fixtures (Plan 02-02 SUMMARY) |
| `MessageDto` | `/act/get_sms` | placeholder — endpoint возвращает `[]` для обоих captured аккаунтов; conservative `[id, date, sender, subject, body, is_read]` ordering |

`value: String` для grades **сохраняется literally** — `"5"`, `"4"`, `"Н"` (отсутствие),
`"Б"` (болен), `"ОСВ"` (освобождён), digit grades. Phase 4 решает display semantics.

## Anti-bot signals (D-05 — пассивное наблюдение)

> **Никакого активного probing'а.** Не «вводим неправильный пароль 5 раз», не имитируем
> bot-трафик в сторону сервера школы №28. Документируем только то, что увидели в
> обычных user-flow captures.

### Cookies, замеченные в HAR

| Cookie | Назначение | Кем выставляется |
|--------|-----------|------------------|
| `ys-user` | Login auth (`s:<login>` → JS escape) | Клиент (`Ext.state.CookieProvider`) |
| `ys-password` | SHA-1 hex пароля | Клиент |
| `ys-userId` | Numeric user_id | Клиент |
| _(никаких других)_ | — | _(сервер `Set-Cookie` НЕ отдаёт)_ |

### Заголовки, замеченные в HAR

| Заголовок | Назначение | Replay-обязательный? |
|-----------|-----------|---------------------|
| `User-Agent` | Mozilla/5.0 (X11; Linux x86_64)... Chrome/124.0.0.0 Safari/537.36 (capture script) | НЕТ — сервер принимает curl/urllib/Chrome UA одинаково |
| `Accept-Language: ru-RU,ru;q=0.9,en;q=0.8` | RU-предпочтение | НЕТ |
| `Accept: */*` | — | НЕТ |
| `Accept-Encoding: identity` | Без gzip (упрощает capture) | НЕТ — сервер поддерживает gzip но identity тоже принимается |
| `X-Requested-With: XMLHttpRequest` | ExtJS marker | НЕТ — сервер не проверяет |

**Что НЕ обнаружено:**
- ❌ Никаких custom `X-*` security headers (`X-CSRF-Token`, `X-API-Key` и т.п.)
- ❌ Никаких `Authorization` заголовков (Bearer/Basic) — auth идёт целиком через cookies

### Anti-CAPTCHA endpoints (если найдено)

**Не найдено anti-CAPTCHA endpoints в 12 HAR captures × 28 запросов на 2 аккаунта.** Сервер
отвечал HTTP 200 на все валидные запросы. Failed login (с неправильными кредами) возвращает
`[error_symbol]` text-литерал, НЕ HTML CAPTCHA-форму.

**Defense-in-depth (Phase 2 — Plan 02-06):** `AntiBotDetector` в `:core:api-avers-v4` всё
равно проверяет два сигнала:
1. **Content-Type sniff:** `text/html` на `/act/*` → `AversApiError.AntiBotChallenge(rawHtml)`
2. **Body sniff:** парсинг тела ищет `<!DOCTYPE`, `<html`, `<form action="/captcha`,
   `g-recaptcha` маркеры. Если найдено — `AntiBotChallenge`, Phase 3 откроет WebView fallback (D-06).

Если в будущем такие endpoints появятся — здесь надо обновить документ + добавить запись
в `docs/aversApiV4_23813-CHANGELOG.md`.

### Throttling / Retry-After

- ❌ **HTTP 429 ответы — не наблюдались** на текущем volume (28 запросов на аккаунт за
  capture-сессию).
- ❌ **`Retry-After` header — не присутствует** в response headers.
- **Ktor `HttpRequestRetry` (Plan 02-04)** ретраит на `5xx` + `429` (если когда-нибудь
  появится) с exponential delay 1s..30s + max 3 attempts. Honored `Retry-After` если
  сервер начнёт его эмитить.

### Логин rate (D-05 общепринятые значения, не подтверждённые на проде)

Так как активный probing запрещён, baseline rate берётся из общепринятых рекомендаций для
school-server без backend-проксирования:
- **1 login per 5s baseline** на учётную запись
- **Exponential backoff** на ошибки auth (1s → 2s → 4s → 8s → ...)
- **Manual smoke** (`tools/manual-smoke.sh`, см. ниже §Manual smoke) — единственный live
  путь; запрещён в CI (D-25).

## Pagination (D-27 — документируем, не реализуем)

> **Phase 2 НЕ реализует clientside paging.** Endpoint-функции (`AversApi.fetch*`)
> возвращают full result. Phase 4 UI может перейти на paging если потребуется.

### Параметры pagination, обнаруженные в HAR

- ❌ **ExtJS-style `start`/`limit`** — НЕ обнаружено ни на одном из 6 endpoints.
- ❌ **`offset`/`limit`** — не обнаружено.
- ❌ **`page`/`size`** — не обнаружено.
- **Что есть:** диапазонные параметры по дате — `begin_dt`/`end_dt` (homework),
  `period_begin`/`period_end` (attendance), `currentDate` (bootstrap chain).
  Это семантическая фильтрация, не пагинация.

### Канонический паттерн Phase 2

`fetch{Endpoint}(period: Period)` → все записи за период. Для школьного журнала
record-counts малые (~hundreds в seasonal endpoints, тысячи в годовом расписании). Полный
fetch допустим в memory-budget мобильного клиента.

Если в Phase 4 UI выяснится что годовой `/act/GET_TIMETABLE` (~80KB) тяжело рендерить —
clientside paging добавляется в `:core:data` слое, не в API contract.

## Coverage gaps (Phase 5 deferred)

> Phase 2 captures охватывают только базовые сценарии (D-24). Следующие подсценарии
> отложены в Phase 5 расширения и **НЕ покрыты** текущими DTO / fixtures.

| # | Сценарий | Куда отнесён | Когда добавлять |
|---|----------|--------------|-----------------|
| 1 | **Итоговые оценки** (за период / год — отдельный endpoint, отличный от текущего grades) | Phase 5 | Расширение `GradeDto` или новый `FinalGradeDto` + новый fixture-set + reverse-engineering сессии для `/act/GET_STUDENT_DIRECTOR_DATA` (упомянут в `Login.js`, не captured) |
| 2 | **История по периодам** (предыдущие четверти/триместры) | Phase 5 | Новый query-param к существующим endpoints + миграция Room schema |
| 3 | **Замены в расписании** | Phase 5 | Расширение `LessonDto` (флаг `isReplacement`) — нужен capture учителя или класса с заменой; на student role не наблюдается отдельный signal |
| 4 | **Прикреплённые файлы ДЗ** (вложения к homework) | Phase 5 | Расширение `HomeworkDto` + Coil 3 для preview + кэш файлов в `journal_${accountId}.db` |
| 5 | **Веса оценок** (для прогноза итоговой) | Phase 5 (или v2) | Расширение `GradeDto.weight` если АВЕРС отдаёт; иначе UI-дисклеймер «веса недоступны» |

**Worked example для каждого:** при первой capture-сессии в Phase 5 doc-author расширяет
этот документ. Изменения логируются в `docs/aversApiV4_23813-CHANGELOG.md`.

## Error model

`AversApiError` (`core/api-avers-v4/.../result/AversApiError.kt`) — sealed:

| Variant | When | Phase 3 UI handling |
|---------|------|---------------------|
| `Unauthorized` | 401 после re-login (либо тело `[error_symbol]` для `/login` failure) | Login screen |
| `AntiBotChallenge(rawHtml)` | HTML-страница вместо JSON / CAPTCHA-форма | WebView fallback (Phase 3 D-06); rawHtml загружается в WebView |
| `Network` | I/O / timeout / DNS / `IncompleteRead` без частичного body | Offline-banner + retry |
| `ContractMismatch(rawDump, missingFields, endpoint)` | Mandatory field отсутствует / payload не парсится после `ExtJsArrayPreprocessor` | "Обновите приложение" banner — может означать что АВЕРС обновился до 23901 |
| `Server(httpCode)` | 5xx или иной non-success HTTP-код | Retry / banner |
| `KillSwitchTriggered` | api-config.json severity=block + build mismatch | "Обновите приложение" banner |

`ApiResult<T>` — sealed: `Success<T>`, `Mismatch(rawDump, missingFields, endpoint)`,
`Failure(AversApiError)`. Endpoint функции **никогда не throw** на boundary
`:core:api-avers-v4` (`runCatching { ... }.getOrElse { mapException(...) }` wrapper в
`AversApi.executeListEndpoint`).

## Logging hygiene (D-28)

> Pitfall #5 closer. Sanitization работает в **debug И release** (D-28 mandatory addendum).

### Sensitive keys

`HttpRequestRedactor` (`core/network/.../plugins/HttpRequestRedactor.kt`) маскирует
следующий список ключей в:
- HTTP headers (`Cookie`, `Set-Cookie`, `Authorization`)
- Form body params (Ktor `FormData`)
- JSON body fields (если в будущем АВЕРС перейдёт на JSON — на 23813 это не критично,
  но redactor работает proactively)

```
password
pwd
pass
cookie
authorization
set-cookie
token
```

### sanitizeHeader (Ktor Logging plugin)

Ktor `Logging` plugin конфигурируется с `sanitizeHeader { it == "Authorization" || it == "Cookie" || it == "Set-Cookie" }`
— значения этих headers заменяются на `***` в Kermit-выводе.

### Canary string (ROADMAP success #5 / Pitfall #5)

```
kanareyka_PASSWORD_DO_NOT_LEAK_42
```

**CI gate:** `tests/log-redactor-canary.sh` запускает `:core:network:test --info`,
грепает stdout/stderr на canary-строку; **exit 1 если найдена**. Тест работает на
debug-build (mandatory D-28 addendum) — если канарейка leaked в debug-логи, CI красный.

Plan 02-04 wired canary в `HttpRequestRedactorTest`: тест устанавливает
`AVERS_PASSWORD=kanareyka_PASSWORD_DO_NOT_LEAK_42`, выполняет fake-login через MockEngine,
проверяет что canary НЕ появляется ни в одном логе HTTP-плагина.

### Транслитерация

`kanareyka` (НЕ `canary`, не `канарейка`) — закреплено в ROADMAP success #5; уважение
D-08 (transliteration policy `linteh` package root).

## Kill-switch (D-12, D-13, D-14)

> Remote kill-switch — статический JSON на GitHub Pages:
> `https://chudoxl.github.io/LintehJournal/api-config.json`. Бесплатный CDN, GitHub-native,
> auto-deploy через `pages.yml` (триггер: `docs/**` push).

### Schema

`core/api-avers-v4/.../killswitch/KillSwitchConfig.kt`:

```json
{
  "latestSupportedAversBuild": "23813",
  "message": "",
  "severity": "info",
  "minAppVersion": "0.2.0"
}
```

| Field | Type | Semantics |
|-------|------|-----------|
| `latestSupportedAversBuild` | string | Текущий supported АВЕРС build. Mismatch + severity=block ⇒ trigger. |
| `message` | string | UI-текст для banner-а. Пустой = нет сообщения. |
| `severity` | enum `info`, `warning`, `block` | `block` ⇒ `AversApiError.KillSwitchTriggered`. |
| `minAppVersion` | string | Soft hint для Phase 4 UI (баннер «обновите приложение»). |

**Forward-compatibility:** `Severity.fromWire(value: String?)` downgrade-ит unknown wire
values до `info` — будущие enum-codes (`silent`, `nudge`, ...) не будут крашить старые
client-ы.

### Fail-open (D-13)

GitHub Pages 4xx/5xx/timeout/`SerializationException` → `KillSwitchClient.checkOrFailOpen()`
возвращает `Success(defaultConfig())`. **Никогда не блокируем offline-юзера** из-за CDN
downtime.

`runCatching {...}.getOrElse { e -> defaultConfig() }` arm ловит:
- `HttpRequestTimeoutException` (5s по умолчанию)
- `response.status.isSuccess() == false` (явная проверка inside runCatching)
- `SerializationException` (malformed JSON)
- `IOException` (DNS / TLS / network)

### Cadence (D-14)

Один fetch при cold-start, кэш в памяти на сессию (`Mutex`-guarded double-checked locking).
Phase 6 BGAppRefreshTask может расширить до 1 раз / 24ч в фоне через
`KillSwitchClient.invalidateCache()` + повторный `checkOrFailOpen()`.

### T-02-38 hardening

`severity=block` honored ТОЛЬКО при `latestSupportedAversBuild != currentAversBuild`. Если
admin случайно опубликует `severity=block` с тем же build-ом, что у клиента — `block`
downgrade-ится до `Success` с warn-логом. Защищает от случайного DoS при misconfigured
prod-config.

Verified by `severity_block_with_same_build_returns_success_hardening_t_02_38` test case
(Plan 02-07 SUMMARY).

## Manual smoke (dev-host only)

> **D-25:** live запросы к `journal.school28-kirov.ru` ЗАПРЕЩЕНЫ в CI. Только локальный
> manual run.

`tools/manual-smoke.sh` — bash-script на dev-host (Linux Mint). Читает `.env.local`
(`AVERS_LOGIN`, `AVERS_PASSWORD`), делает один `POST /login` + один `POST /act/GET_STUDENT_JOURNAL_DATA`
(grades) fetch, проверяет shape против ожидаемой типизации (валидный JSON после
`ExtJsArrayPreprocessor`). Все логи через `HttpRequestRedactor` (canary защита).

См. `tools/manual-smoke.README.md` для usage / setup / exit codes.

**Проверка CI-prohibition:** скрипт первым делом проверяет `$CI` и `$GITHUB_ACTIONS`
env-vars и завершается `exit 1` если они выставлены. `.github/workflows/*.yml` НЕ
содержит вызовов `tools/manual-smoke.sh` — это инвариант (если когда-либо появится — это
баг, нарушающий D-25).

## References

- [`.planning/ROADMAP.md`](../.planning/ROADMAP.md) §Phase 2 success criteria
- [`.planning/phases/02-api-reverse-engineering-network-layer/02-CONTEXT.md`](../.planning/phases/02-api-reverse-engineering-network-layer/02-CONTEXT.md) (D-01..D-28)
- [`.planning/phases/02-api-reverse-engineering-network-layer/02-RESEARCH.md`](../.planning/phases/02-api-reverse-engineering-network-layer/02-RESEARCH.md) (Architecture Patterns, Pitfalls)
- [`.planning/phases/02-api-reverse-engineering-network-layer/02-02-SUMMARY.md`](../.planning/phases/02-api-reverse-engineering-network-layer/02-02-SUMMARY.md) — capture observations (auth, ExtJS, IncompleteRead)
- [`.planning/phases/02-api-reverse-engineering-network-layer/02-06-SUMMARY.md`](../.planning/phases/02-api-reverse-engineering-network-layer/02-06-SUMMARY.md) — DTO contract + AversApi
- [`.planning/phases/02-api-reverse-engineering-network-layer/02-07-SUMMARY.md`](../.planning/phases/02-api-reverse-engineering-network-layer/02-07-SUMMARY.md) — kill-switch wiring
- `fixtures/sanitized/account-{A,B}/*.har` — source-of-truth captures (12 файлов)
- `core/api-avers-v4/` — typed contract module (Plan 02-06 + 02-07)
- `tools/capture-avers-fixtures.py` — programmatic HAR capture (Plan 02-02)
- `tools/sanitize-har.py` — sanitization (NAME_POOL: `Иванов И.И.`, `Петров П.П.`, `Сидоров С.С.`)
- `tools/manual-smoke.sh` — dev-host live smoke (Plan 02-08, **dev-only**)
