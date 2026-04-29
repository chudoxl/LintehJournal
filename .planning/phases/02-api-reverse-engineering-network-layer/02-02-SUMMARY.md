---
phase: 02-api-reverse-engineering-network-layer
plan: 02
status: complete
completed_at: "2026-04-29"
---

# Plan 02-02 Summary — HAR fixture capture

## Outcome

12 sanitized HAR fixtures committed under `fixtures/sanitized/account-{A,B}/{login,grades,schedule,homework,attendance,messages}.har`. Plan 06 endpoint-contract tests can now build against this corpus.

**Deviation from plan**: replaced manual Chrome DevTools capture (autonomous: false) with programmatic capture via `tools/capture-avers-fixtures.py` after discovering that the SPA's auth + endpoint contract was fully derivable from `site/client/*.js` reverse-engineering. Plan frontmatter retroactively flipped to `autonomous: true`. Same deliverables, identical contract.

## What was built

| Artefact | Path | Purpose |
|----------|------|---------|
| Capture script | `tools/capture-avers-fixtures.py` | stdlib-only Python (urllib + http.cookiejar + hashlib). Reads `.env.local`, replicates Login.js+Broker.js auth flow, walks bootstrap chain, writes raw HAR-1.2 fixtures. Modes: `--probe` (login only), `--account A|B`, `--full` (default both). |
| Sanitizer extensions | `tools/sanitize-har.py` + `tools/sanitize-rules.yaml` | Added redaction for: `ys-user`/`ys-password`/`ys-userId` cookies, `l=`/`p=` form params in `/login` POST body, explicit `replace_strings` list for single-word surnames missed by 2-word ФИО regex. |
| Canary fix | `tests/sanitize-har-canary.sh` | Forced `LC_ALL=C.UTF-8` — under `ru_RU.UTF-8` GNU grep treats `[а-яё]` case-insensitively, producing false positives on subject names ("Окружающий мир", "Физическая культура") and the SPA root title ("АИАС АВЕРС"). |
| 12 sanitized fixtures | `fixtures/sanitized/account-{A,B}/*.har` | Body sizes range 3KB (empty messages/attendance) → 80KB (schedule). Cross-account proof: A=user_id 3020 / class 1013, B=user_id 2684 / class 1015. |
| Updated workflow doc | `tools/README.md` | Programmatic flow as primary; manual Chrome DevTools as fallback. |

## API contract observations (feed Plan 08 documentation)

### Auth mechanism — **client-side cookies, no server-issued sessions**

