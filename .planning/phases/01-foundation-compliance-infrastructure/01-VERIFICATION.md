---
phase: 01-foundation-compliance-infrastructure
verified: 2026-04-28T10:30:00Z
status: human_needed
score: 5/5 must-haves verified (code-complete); 2 ROADMAP success criteria require human GitHub UI confirmation
overrides_applied: 0
re_verification: null
gaps: []
human_verification:
  - test: "First green CI run on main (Android + iOS jobs both green)"
    expected: "GitHub Actions tab shows green status for both `Android (CI)` and `iOS (CI)` jobs after first push to main with .github/workflows/ci.yml"
    why_human: "CI execution requires GitHub-side workflow runners; Linux Mint dev-host cannot run macos-15 iOS job locally. Pre-merge verification on local machine confirmed compileKotlinIosX64 + compileTestKotlinIosX64 + assembleDebug + testDebugUnitTest all BUILD SUCCESSFUL."
  - test: "Enable GitHub Pages (Settings → Pages → Source: GitHub Actions)"
    expected: "GitHub repo settings show 'Your site is ready to be published at https://chudoxl.github.io/LintehJournal/'"
    why_human: "Manual GitHub UI step — cannot be automated from a workflow file (security policy). Owner-only action. Tracked in 01-HUMAN-UAT.md item 1."
  - test: "Configure main branch protection rule (Android + iOS required checks)"
    expected: "Settings → Branches shows protection rule for `main` requiring status checks `Android (CI)` and `iOS (CI)`, branches up-to-date, conversation resolution"
    why_human: "Manual GitHub UI step — owner-only action. Tracked in 01-HUMAN-UAT.md item 2."
  - test: "Privacy Policy URL HTTP 200 + content checks (after Pages enabled)"
    expected: "curl -I https://chudoxl.github.io/LintehJournal/privacy/ returns HTTP 200; page contains 'Политика конфиденциальности', 'journal.school28-kirov.ru', 'iOS Keychain, Android Keystore'; footer shows 'Последнее обновление: YYYY-MM-DD' (NOT literal placeholder)"
    why_human: "Live URL verification depends on GitHub Pages being enabled (manual prerequisite) + first pages.yml run executing successfully. Cannot verify without GitHub-side network access. Tracked in 01-HUMAN-UAT.md items 4-5."
  - test: "Hello LinTech Android UX smoke test on physical device or emulator"
    expected: "./gradlew :composeApp:installDebug installs APK; opening app shows 'ЛИнТех Дневник', version 'v0.1.0 (NN)', privacy URL, 'Открыть' button; tapping button launches system browser at the privacy URL"
    why_human: "Visual UX verification — requires running Android device/emulator and human eye to confirm rendering, kerning, button tap → browser launch. Compile, build and Robolectric-based AppTestAndroid (testDebugUnitTest) all PASS locally."
---

# Phase 1: Foundation & Compliance Infrastructure — Verification Report

**Phase Goal:** Гарантированно‑воспроизводимая сборка обеих платформ, готовая инфраструктура для всего последующего кода и опубликованные compliance-артефакты для personal-use distribution

**Verified:** 2026-04-28T10:30:00Z

**Status:** human_needed — все code-level must-haves закрыты; остались GitHub UI manual steps + browser-side UX smoke

**Re-verification:** No — initial verification

---

## Goal Achievement

