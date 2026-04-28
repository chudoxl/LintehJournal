---
phase: 01-foundation-compliance-infrastructure
fixed_at: 2026-04-28T00:00:00Z
review_path: .planning/phases/01-foundation-compliance-infrastructure/01-REVIEW.md
iteration: 1
findings_in_scope: 12
fixed: 11
skipped: 1
status: partial
---

# Phase 01: Code Review Fix Report

**Fixed at:** 2026-04-28
**Source review:** `.planning/phases/01-foundation-compliance-infrastructure/01-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope (BLOCKER + WARNING): 12
- Fixed: 11
- Skipped: 1 (deferred to Phase 5/6 per reviewer recommendation)

## Fixed Issues

### BL-01: BuildKonfig `IS_DEBUG = "true"` hard-coded

**Files modified:** `composeApp/build.gradle.kts`
**Commit:** `104a7f8`
**Applied fix:** Добавил `defaultConfigs("release") { buildConfigField(BOOLEAN, "IS_DEBUG", "false") }` блок после default `defaultConfigs { ... }`. BuildKonfig per-flavor override теперь даёт IS_DEBUG=false для release-сборок. Verified локально: `./gradlew :composeApp:generateBuildKonfig -Pbuildkonfig.flavor=release` генерирует `IS_DEBUG: Boolean = false`; default flavor (debug) сохраняет `IS_DEBUG = true`.

### BL-02: `androidApplicationContext` is `public lateinit var` global

**Files modified:** `core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt`, `composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainApplication.kt`
**Commit:** `71c1d3b`
**Applied fix:** Заменил top-level `lateinit var androidApplicationContext: Context` на private holder + одноразовый `initApplicationContext(...)` initializer. Public API теперь — только `initApplicationContext`; повторный вызов кидает IllegalStateException; чтение до init даёт явный error message. `MainApplication.onCreate` обновлён на `initApplicationContext(this)`. Verified: `./gradlew :core:platform:compileDebugKotlinAndroid + :composeApp:compileDebugKotlinAndroid + compileKotlinIosX64` — все green (expect/actual signature не изменился).

### BL-03: `Verify Xcode version` CI step informational only

**Files modified:** `.github/workflows/ci.yml`
**Commit:** `ca8111f`
**Applied fix:** Заменил `xcodebuild -version | head -1` (всегда exit 0) на bash-блок с `set -euo pipefail` + `grep -qE '^Xcode 16\.'` assertion. Теперь любой fallback к Xcode 15 / default toolchain делает CI red с явным сообщением "expected Xcode 16.x, got 'Xcode 15.x'". Pitfall #4 mitigation теперь сам себя закрывает. YAML validated.

### WR-01: Android `openUrl` без scheme allowlist

**Files modified:** `core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt`
**Commit:** `fe2fcdc`
**Applied fix:** Добавил `private val ALLOWED_SCHEMES = setOf("https", "http")` + `runCatching { Uri.parse(url) }.getOrNull() ?: return` (defensive parse) + `if (parsed.scheme?.lowercase() !in ALLOWED_SCHEMES) return` + `runCatching { ...startActivity(intent) }` (ActivityNotFoundException guard). Trust-boundary contract теперь параллелен iOS-actual'у (который уже defensive). Phase 4 deep-link / messages могут расширять allowlist explicitly. Verified: `./gradlew :core:platform:compileDebugKotlinAndroid` green.

### WR-02: Privacy Policy claims unimplemented features

**Files modified:** `docs/privacy/index.html`
**Commit:** `906dc59`
**Applied fix:** Добавил вводный paragraph "Замечание для версии 0.1.0: текущая сборка является технической foundation-версией без логин-flow, без БД, без cookies, без экрана настроек. Разделы ниже описывают целевую модель данных v1." Переименовал секцию "Что хранится на вашем устройстве" → "Что будет храниться (целевая модель v1)" с per-bullet phase-маркерами (Phase 2 / Phase 3) и явным "В версии 0.1.0 ничего из перечисленного пока не сохраняется". В разделе "Удаление данных" — переписал на future tense ("появится в Phase 4"); добавил указание "удалять нечего; удалите приложение стандартным способом ОС". HTML structure валидирована через html.parser.

### WR-03: `{{LAST_MODIFIED}}` placeholder visible in raw HTML view

**Files modified:** `docs/privacy/index.html`, `.github/workflows/pages.yml`
**Commit:** `037d79c`
**Applied fix:** Заменил литеральный `{{LAST_MODIFIED}}` на semantic sentinel span: `<span data-stamp="last-modified">текущая разработка (см. git history)</span>`. Pages workflow `sed` pattern обновлён на match content между открывающим/закрывающим тегом span'а. Raw view теперь показывает читаемый default text; deployed view — actual git-derived date. Verified: yaml.safe_load OK; offline sed dry-run produced expected output.

### WR-04: `versionCode` exec duplicated, not crash-safe

**Files modified:** `composeApp/build.gradle.kts`
**Commit:** `608d7a9`
**Applied fix:** Извлёк exec-блок в shared `val versionCodeProvider = providers.environmentVariable("GITHUB_RUN_NUMBER").orElse(...).map { it.toIntOrNull() ?: 1 }` у top-of-file (после plugins{}). Заменил оба call-site на `versionCodeProvider.get()` / `.get().toString()`. Дополнительно добавил `isIgnoreExitValue = true` для graceful degradation, и crash-safe `toIntOrNull() ?: 1` вместо `.toInt()`. Verified end-to-end: `./gradlew :composeApp:assembleDebug` → `aapt dump badging APK` показывает versionCode='50' (matches `git rev-list --count HEAD`); `generateBuildKonfig` показывает `VERSION_CODE = "50"`.

### WR-05: AppTest iOS↔Android body duplication

**Files modified:** `composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/AppTestHelpers.kt` (создан), `composeApp/src/iosTest/kotlin/io/github/chudoxl/linteh/journal/AppTest.kt`, `composeApp/src/androidUnitTest/kotlin/io/github/chudoxl/linteh/journal/AppTestAndroid.kt`, `composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/.gitkeep` (удалён)
**Commit:** `70b2d81`
**Applied fix:** Создал `AppTestHelpers.kt` в commonTest source set с `fun ComposeUiTest.assertHelloLintechRendered()` extension function (4 onNodeWithTag проверок). Оба теста (iOS-side `AppTest`, Android-side `AppTestAndroid`) теперь body-делегируют helper'у. Платформенные runners (`runComposeUiTest`, `RobolectricTestRunner`) сохранены — split нужен из-за Compose UI Test Android JVM NPE без Robolectric. Verified: compile both test source sets + testDebugUnitTest passes.

### WR-06: PrivacyInfo.xcprivacy lint brittle `<array` regex

**Files modified:** `.github/workflows/ci.yml`
**Commit:** `3981ae2`
**Applied fix:** Заменил `plutil -extract NSPrivacyCollectedDataTypes xml1 ... | grep -qE '<array(/| )'` (regex matched только `<array/>` и `<array `) на `plutil -convert json -o - "$PLIST" | jq -r ...` с двумя assertions: (1) `.NSPrivacyCollectedDataTypes | type` == "array", (2) `.NSPrivacyCollectedDataTypes | length` == "0". jq предустановлен на macos-15 runner. Robust к Apple plutil internal formatting changes. YAML validated.

### WR-07: No explicit policy on Compose UI test deps

**Files modified:** `composeApp/build.gradle.kts`
**Commit:** `d755276`
**Applied fix:** Добавил POLICY-comment в `commonTest.dependencies` блок: "НЕ добавлять androidx.compose.ui:ui-test-* напрямую — все Compose-test deps должны идти через compose.uiTest (CMP-managed) для version-alignment". Future maintainer увидит запрет до того, как добавит drift-prone dep. Версионная проверка в CI отложена в Phase 5/6 backlog (вместе с supply-chain SHA-pinning из WR-09). Verified: `./gradlew :composeApp:tasks` parses.

### WR-08: openUrl silent failures, no Kermit logger

**Files modified:** `core/platform/build.gradle.kts`, `core/platform/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.ios.kt`, `core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt`
**Commit:** `7792587`
**Applied fix:** Добавил `implementation(libs.kermit)` в `:core:platform` commonMain.dependencies. iOS UrlOpener: invalid URL (NSURL.URLWithString returns null) теперь логирует `Logger.w("UrlOpener") { "Invalid URL, skipping openUrl: $url" }`. Android UrlOpener: parse fail / scheme reject / startActivity exception — каждый путь логируется (включая stack trace на startActivity exception). Default Kermit config работает out-of-box — Logcat (Android), OSLog (iOS). Explicit `Logger.setMinSeverity(...)` init в MainApplication/MainViewController отложен в Phase 2 вместе с Ktor logging plugin setup. Verified: compile Android + iOS + testDebugUnitTest passes.

## Skipped Issues

### WR-09: GitHub Actions versions pinned by major-tag, not commit SHA

**File:** `.github/workflows/ci.yml:22, 25, 31`, `.github/workflows/pages.yml:27, 32, 51, 57`
**Reason:** Reviewer's own recommendation: "Не блокирует Phase 1, но добавить в Phase 5 (security hardening) backlog." Применение SHA-pins требует точных known-good commit hashes для каждого major-tag (`actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`, `actions/configure-pages@v5`, `actions/upload-pages-artifact@v3`, `actions/deploy-pages@v4`); неверный SHA сделает CI red. Правильное место для этого изменения — Phase 5/6 supply-chain hardening через Dependabot или [`pin-github-action`](https://github.com/mheap/pin-github-action) автоматизацию (которая разрешает SHAs из API и обновляет их при security-patch releases). Применять SHAs вручную в Phase 1 без автоматизации создаёт maintenance debt: каждое security-patch update требует manual re-pinning, что более рисково чем major-tag-pinning текущих action'ов от первоисточников (`actions/*` GitHub-owned, `gradle/actions/*` Gradle Inc).
**Original issue:** Major-tag pinning потенциально позволяет maintainer-compromise scenario, при котором malicious tag-update переуказал бы на vulnerable commit. В Phase 1 personal-use risk низкий (нет signing secrets, нет TestFlight upload).

## Out-of-Scope (Info findings — not addressed by `critical_warning` scope)

8 INFO findings (IN-01 — IN-08) сознательно вне scope этой fix-итерации. Все они либо документационные (IN-01: уточнить freeCompilerArgs comment; IN-07: explain config-cache disable), либо архитектурные (IN-02: split convention plugins; IN-03: Compose internal API), либо micro-optimizations (IN-08: combine Gradle commands в CI). Они переходят в Phase 5/6 backlog или будут адресованы естественно по мере эволюции codebase в Phase 2-4.

---

_Fixed: 2026-04-28_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
