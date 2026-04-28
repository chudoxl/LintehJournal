---
phase: 2
slug: api-reverse-engineering-network-layer
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-28
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | kotlin.test + kotest 5.9 assertions + Turbine 1.2 + Mokkery + Ktor MockEngine |
| **Config file** | `gradle/libs.versions.toml`, `core/{network,database,api-avers-v4}/build.gradle.kts` |
| **Quick run command** | `./gradlew :core:network:test :core:database:test :core:api-avers-v4:test` |
| **Full suite command** | `./gradlew test :core:network:iosX64Test :core:database:iosX64Test :core:api-avers-v4:iosX64Test lint` |
| **Estimated runtime** | ~120 seconds (Android JVM) + ~180 seconds (iOS Native, macos-15 only) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :core:<module>:test` (Android JVM only — fast feedback ≤30s)
- **After every plan wave:** Run `./gradlew test lint` (Android full suite + log-redactor canary + sanitize-har canary)
- **Before `/gsd-verify-work`:** Full suite green on **both** Android (ubuntu-latest) and iOS (macos-15 CI job)
- **Max feedback latency:** ≤30 seconds local (Android JVM under Robolectric); CI iOS Native ~6min cold, ~3min cached

---

## Per-Task Verification Map

> Filled by planner during plan generation. Each task references its plan + wave + automated verify command.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-XX-XX | XX | N | infrastructure | T-02-NN | (planner fills) | unit / integration / canary | `./gradlew :core:<m>:test --tests <pattern>` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Wave 0 (test infrastructure scaffolding) MUST exist before any endpoint or storage code lands. Researcher итемизировал 14 test files + 2 CI canary scripts + 4 doc skeletons:

### Test files (commonTest in 3 new modules)

- [ ] `core/database/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/database/CookieDaoTest.kt` — Room DAO contract (in-memory builder)
- [ ] `core/database/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/database/SchemaV1Test.kt` — schema fields, indices, expiry filter logic
- [ ] `core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/HttpClientFactoryTest.kt` — plugin chain assembly + cached per-account
- [ ] `core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/RoomCookiesStorageTest.kt` — Ktor CookiesStorage contract via Room DAO
- [ ] `core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/HttpRequestRedactorTest.kt` — body mask password/pwd/cookie/authorization/token (regression)
- [ ] `core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/SanitizeHeaderTest.kt` — Authorization/Cookie/Set-Cookie redaction
- [ ] `core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/AversAuthInterceptorTest.kt` — D-26 auto re-login (401 → CredentialProvider → retry)
- [ ] `core/network/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/network/AccountDataPurgerTest.kt` — D-22 cookies wipe + DB delete
- [ ] `core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/EndpointsContractTest.kt` — 6 endpoints × HAR-fixture replay через MockEngine
- [ ] `core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/ApiResultTest.kt` — sealed Success / Mismatch / Failure
- [ ] `core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/AversApiErrorTest.kt` — sealed exhaustive when
- [ ] `core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/AntiBotDetectorTest.kt` — HTML CAPTCHA detection
- [ ] `core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/KillSwitchClientTest.kt` — fetch + parse + fail-open
- [ ] `core/api-avers-v4/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/core/api/avers/v4/ExtJsEnvelopeTest.kt` — `{success, data}` vs прямой ответ unwrapper

### CI canary scripts

- [ ] `tests/log-redactor-canary.sh` — `kanareyka_PASSWORD_DO_NOT_LEAK_42` зашит в test request → запустить `./gradlew test` debug+release → grep build output → exit 1 если найдено
- [ ] `tests/sanitize-har-canary.sh` — grep PII canary names (Иванов, Петров, Сидоров placeholder ≠ реальные) в `fixtures/sanitized/` → exit 1 если найдено что-то выглядящее как реальная фамилия

### Documentation skeletons

- [ ] `docs/aversApiV4_23813.md` — login flow + endpoint map + ExtJS envelope rules + anti-bot thresholds (Phase 2 success criterion #2)
- [ ] `docs/aversApiV4_23813-CHANGELOG.md` — diff vs предыдущим build (для будущих parallel модулей)
- [ ] `tools/sanitize-har.py` — Python script (PII regex → fakes, randomize cookie values, preserve grades)
- [ ] `tools/sanitize-har.README.md` — usage, env vars, dotenv format

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real login против `journal.school28-kirov.ru` | Success #3 (Ktor login без CAPTCHA) | D-25: live запросы запрещены в CI | Локально на dev-host (Linux Mint): `./tools/manual-smoke.sh --account=child1` — single login + grades fetch, проверить нет CAPTCHA в первые 5 попыток |
| Mobile UA divergence | D-02 risk | Phase 4 — первый Android `installDebug` сделает реальный запрос | Phase 4 plan содержит UAT-задачу: `./gradlew :composeApp:installDebug` → manual launch на Android-телефоне разработчика → захватить новый HAR через mitmproxy iOS proxy → diff с desktop HAR |
| Anti-bot empirical thresholds | D-05 пассивное наблюдение | Целенаправленное triggering запрещено (уважение к серверу школы) | Manual log fields в `aversApiV4_23813.md` если что-то новое замечено в captures |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (14 test files + 2 CI canary scripts + 4 doc skeletons)
- [ ] No watch-mode flags (CI deterministic, single-shot)
- [ ] Feedback latency < 30s local Android JVM
- [ ] iOS Native test invocations добавлены в `.github/workflows/ci.yml` macos-15 job (`:core:network:iosX64Test`, `:core:database:iosX64Test`, `:core:api-avers-v4:iosX64Test`)
- [ ] log-redactor-canary + sanitize-har-canary интегрированы в CI Android job (ubuntu-latest)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