### ROADMAP Success Criteria (Phase 1, 5 truths)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | CI собирает iOS и Android из коробки (assembleDebug + iosX64Test) — обе платформы зелёные на каждом коммите | VERIFIED (code-complete) — green CI run = HUMAN | `.github/workflows/ci.yml` declares Android job (ubuntu-latest: assembleDebug + lint + testDebugUnitTest) and iOS job (macos-15: iosX64Test + linkDebugFrameworkIosX64 + 5-step plutil-lint). YAML well-formed (yaml.safe_load OK). Local pre-merge: `./gradlew :composeApp:assembleDebug`, `:composeApp:compileKotlinIosX64`, `:composeApp:compileTestKotlinIosX64`, `:composeApp:testDebugUnitTest` — all BUILD SUCCESSFUL on Linux Mint. AppTestAndroid passes (TEST-AppTestAndroid.xml: tests=1, failures=0). First green CI run requires push to main + macos-15 runner — deferred to HUMAN-UAT. |
| 2 | Разработчик может открыть проект в Android Studio и запустить заглушку «Hello LinTech» на Android-устройстве/эмуляторе. iOS «Hello LinTech» валидируется через автоматический `iosX64Test` screenshot-test в CI на macos-15 runner-е (dev-host разработчика — Linux Mint) | VERIFIED (code-complete) — physical device tap = HUMAN | `composeApp/src/commonMain/kotlin/.../App.kt` (72 lines): `@Composable fun App()` renders MaterialTheme + Surface + Column with 4 tagged elements (`app_title`, `app_version`, `privacy_url`, `open_privacy_button`). Imports verified: `openUrl` from `core.platform`, `BuildKonfig.VERSION_NAME/CODE`, `Res.string.app_name/privacy_policy_url/open_button`. Generated BuildKonfig.kt: `VERSION_NAME = "0.1.0"`, `VERSION_CODE = "56"` (git rev-list count). Generated Compose Resources: `lintehjournal.composeapp.generated.resources.Res` — strings.xml has all 3 keys. Android entry chain: MainApplication.onCreate calls initApplicationContext(this) → MainActivity sets content { App() }. iOS entry: `MainViewController() = ComposeUIViewController { App() }`. AppTest.kt (iosTest) and AppTestAndroid.kt (androidUnitTest @RunWith(RobolectricTestRunner)) share assertHelloLintechRendered helper. AppTestAndroid PASS locally. |
| 3 | Privacy Policy опубликована на отдельном URL (GitHub Pages) и линк виден из проекта (README + строка ресурсов для будущего «О приложении») | VERIFIED (code-complete) — live URL HTTP 200 = HUMAN | `docs/privacy/index.html` (103 lines) — RU custom-written policy with 'Политика конфиденциальности' heading, 'journal.school28-kirov.ru' reference, 'iOS Keychain, Android Keystore' content, footer with `<span data-stamp="last-modified">` placeholder. `docs/index.html` — root landing with link to `./privacy/`. `.github/workflows/pages.yml` (60 lines) — actions/deploy-pages@v4 + actions/upload-pages-artifact@v3 (path: 'docs') + actions/configure-pages@v5 + Stamp Last-Modified date step (sed substitution from `git log -1 --format=%cs`) + checkout fetch-depth: 0. Privacy URL `https://chudoxl.github.io/LintehJournal/privacy/` referenced in (a) `composeApp/src/commonMain/composeResources/values/strings.xml` line 4 (`privacy_policy_url`), (b) `README.md` line 19, (c) `CLAUDE.md §Architecture` line 358. Live URL verification deferred to HUMAN-UAT items 4-5 (depends on Pages enable manual step). |
| 4 | iOS-сборка содержит `PrivacyInfo.xcprivacy` с required-reason API, `NSPrivacyTracking=false`, `NSPrivacyCollectedDataTypes=[]` — Xcode «Validate App» не выдаёт ITMS-91053; CI lint (`plutil -lint` + grep CA92.1, C617.1, NSPrivacyTracking=false) на macos-job не выдаёт ошибок на каждом коммите | VERIFIED (code-complete) — Xcode Validate App = Phase 6 | `composeApp/PrivacyInfo.xcprivacy` (32 lines) — XML well-formed (xml.etree.ElementTree.parse OK). Contains: NSPrivacyTracking=false, NSPrivacyTrackingDomains=empty array, NSPrivacyCollectedDataTypes=empty array, NSPrivacyAccessedAPITypes with NSPrivacyAccessedAPICategoryUserDefaults reason CA92.1 + NSPrivacyAccessedAPICategoryFileTimestamp reason C617.1. `composeApp/build.gradle.kts` lines 10, 90-94: applies `org.jetbrains.kotlin.apple-privacy-manifests` plugin via `alias(libs.plugins.applePrivacyManifests)`; `privacyManifest { embed(privacyManifest = layout.projectDirectory.file("PrivacyInfo.xcprivacy").asFile) }` block correctly placed inside `kotlin {}` scope. `.github/workflows/ci.yml` ios job lines 90-138: `linkDebugFrameworkIosX64` (verifies plugin packaging) + 5-step plutil-lint chain (syntax + CA92.1 + C617.1 + NSPrivacyTracking=false + NSPrivacyCollectedDataTypes empty via plutil JSON + jq). `./gradlew :composeApp:compileKotlinIosX64` BUILD SUCCESSFUL. Xcode Validate App in TestFlight upload deferred to Phase 6 (TestFlight = v2 milestone). |
| 5 | Convention plugins (`build-logic/`) применяются к фейковому модулю — добавление нового KMP-модуля займёт ≤5 строк build.gradle.kts | VERIFIED | `build-logic/convention/build.gradle.kts` registers 3 plugins via `gradlePlugin { plugins { register("lintechKmp"/lintechCompose/lintechTest") { id = "lintech-kmp"/-compose/-test" } } }`. Plugin classes implement `Plugin<Project>` and call `pluginManager.apply(...)` for KMP/Compose/test deps. Empirical proof: `core/platform/build.gradle.kts` plugins block = 4 lines (`plugins { id("lintech-kmp"); id("lintech-test"); }`); `core/network/build.gradle.kts` plugins block = 4 lines; `core/ui/build.gradle.kts` plugins block = 5 lines (id + lintech-compose + lintech-test). All 3 modules ≤5 lines plugins-block. `settings.gradle.kts` line 7: `includeBuild("build-logic")`. `./gradlew tasks --all` BUILD SUCCESSFUL — all 4 modules + build-logic registered correctly. |