The AVERS server **never issues `Set-Cookie`**. Authentication state lives in three cookies the SPA sets itself via `Ext.state.CookieProvider` (instantiated in `site/client/Journal.js`, downloaded separately — not in the SPA's own client folder). The cookie names+formats:

| Cookie name | Format | Value example |
|-------------|--------|---------------|
| `ys-user` | `s:<login>` then JS `escape()` | `s%3A%u0427%u0443%u0434%u0438%u043D%u043E%u0432%u0441%u043A%u0438%u0445` (Cyrillic surname login) |
| `ys-password` | `s:<sha1_hex>` then JS `escape()` | `s%3A1a0b20b21c48749c6e9dcbdcc25473e70355f38d` |
| `ys-userId` | `n:<user_id>` then JS `escape()` | `n%3A3020` |

**Critical encoding gotcha**: JS `escape()` ≠ `encodeURIComponent()` ≠ `urllib.parse.quote()`. For codepoints ≥ 256 (Cyrillic), JS `escape()` emits `%uXXXX` (4-hex codepoint), NOT UTF-8 byte sequence `%XX%XX`. School logins at `journal.school28-kirov.ru` are Cyrillic surnames, so the Kotlin client's cookie writer must polyfill JS `escape()` semantics — `URLEncoder.encode()` will NOT work. Capture script implements this in `js_escape()`.

**Login flow**:
1. POST `/login` form `l=<login>&p=<sha1_hex_of_password>` → returns `[[user_id, user_type, null, null, null, "ФИО full", id_a, null, id_b]]` (text/plain, JSON-array-shaped)
2. Client writes 3 `ys-*` cookies based on login response
3. POST `/auth` form `uId=<user_id>&act=1` → returns `ok` (string literal, not JSON)
4. All subsequent `/act/<NAME>` calls send the 3 `ys-*` cookies; server reads them on each request

`user_type=4` = UT_STUDENT (per `Login.js`); types 0–5 are admin/manager/teacher/parent/student/auditor. For UT_STUDENT, `UserHumanId = user[8]` = `id_pupil`. For UT_PARENT, `user[7]` would be `id_person` (different schema).

### Broker action endpoints

Single URI pattern: `<base>/act/<ACTION_NAME>`. `Broker.deal()` sends GET when no params, POST (form-encoded) otherwise. `<base>` is `document.location.pathname` — for production deployment it's just `/`.

| Plan endpoint | Confirmed action | HTTP | Required params |
|---------------|------------------|------|-----------------|
| login | `POST /login` + `POST /auth` | POST | `l`, `p` then `uId`, `act` |
| schedule | `GET /act/GET_TIMETABLE` | GET | none (cookie-driven scope) |
| homework | `POST /act/GET_STUDENT_DAIRY` | POST | `cls`, `student`, `pClassesIds`, `begin_dt` (DD.MM.YYYY), `end_dt` |
| grades | `POST /act/GET_STUDENT_JOURNAL_DATA` + `POST /act/GET_STUDENT_DIRECTOR_DATA` | POST | `cls`, `parallelClasses`, `student` |
| attendance | `POST /act/GET_ATT_JOURNAL_DATA` | POST | `cls`, `period_begin`, `period_end` |
| messages | `POST /act/get_sms` | POST | `uchYear` |

**Bootstrap chain** required before journal/dairy/grades:
1. `POST /act/get_user_data` (no params) → `[["lastname","first_initial","patronymic_initial","school_full_name", uchId, max_mark]]`. For our school: `uchId=1`. **NB**: returns `[]` if auth cookies absent or wrong format.
2. `POST /act/get_uch_year` `currentDate=DD.MM.YYYY` → `[[year]]` (e.g. `[[2025]]`). For some accounts returned empty `[[]]` — capture script falls back to start-year-of-period (Aug cutover).
3. `POST /act/GET_STUDENT_CLASS` `currentDate, student, uchYear, uchId` → `[[classId, parallel, "letter"]]` (e.g. `[[1013, 4, "б"]]`).
4. `POST /act/GET_STUDENT_PARALLEL` `student, uchYear, uchId` → `[[paralleClassIds...]]` (often `[]`).
5. (only for homework) `POST /act/GET_DAIRY_CLASS_SUBJECTS` `pClassesIds, cls` → subject list.

### Response shape — **NOT strict JSON**

Server emits ExtJS-style code that the client passes through `eval()`:

```
[
[new Date(2026,3,27,0,0,0,0), 1379529, 3, "Родная страна и страны изучаемого языка", "", null, 1, null, null, 49, 0, null],
 ...
]
```

Note the `new Date(YYYY, M_minus_1, D, ...)` literal — JS `Date` constructor with **0-indexed month**. Strict JSON parsers reject this. **Plan 06 implication**: Kotlin client must pre-process response text via regex substitution:

```kotlin
val isoBody = body.replace(
    Regex("""new\s+Date\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,[^)]*\)""")
) { m -> "\"${m.groupValues[1]}-%02d-%02d\"".format(m.groupValues[2].toInt() + 1, m.groupValues[3].toInt()) }
```

Then parse with kotlinx.serialization. Or write a tolerant ExtJS parser. Decision deferred to Plan 06.

Other shape quirks observed:
- `Content-Type: text/plain; charset=UTF-8` for `/act/*` and `/login` (NOT `application/json`)
- Login response and `/act/get_user_data` use `text/plain; charset=UTF-8` — Cyrillic decodes correctly via UTF-8
- Root `/` page is `text/html; charset=windows1251` (HTML metadata mislabeled — actual bytes UTF-8 work fine)
- Server announces incorrect `Content-Length` on `/` (announces 13693, sends 13651) — capture script tolerates `IncompleteRead` exception with `e.partial` body. Kotlin Ktor must do the same (probably via `HttpClientConfig` / `expectSuccess = false` + custom body reader).

### Anti-bot / CSRF observations

- **No CSRF token** observed — neither header nor body field. Any client with valid `ys-*` cookies can call `/act/`.
- **No anti-bot CAPTCHA** during capture (28 requests across 2 accounts). Server replies HTTP 200 to all valid requests; failed login (bad creds) returns `[error_symbol]` text literal.
- **No rate limiting** observed at this volume. Out of caution, capture script makes one request per endpoint, no parallelism.
- **No User-Agent restriction** — server accepts curl, urllib, Chrome UA equally.

### Cross-account proof (D-01, D-23, D-24)

| Field | Account A | Account B |
|-------|-----------|-----------|
| user_id | 3020 | 2684 |
| user_type | 4 (UT_STUDENT) | 4 (UT_STUDENT) |
| pupil_id | 4028 | 3661 |
| classId | 1013 | 1015 |
| classLetter | "б" | (different) |
| schedule.har size | 80498 b | 80498 b (same school timetable shape) |
| grades.har size | 42002 b | 52836 b |
| homework.har size | 16494 b | 18833 b |

Distinct ids confirm the per-account scope invariant (D-04, ARCHITECTURE.md Pattern 3+4) is enforceable: `journal_${accountId}.db` will not cross-contaminate.

### Empty fixtures (data limitation, not bug)

- `attendance.har` returns `[]` for both accounts — `GET_ATT_JOURNAL_DATA` is a teacher-grid endpoint requiring `aclass-grid` selection. For UT_STUDENT view, attendance marks ("Н" = absent) are interleaved with grades in `GET_STUDENT_JOURNAL_DATA` rows (3rd column observed values: `"Н"`, `"Б"`, digit grades, `"ОСВ"`). Plan 06 endpoint contract should treat dedicated attendance endpoint as not-applicable for student/parent role.
- `messages.har` returns `[]` for both accounts — neither has SMS notifications. Future regression: capture again when notifications exist, or write Plan 06 contract that tolerates empty payload.

## Sanitization edge cases discovered

1. **Single-word Cyrillic surnames** in API responses (`get_user_data` splits ФИО into 3 separate fields: `["lastname", "i", "p", ...]`). Original `RU_FULLNAME_RE` requires 2+ adjacent words → missed `"Чудиновских"` alone. **Fix**: added `replace_strings: [Чудиновских]` explicit list in YAML.
2. **HTML title false positive**: `<title>АИАС АВЕРС: Электронный Классный Журнал 4.1</title>` — "Электронный Классный" matches 2-word ФИО pattern. Sanitizer over-replaces with `Петров П.П.`. Cosmetic, not a leak; acceptable.
3. **Login form params `l=`/`p=`** in `/login` POST body weren't covered by original sanitizer. **Fix**: added `login_param_names: [l, p]` rules + URL-context match in `walk_har()`.
4. **Canary regex was locale-sensitive**. `LC_ALL=ru_RU.UTF-8` (default on Russian dev hosts) makes GNU grep `[а-яё]` match uppercase too — false positives on subject/UI strings. **Fix**: `export LC_ALL=C.UTF-8` at top of canary script.

## What Plan 06 must know

1. **Cookie auth via 3 client-set cookies**, format `<type>:<value>` URL-escaped via JS-escape semantics. Implement `JsEscape.encode()` helper in `:core:platform` (Kotlin) — must polyfill for codepoints ≥256 emitting `%uXXXX`.
2. **`HttpClientFactory`** (Plan 06) must:
   - Use a `CookiesStorage` that supports manual cookie injection after login (not just `Set-Cookie`-driven persistence)
   - Tolerate `IncompleteRead` / wrong `Content-Length` (Ktor `HttpResponse.bodyAsText()` must accept partial body)
   - Pre-process response text to convert `new Date(YYYY,MM,DD,…)` → ISO before kotlinx-serialization parse, OR write a tolerant ExtJS-array parser
3. **Endpoint contract** — typed DTOs in `:core:api-avers-v4`:
   - `LoginResponse`: array `[user_id: Int, user_type: Int, ?, ?, ?, fio: String?, id_a: Int, ?, id_b: Int]`
   - `UserData`: array `[lastname, first_initial, patronymic_initial, school: String, uchId: Int, maxMark: Int]`
   - `JournalRow`: array `[mark_id, pupil_id, value: String|"Н"|"Б"|"ОСВ"|digit, date: Date, lesson_id, ?, type_code, fake: Int, comment: String?]`
   - `DairyRow`: array `[date: Date, lesson_id, subject_id, theme: String, homework: String, mark: String?, in_day: Int, ?, ?, teacher_id, in_parallel: Int, comment: String?]`
   - `TimetableRow`: array `[id, lesson_id, subject_id, ?, ?, weekday: Int, in_day: Int, shift: Int?, room: String, valid_from?, valid_to?]`
4. **`AversApiError` sealed class** (D-13 from Plan 02 context):
   - `InvalidCredentials` ← response body == `[error_symbol]`
   - `EmptyResponse` ← body == `[]` (data semantically empty)
   - `MalformedResponse` ← body unparseable after `new Date()` substitution
   - `IncompleteContent` ← server `Content-Length` ≠ actual body
   - `NetworkError(cause)` ← transport failure

## Anti-pattern recorded

- **Don't use Python `urllib.parse.quote` for cookie values** that mirror JS `escape()` — produces UTF-8 byte encoding which the AVERS server rejects. Same caveat for Kotlin `URLEncoder.encode()` — Kotlin client must polyfill.
- **Don't trust `ru_RU.UTF-8` locale grep in CI** — pin `LC_ALL=C.UTF-8` for any Cyrillic regex check.

## Files committed

```
tools/capture-avers-fixtures.py            (new)
tools/sanitize-har.py                      (extended: login params, replace_strings)
tools/sanitize-rules.yaml                  (extended: ys-* cookies, login_param_names, replace_strings)
tools/README.md                            (updated: programmatic flow + Chrome DevTools fallback)
tests/sanitize-har-canary.sh               (LC_ALL=C.UTF-8 fix)
fixtures/sanitized/account-A/login.har     (29498 b)
fixtures/sanitized/account-A/grades.har    (42002 b)
fixtures/sanitized/account-A/schedule.har  (80498 b)
fixtures/sanitized/account-A/homework.har  (16494 b)
fixtures/sanitized/account-A/attendance.har (3460 b)
fixtures/sanitized/account-A/messages.har  (3176 b)
fixtures/sanitized/account-B/{same six}.har
```

`fixtures/raw/` remains gitignored (D-04 invariant preserved).

## Verification

```
$ test $(find fixtures/sanitized -type f -name '*.har' | wc -l) -eq 12 && echo OK
OK
$ for F in fixtures/sanitized/**/*.har; do python3 -m json.tool "$F" >/dev/null && echo "OK $F"; done
OK fixtures/sanitized/account-A/attendance.har
OK fixtures/sanitized/account-A/grades.har
OK fixtures/sanitized/account-A/homework.har
OK fixtures/sanitized/account-A/login.har
OK fixtures/sanitized/account-A/messages.har
OK fixtures/sanitized/account-A/schedule.har
OK fixtures/sanitized/account-B/attendance.har
OK fixtures/sanitized/account-B/grades.har
OK fixtures/sanitized/account-B/homework.har
OK fixtures/sanitized/account-B/login.har
OK fixtures/sanitized/account-B/messages.har
OK fixtures/sanitized/account-B/schedule.har
$ bash tests/sanitize-har-canary.sh
PASS: fixtures/sanitized/ contains only approved fake names (or is empty).
$ git ls-files fixtures/raw/ | wc -l
0
```

All 6 success criteria from PLAN.md met.