**Score:** 5/5 ROADMAP success criteria **VERIFIED at code-complete level**. 2 of them have human-side validation tail (CI green run + live Pages URL + physical device tap).

### Per-Plan Truths Cross-check (key sample)

| Truth (Plan source) | Status | Evidence |
|---|--------|----------|
| `./gradlew tasks` exit 0 (Plan 01) | VERIFIED | Ran locally — BUILD SUCCESSFUL exit 0 |
| `./gradlew assembleDebug` succeeds for all 4 modules (Plan 01) | VERIFIED | `:composeApp:assembleDebug` BUILD SUCCESSFUL (90+ tasks, 7s warm) |
| `./gradlew compileKotlinIosX64` succeeds (Plan 01) | VERIFIED | BUILD SUCCESSFUL on Linux Mint without Xcode |
| Convention plugins registered + applied via id(...) (Plan 01) | VERIFIED | gradlePlugin block in build-logic/convention/build.gradle.kts; id("lintech-*") usage in core/* |
| `:core:*` hierarchical naming (Plan 01) | VERIFIED | settings.gradle.kts: `:core:platform`, `:core:ui`, `:core:network` |
| Hello LinTech screen shows app name + version + privacy URL + button (Plan 02) | VERIFIED | App.kt structurally renders all 4 elements with testTags |
| openUrl expect/actual delivered (Plan 02) | VERIFIED | UrlOpener.kt commonMain expect; Android Intent.ACTION_VIEW + scheme allowlist (WR-01); iOS UIApplication.openURL with NSURL defensive (WR-08 logging) |
| BuildKonfig generates VERSION_NAME/VERSION_CODE/IS_DEBUG (Plan 02) | VERIFIED | Generated BuildKonfig.kt confirmed: VERSION_NAME="0.1.0", VERSION_CODE="56", IS_DEBUG=true (debug); release flavor override gives IS_DEBUG=false (BL-01 fix) |
| AppTest passes via runComposeUiTest (Plan 02) | VERIFIED locally on Android JVM | testDebugUnitTest BUILD SUCCESSFUL; TEST-AppTestAndroid.xml: 1 test, 0 failures, 0 errors. iOS `iosX64Test` SKIPPED locally on Linux (expected per environment_constraints) — `compileTestKotlinIosX64` BUILD SUCCESSFUL |
| testTag selectors on 4 UI elements (Plan 02) | VERIFIED | App.kt lines 46/52/59/64 — `testTag("app_title"/"app_version"/"privacy_url"/"open_privacy_button")` |
| MainActivity + MainViewController wrap App() (Plan 02) | VERIFIED | MainActivity.kt: `setContent { App() }`; MainViewController.kt: `ComposeUIViewController { App() }` |
| ci.yml runs on push/PR to main with android+ios jobs (Plan 03) | VERIFIED | Triggers: push branches=[main] + pull_request branches=[main]. Two jobs (android: ubuntu-latest, ios: macos-15) parallel. concurrency block, permissions: contents: read |
| Android job: assembleDebug + lint + testDebugUnitTest (Plan 03) | VERIFIED | ci.yml lines 35-48 |
| iOS job: iosX64Test + linkDebugFrameworkIosX64 + plutil lint (Plan 03 + Plan 05) | VERIFIED | ci.yml lines 87-137 |
| README.md has CI badge + privacy URL + manual setup section (Plan 03 + 06) | VERIFIED | README.md lines 3 (badge), 19/29 (privacy URL), 96-117 (manual setup) |
| Privacy Policy HTML opening on /privacy/ (Plan 04) | VERIFIED (file-level) — live URL = HUMAN | docs/privacy/index.html exists, well-formed, 103 lines, RU custom on-device content |
| pages.yml deploys to GitHub Pages with auto Last-Modified (Plan 04) | VERIFIED (file-level) — first deploy = HUMAN | actions/deploy-pages@v4, fetch-depth=0, sed substitution; YAML well-formed |
| PrivacyInfo.xcprivacy contains required-reason API codes (Plan 05) | VERIFIED | XML well-formed; CA92.1, C617.1, NSPrivacyTracking=false, both empty arrays present |
| apple-privacy-manifests plugin embeds manifest (Plan 05) | VERIFIED | composeApp/build.gradle.kts plugin alias + `privacyManifest { embed(...) }` block inside kotlin{} scope; `compileKotlinIosX64` BUILD SUCCESSFUL after Rule 3 deviation fix |
| CI plutil-lint step on macos job (Plan 05) | VERIFIED | ci.yml ios job: `Lint PrivacyInfo.xcprivacy` step with set -euo pipefail + 5 substeps |
| ROADMAP success criterion #2 reflects Linux Mint dev-host (Plan 06) | VERIFIED | ROADMAP.md line 30: contains 'iosX64Test', 'macos-15', 'Linux Mint, локальный Xcode недоступен' |
| ROADMAP success criterion #4 mentions CI lint (Plan 06) | VERIFIED | ROADMAP.md line 32: contains 'CI lint (`plutil -lint` + grep CA92.1, C617.1, NSPrivacyTracking=false)' |
| CLAUDE.md §Conventions filled (Plan 06) | VERIFIED | CLAUDE.md lines 209-274: comprehensive Conventions section with convention plugin pattern, ≤5-line example, package root, expect/actual, testTag, build commands. TBD placeholder removed |
| CLAUDE.md §Architecture filled (Plan 06) | VERIFIED | CLAUDE.md lines 276-363: Multi-module layout ASCII tree, source set hierarchy with Robolectric notes, dependency rules D-07, SwiftPM (NOT CocoaPods), build infrastructure with all pinned versions, CI infrastructure, Privacy & compliance |
| README.md updated with Phase 1 COMPLETE + PrivacyInfo mention (Plan 06) | VERIFIED | README.md line 14: 'Phase 1 (Foundation & Compliance Infrastructure): COMPLETE'; line 34: PrivacyInfo.xcprivacy + apple-privacy-manifests plugin reference |
| 01-VALIDATION.md nyquist_compliant=true (Plan 06) | VERIFIED | VALIDATION.md frontmatter line 5: `nyquist_compliant: true`; status: approved; approved: 2026-04-28; all 13 task-rows ✅ |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `settings.gradle.kts` | Multi-module + includeBuild("build-logic") | VERIFIED | 4 modules included; pluginManagement.includeBuild("build-logic") line 7 |
| `build.gradle.kts` (root) | Plugin aliases with apply false | VERIFIED | Compiles fine via gradle tasks; Now in Android pattern |
| `gradle/libs.versions.toml` | Pinned Kotlin 2.2.20 + CMP 1.10.3 + AGP 8.7.3 + KSP + Mokkery 2.10.2 + applePrivacyManifests | VERIFIED | All required versions present; mokkery bumped 2.5.1→2.10.2 (Plan 02 deviation) |
| `gradle/wrapper/gradle-wrapper.{jar,properties}` | Gradle 8.13 wrapper | VERIFIED | `./gradlew --version` reports Gradle 8.13 with Kotlin 2.0.21 in kotlin-dsl |
| `build-logic/convention/build.gradle.kts` | Registers 3 plugins | VERIFIED | gradlePlugin block lines 20-35 — lintech-kmp, lintech-compose, lintech-test |
| `LintechKmpConventionPlugin.kt` | Applies kotlin.multiplatform + android.library + configures KMP | VERIFIED | 14 lines; pluginManager.apply(...) + configureKotlinMultiplatform() + configureAndroidLibrary() |
| `LintechComposeConventionPlugin.kt` | Applies compose plugins + adds compose deps to commonMain | VERIFIED | 25 lines; ComposePlugin.Dependencies binding |
| `LintechTestConventionPlugin.kt` | Applies dev.mokkery + adds kotlin.test/kotest/turbine to commonTest | VERIFIED | 25 lines; compose.uiTest deliberately NOT included (BLOCKER 1 mitigation) |
| `composeApp/build.gradle.kts` | Application module + alias plugins + buildkonfig + applePrivacyManifests | VERIFIED | 150 lines; all required plugin aliases + privacyManifest{} inside kotlin{} (Rule 3 fix) |
| `core/platform/build.gradle.kts` | KMP library skeleton with Kermit | VERIFIED | 35 lines; id("lintech-kmp") + id("lintech-test"); namespace io.github.chudoxl.linteh.journal.core.platform |
| `core/ui/build.gradle.kts` | KMP library + compose | VERIFIED | 22 lines; 3 plugins (kmp + compose + test) |
| `core/network/build.gradle.kts` | KMP library skeleton | VERIFIED | 21 lines |
| `composeApp/src/commonMain/kotlin/.../App.kt` | @Composable App() with 4 testTags | VERIFIED | 72 lines; openUrl + BuildKonfig + Res.string.* wired correctly |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | 3 keys: app_name, privacy_policy_url, open_button | VERIFIED | 7 lines; all 3 keys present; privacy URL = https://chudoxl.github.io/LintehJournal/privacy/ |
| `composeApp/src/androidMain/kotlin/.../MainActivity.kt` | ComponentActivity setContent { App() } | VERIFIED | 13 lines |
| `composeApp/src/androidMain/kotlin/.../MainApplication.kt` | Application class calling initApplicationContext | VERIFIED | 19 lines (BL-02 fix applied — private holder pattern) |
| `composeApp/src/androidMain/AndroidManifest.xml` | MainApplication + MainActivity LAUNCHER | VERIFIED | Manifest declares android:name=".MainApplication" + activity exported with LAUNCHER intent-filter |
| `composeApp/src/iosMain/kotlin/.../MainViewController.kt` | ComposeUIViewController factory | VERIFIED | 6 lines |
| `composeApp/src/iosTest/kotlin/.../AppTest.kt` | runComposeUiTest helloLintech_displaysAllElements | VERIFIED | 17 lines; calls assertHelloLintechRendered helper |
| `composeApp/src/androidUnitTest/kotlin/.../AppTestAndroid.kt` | @RunWith(RobolectricTestRunner) AppTest mirror | VERIFIED | 26 lines; PASS locally (TEST result XML: 1/0/0) |
| `composeApp/src/commonTest/kotlin/.../AppTestHelpers.kt` | Shared assertHelloRendered (WR-05 fix) | VERIFIED | 36 lines; ComposeUiTest.assertHelloLintechRendered with 4 onNodeWithTag(...).assertIsDisplayed() |
| `composeApp/src/debug/AndroidManifest.xml` | Activity declaration for Robolectric ActivityScenario | VERIFIED | Present (referenced in CLAUDE.md §Architecture) |
| `core/platform/src/commonMain/kotlin/.../UrlOpener.kt` | expect fun openUrl | VERIFIED | 16 lines; KDoc + expect signature |
| `core/platform/src/androidMain/kotlin/.../UrlOpener.android.kt` | actual via Intent.ACTION_VIEW + scheme allowlist | VERIFIED | 68 lines; private holder + initApplicationContext + ALLOWED_SCHEMES + Kermit logging (BL-02, WR-01, WR-08 fixes) |
| `core/platform/src/iosMain/kotlin/.../UrlOpener.ios.kt` | actual via UIApplication.sharedApplication.openURL | VERIFIED | 26 lines; defensive NSURL.URLWithString + Kermit warning on null URL (WR-08) |
| `composeApp/PrivacyInfo.xcprivacy` | Plist with CA92.1 + C617.1 | VERIFIED | 32 lines; XML well-formed; all required keys present |
| `.github/workflows/ci.yml` | Two parallel jobs Android + iOS, with PrivacyInfo lint | VERIFIED | 138 lines; YAML well-formed; concurrency, permissions, defensive Xcode 16 fallback + assertion (BL-03 fix), 5-step plutil chain with jq (WR-06 fix) |
| `.github/workflows/pages.yml` | actions/deploy-pages@v4 with Last-Modified stamping | VERIFIED | 60 lines; YAML well-formed; fetch-depth: 0; sed substitution targeting `<span data-stamp="last-modified">` (WR-03 fix) |
| `docs/privacy/index.html` | RU custom Privacy Policy with on-device semantics | VERIFIED | 103 lines; reflects v0.1.0 forward-promises (WR-02 fix); `chxevdev@gmail.com` as sole PII (intentional T-01-01) |
| `docs/index.html` | Root landing → /privacy/ | VERIFIED | 32 lines; link to ./privacy/ + repo link |
| `README.md` | Phase 1 COMPLETE status + CI badge + privacy URL + manual setup | VERIFIED | 130 lines; all required sections + Linux Mint dev-host note |
| `CLAUDE.md` (§Conventions + §Architecture) | Filled with Phase 1 patterns | VERIFIED | Both sections fully written with module structure, expect/actual, build infra, CI, privacy details. TBD placeholders removed |
| `.planning/ROADMAP.md` (success criteria #2 + #4) | Linux Mint dev-host + CI lint reflected | VERIFIED | Both edits present at ROADMAP.md lines 30 and 32 |
| `.planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md` | nyquist_compliant=true + 13 ✅ rows | VERIFIED | Frontmatter + Per-Task Verification Map confirm |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `settings.gradle.kts` | `build-logic/` | `pluginManagement.includeBuild` | WIRED | Line 7: `includeBuild("build-logic")` |
| `build-logic/convention/build.gradle.kts` | `LintechKmpConventionPlugin` | `gradlePlugin.plugins.register` with id="lintech-kmp" | WIRED | Lines 22-24 |
| `core/platform/build.gradle.kts` | `lintech-kmp` plugin | `id("lintech-kmp")` | WIRED | Line 2; verified by successful build of all 4 modules |
| `composeApp/build.gradle.kts` | `composeApp/PrivacyInfo.xcprivacy` | `privacyManifest { embed(...) }` inside kotlin{} | WIRED | Lines 90-94 |
| `.github/workflows/ci.yml` Android job | `./gradlew assembleDebug lint testDebugUnitTest` | run steps | WIRED | Lines 36, 39, 48 |
| `.github/workflows/ci.yml` iOS job | `./gradlew :composeApp:iosX64Test` + `linkDebugFrameworkIosX64` | run steps | WIRED | Lines 88, 98 |
| `.github/workflows/ci.yml` iOS job | `composeApp/PrivacyInfo.xcprivacy` | `plutil -lint` + grep CA92.1 + C617.1 | WIRED | Lines 100-137 |
| `.github/workflows/pages.yml` | `docs/` | `actions/upload-pages-artifact path: 'docs'` | WIRED | Lines 52-55 |
| `composeApp/.../App.kt` | `:core:platform openUrl` | import + button onClick | WIRED | Line 18 import; line 63 onClick = `{ openUrl(privacyUrl) }` |
| `composeApp/.../App.kt` | `BuildKonfig.VERSION_NAME / VERSION_CODE` | Compose Text | WIRED | Line 50 |
| `composeApp/.../App.kt` | `Res.string.privacy_policy_url` | stringResource(...) | WIRED | Line 55 |
| `composeApp/.../MainApplication.kt` | `initApplicationContext` (in core:platform) | Application.onCreate | WIRED | Lines 4 import + 16 `initApplicationContext(this)` |
| `composeApp/.../MainActivity.kt` | `App()` | `setContent { App() }` | WIRED | Line 10 |
| `composeApp/.../MainViewController.kt` | `App()` | `ComposeUIViewController { App() }` | WIRED | Line 6 |
| `docs/privacy/index.html` | on-device architecture text | policy mentions Keychain + Keystore + journal.school28-kirov.ru | WIRED | Lines 62-71 |
| `README.md` | Privacy URL | https://chudoxl.github.io/LintehJournal/privacy/ | WIRED | Lines 19, 29 |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | `privacy_policy_url` | Res.string.privacy_policy_url consumer in App.kt | WIRED | strings.xml line 4 ↔ App.kt line 55 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|---------------------|--------|
| `App.kt` Hello LinTech | `Res.string.app_name` | `strings.xml` "ЛИнТех Дневник" | YES (compile-time const string) | FLOWING |
| `App.kt` Hello LinTech | `Res.string.privacy_policy_url` | `strings.xml` "https://chudoxl.github.io/LintehJournal/privacy/" | YES | FLOWING |
| `App.kt` Hello LinTech | `Res.string.open_button` | `strings.xml` "Открыть" | YES | FLOWING |
| `App.kt` version display | `BuildKonfig.VERSION_NAME` / `VERSION_CODE` | Generated BuildKonfig.kt — VERSION_NAME="0.1.0" from gradle.properties; VERSION_CODE="56" from `git rev-list --count HEAD` (triple fallback chain) | YES (real build-time values) | FLOWING |
| `App.kt` button onClick | `openUrl(privacyUrl)` | core:platform UrlOpener.android.kt: validates scheme, calls `androidApplicationContext.startActivity(Intent.ACTION_VIEW)`; iOS: `UIApplication.sharedApplication.openURL` | YES — both actuals call platform APIs (NOT stubs) | FLOWING |
| `MainApplication.onCreate` | `initApplicationContext(this)` | Stores `applicationContext` into private holder; openUrl reads via `androidApplicationContext` getter | YES | FLOWING |
| `pages.yml` Last-Modified | `<span data-stamp="last-modified">` HTML | Stamp step: sed substitution from `git log -1 --format=%cs -- docs/privacy/index.html` (fetch-depth: 0 ensures full history) | YES (real git-derived date during deploy) | FLOWING (file-level); end-to-end live verification = HUMAN |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Gradle wrapper functional | `./gradlew --version` | Gradle 8.13, Kotlin 2.0.21 in kotlin-dsl, Daemon JVM 17 (Android Studio JBR) | PASS |
| All Gradle tasks resolvable | `./gradlew tasks --all` | BUILD SUCCESSFUL — composeApp + core:platform/ui/network tasks listed | PASS |
| Android assembleDebug builds | `./gradlew :composeApp:assembleDebug` | BUILD SUCCESSFUL (warm 7s) | PASS |
| iOS X64 Kotlin compiles | `./gradlew :composeApp:compileKotlinIosX64` | BUILD SUCCESSFUL | PASS |
| iOS X64 test compiles | `./gradlew :composeApp:compileTestKotlinIosX64` | BUILD SUCCESSFUL | PASS |
| Android JVM AppTestAndroid passes (Robolectric) | `./gradlew :composeApp:testDebugUnitTest` | BUILD SUCCESSFUL; TEST-AppTestAndroid.xml: tests=1, failures=0, errors=0 | PASS |
| BuildKonfig generation produces real values | `./gradlew :composeApp:generateBuildKonfig` + read BuildKonfig.kt | BuildKonfig.kt contains VERSION_NAME="0.1.0", VERSION_CODE="56", IS_DEBUG=true | PASS |
| Compose Resources Res class generated | `find ... commonResClass/.../Res.kt` | `lintehjournal.composeapp.generated.resources.Res` exists | PASS |
| PrivacyInfo XML well-formed | `python3 ET.parse(...)` | Exits 0 | PASS |
| ci.yml YAML well-formed | `python3 yaml.safe_load(...)` | Exits 0 | PASS |
| pages.yml YAML well-formed | `python3 yaml.safe_load(...)` | Exits 0 | PASS |
| iosX64Test runs | `./gradlew :composeApp:iosX64Test` | SKIP locally (Linux Mint = no macOS Simulator) — expected | SKIP (deferred to CI macos-15) |
| Xcode "Validate App" in TestFlight | n/a | Deferred to Phase 6 TestFlight upload | SKIP (out of Phase 1 scope) |

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|----------------|-------------|--------|----------|
| COMP-01 | 01-01, 01-02, 01-03, 01-04, 01-06 | Политика конфиденциальности опубликована на отдельном URL и доступна из приложения | SATISFIED (code-complete) — live URL = HUMAN | docs/privacy/index.html (RU custom policy) + docs/index.html (root landing) + .github/workflows/pages.yml (auto-deploy via deploy-pages@v4 + Last-Modified stamping). URL `https://chudoxl.github.io/LintehJournal/privacy/` referenced from strings.xml (App.kt button), README.md, CLAUDE.md §Architecture. Live URL HTTP 200 + visual check deferred to HUMAN-UAT items 4-6 (depends on owner-side Pages enable). |
| COMP-02 | 01-01, 01-05, 01-06 | iOS-сборка содержит PrivacyInfo.xcprivacy с required-reason API | SATISFIED | composeApp/PrivacyInfo.xcprivacy with NSPrivacyAccessedAPICategoryUserDefaults CA92.1, NSPrivacyAccessedAPICategoryFileTimestamp C617.1, NSPrivacyTracking=false, NSPrivacyCollectedDataTypes=[] (XML well-formed). Plugin org.jetbrains.kotlin.apple-privacy-manifests:1.0.0 applied via composeApp/build.gradle.kts privacyManifest{} block (inside kotlin{}). CI ios job has 5-step plutil-lint regression chain on each PR. Xcode "Validate App" TestFlight upload deferred to Phase 6 reactively. |

**Both Phase 1 requirements (COMP-01, COMP-02) are mapped per REQUIREMENTS.md traceability table line 181-182. No orphaned IDs detected — every PLAN-frontmatter requirement is covered.**

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `core/platform/src/commonMain/kotlin/.../UrlOpener.kt` | 10 | KDoc text "Phase 4 TODO" (security note about URL validation/scheme allowlist for user-input URLs) | Info (intentional documentation) | NOT a stub. The Android actual already has scheme allowlist + ActivityNotFoundException guard (WR-01 fix). The iOS actual has defensive NSURL.URLWithString check. The KDoc TODO is a forward-looking reminder for Phase 4 when user-input URLs become a concern (currently only compile-time const Privacy URL). No action required for Phase 1. |

No other TODO/FIXME/PLACEHOLDER/coming-soon markers found in any Phase 1 source file or workflow file. No empty handler stubs (onClick, onSubmit). No hardcoded empty data flowing to UI (App.kt renders real BuildKonfig + real strings.xml values).

### Human Verification Required

#### 1. First green CI run on main (Android + iOS jobs both green)

**Test:** After merging this branch into main, navigate to https://github.com/chudoxl/LintehJournal/actions and observe the `CI` workflow run.
**Expected:** Both jobs (Android on ubuntu-latest, iOS on macos-15) finish with green checkmarks. iOS job specifically must complete `Run iOS X64 tests`, `Link iOS X64 debug framework`, and `Lint PrivacyInfo.xcprivacy` (all 5 plutil sub-steps) without errors.
**Why human:** CI execution requires GitHub-side workflow runners. Linux Mint dev-host cannot run macos-15 iOS job locally. Pre-merge verification on local machine confirmed `compileKotlinIosX64`, `compileTestKotlinIosX64`, `assembleDebug`, `testDebugUnitTest` all BUILD SUCCESSFUL.

#### 2. Enable GitHub Pages

**Test:** Navigate to https://github.com/chudoxl/LintehJournal/settings/pages, select Source = "GitHub Actions".
**Expected:** Settings page shows "Your site is ready to be published at https://chudoxl.github.io/LintehJournal/".
**Why human:** Manual GitHub UI step — cannot be automated from a workflow file (security policy). Owner-only action. Tracked in 01-HUMAN-UAT.md item 1. Without this step, the first run of pages.yml will fail at the `Setup Pages` step ("Pages site not enabled").

#### 3. Configure main branch protection rule

**Test:** Settings → Branches → Branch protection rules → Add rule for `main`. Enable "Require status checks to pass before merging" with required checks `Android` (CI) + `iOS` (CI); enable "Require branches to be up to date" + "Require conversation resolution".
**Expected:** Branch protection rule shows in the list. PRs to main cannot merge without both CI jobs green.
**Why human:** Manual GitHub UI step — owner-only action. Tracked in 01-HUMAN-UAT.md item 2.

#### 4. Privacy Policy URL HTTP 200 + content checks

**Test:** After items 1-2 succeed and pages.yml first deploy is green, run:
```bash
curl -I https://chudoxl.github.io/LintehJournal/privacy/                              # expect HTTP/2 200
curl -sf https://chudoxl.github.io/LintehJournal/privacy/ | grep -q "Политика конфиденциальности"  # exit 0
curl -sf https://chudoxl.github.io/LintehJournal/privacy/ | grep -q "journal.school28-kirov.ru"   # exit 0
curl -sf https://chudoxl.github.io/LintehJournal/privacy/ | grep -q "iOS Keychain, Android Keystore"  # exit 0
curl -sf https://chudoxl.github.io/LintehJournal/privacy/ | grep -E "Последнее обновление: 20[0-9]{2}-[0-9]{2}-[0-9]{2}"  # exit 0
```
**Expected:** All five checks pass; footer shows actual git-stamped date (NOT literal `{{LAST_MODIFIED}}` or sentinel "текущая разработка...").
**Why human:** Live URL verification depends on Pages enabled (item 2) + pages.yml first run (item 1 trigger). Tracked in 01-HUMAN-UAT.md items 4-5.

#### 5. Hello LinTech Android UX smoke test

**Test:** Plug Android device or start emulator. Run `./gradlew :composeApp:installDebug` → open the installed app.
**Expected:**
- Screen shows "ЛИнТех Дневник" (large title, top center)
- Below: version line `v0.1.0 (NN)` (NN = build number from git rev-list)
- Below: privacy URL text `https://chudoxl.github.io/LintehJournal/privacy/`
- Below: button "Открыть"
- Tap button → system browser opens at the privacy URL
- Cyrillic readable; layout centered without clipping
**Why human:** Visual UX validation — requires running Android device/emulator and human eye to confirm visual correctness. Compile, build and Robolectric-based AppTestAndroid all PASS locally; iosX64Test PASS will be confirmed by CI item 1 above.

### Gaps Summary

**No code-level gaps found.** All 5 ROADMAP success criteria for Phase 1 are achieved at the code-complete level. All declared must-haves from each plan's frontmatter are verified against the actual codebase: artifacts exist with substantive content, all key links are wired, build succeeds, AppTestAndroid passes locally, generated artifacts (BuildKonfig.kt, Compose Resources Res.kt) contain real values flowing to UI.

The remaining items in `human_verification` are not code gaps — they are:
1. **CI pipeline activation:** requires push to main + GitHub Actions runner execution (deferred per environment_constraints — pre-merge local verification is the maximum reachable validation on Linux Mint).
2. **GitHub UI manual configuration:** owner-only Settings → Pages and Settings → Branches actions that cannot be automated from a workflow file (policy reason).
3. **Live URL HTTP verification:** depends on prerequisite GitHub Pages enable (item 2 above).
4. **Physical device UX smoke:** standard human-eye UX validation that is intentionally out-of-scope for any automated verification step in this phase plan; AppTestAndroid covers the structural rendering on Android JVM under Robolectric.

These items are explicitly tracked in `01-HUMAN-UAT.md` (items 1-6) per Phase 1 close-out plan and per environment_constraints in this verification request. They do not block the Phase 1 goal ("Гарантированно‑воспроизводимая сборка обеих платформ, готовая инфраструктура для всего последующего кода и опубликованные compliance-артефакты для personal-use distribution") — the codebase delivers a reproducible build, complete infrastructure, and committed compliance artifacts. The human items confirm distribution-side activation, not the goal itself.

### Notable Code Quality Improvements (Post-Initial-Plans, From REVIEW-FIX)

The following 11 improvements were committed after initial plans completed and improve robustness of Phase 1 artifacts (all verified to be in current HEAD):

- **BL-01:** BuildKonfig per-flavor IS_DEBUG=false for release (composeApp/build.gradle.kts lines 142-148)
- **BL-02:** Encapsulated androidApplicationContext via private holder + initApplicationContext (UrlOpener.android.kt lines 28-39 + MainApplication.kt line 16)
- **BL-03:** Verify Xcode version is now a real gate (ci.yml lines 75-80; grep -qE '^Xcode 16\.' assertion)
- **WR-01:** Android openUrl scheme allowlist + ActivityNotFoundException guard (UrlOpener.android.kt lines 50-67)
- **WR-02:** Privacy Policy text correctly marks unimplemented features as forward-promises (docs/privacy/index.html lines 44-48, 66, 84-86)
- **WR-03:** {{LAST_MODIFIED}} placeholder replaced with sentinel `<span data-stamp>` (pages.yml line 50; privacy/index.html line 98)
- **WR-04:** Single shared versionCodeProvider (composeApp/build.gradle.kts lines 19-26) — dedupes versionCode + VERSION_CODE, crash-safe via toIntOrNull()
- **WR-05:** AppTest body extracted to commonTest helper (AppTestHelpers.kt) — eliminates iOS↔Android drift
- **WR-06:** plutil JSON + jq replaces brittle `<array(/| )` regex for empty-array check (ci.yml lines 124-135)
- **WR-07:** Explicit policy banning direct androidx.compose.ui:ui-test-* deps (composeApp/build.gradle.kts comment lines 59-63)
- **WR-08:** Kermit logging for openUrl silent skips on iOS and Android (UrlOpener.{android,ios}.kt)

These reflect strong code review hygiene and active iteration before verification — the codebase is in better shape than the initial SUMMARY snapshots described.

---

*Verified: 2026-04-28T10:30:00Z*
*Verifier: Claude (gsd-verifier)*
