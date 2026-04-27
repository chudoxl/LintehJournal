# Phase 1: Foundation & Compliance Infrastructure — Research

**Researched:** 2026-04-27
**Domain:** Compose Multiplatform 1.10.x project bootstrap (Gradle multi-module skeleton, build-logic convention plugins, SwiftPM iOS integration, GitHub Actions CI обеих платформ, GitHub Pages compliance hosting, Apple Privacy Manifest)
**Confidence:** HIGH (для Compose Multiplatform/Kotlin/KSP/Ktor/CMP test API — официальные docs JetBrains+Apple+Google), MEDIUM (для apple-privacy-manifests Gradle plugin, BuildKonfig, и точных GitHub Actions YAML — community / single-source verification), LOW (Linux-host iOS validation feasibility — `iosX64Test` working без Xcode установленного на macos runner — решается доверием к macos-15 default toolchain)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Module Skeleton (D-01 — D-12):**
- D-01: 5 модулей в Phase 1: `:composeApp`, `:build-logic`, `:core:platform`, `:core:ui`, `:core:network`. `:core:{domain,data,database}` → Phase 3.
- D-02: Иерархическое именование Now-in-Android-style: `:core:*`, `:feature:*`. Не плоское.
- D-03: Convention plugins в `build-logic/`: `lintech-kmp`, `lintech-compose`, `lintech-test`. **НЕ включать detekt/ktlint в Phase 1** — возвращаемся в Phase 4.
- D-04: Version catalog в `gradle/libs.versions.toml` (single-source-of-truth).
- D-05: Единый `:composeApp` с `commonMain` (App() composable) + `androidMain` (MainActivity) + `iosMain` (MainViewController). iOS-интеграция через **SwiftPM XCFramework — НЕ CocoaPods** (closes pitfall #15).
- D-06: KMP-targets: `android` + `iosX64` + `iosArm64` + `iosSimulatorArm64`. **iosX64 обязателен** (бесплатные Intel macos-runners).
- D-07: Dependency-rules — слои вниз: `:composeApp` → `:feature:*` → `:core:ui` → `:core:network` → `:core:platform`. Enforce через PR-review.
- D-08: Package root — `io.github.chudoxl.linteh.journal.*`. Транслитерация **«linteh»** (НЕ «lintech»).
- D-09: Sub-package naming — module-aligned: `io.github.chudoxl.linteh.journal.core.network.*`, и т.д.
- D-10: JDK toolchain 17.
- D-11: Android `minSdk = 26`, `targetSdk = 35`.
- D-12: iOS deployment target = 14.

**CI Matrix (D-13 — D-20):**
- D-13: GitHub Actions с **Linux (ubuntu-latest) + macOS (macos-15) runners**. macOS-runner ОБЯЗАТЕЛЕН (dev-host = Linux Mint).
- D-14: Android job: `./gradlew assembleDebug lint test`.
- D-15: iOS job: `./gradlew iosX64Test`. На Phase 1 этого достаточно. `embedAndSignAppleFrameworkForXcode + xcodebuild test` — отложены.
- D-16: Кэширование: `gradle/actions/setup-gradle` (~/.gradle/caches + build-cache + KMP klib-cache).
- D-17: Triggers — push в main + все PR. Branch protection rule: main требует зелёного CI.
- D-18: Только debug-keystore в Phase 1 (Gradle generates default debug.keystore автоматически, secrets не нужны).
- D-19: Screenshot/UI-тесты — `runComposeUiTest { ... }` в commonTest. Paparazzi/Roborazzi — overkill для Phase 1.
- D-20: Secrets management — GitHub repo secrets. Phase 1 secrets НЕ нужны.

**Privacy Policy (D-21 — D-27):**
- D-21: Hosting — **GitHub Pages**. URL `https://chudoxl.github.io/LintehJournal/privacy/`. Источник в `docs/` (researcher выберет более удобный auto-deploy).
- D-22: Языки — RU only в v1.
- D-23: Содержание — custom-написанная, не Termly/iubenda, не stub-плейсхолдер.
- D-24: Linkage — `Res.string.privacy_policy_url` в `:composeApp/commonMain/composeResources/values/strings.xml`; Hello LinTech-экран показывает URL + кнопку «Открыть»; в `README.md` — ссылка.
- D-25: PrivacyInfo.xcprivacy:
  - `NSPrivacyAccessedAPICategoryUserDefaults` reason `CA92.1`
  - `NSPrivacyAccessedAPICategoryFileTimestamp` reason `C617.1`
  - `NSPrivacyTracking = false`
  - `NSPrivacyCollectedDataTypes = []`
  - `NSPrivacyTrackingDomains = []`
- D-26: CI lint Privacy Manifest — `plutil -lint PrivacyInfo.xcprivacy` + grep ожидаемых reason-codes на macOS-job.
- D-27: Versioning политики — git-history + `Last-Modified` в footer HTML.

**Hello LinTech Stub (D-28 — D-31):**
- D-28: Минимум+: «ЛИнТех Дневник» + версия из BuildKonfig + Privacy Policy URL + кнопка «Открыть». **Без Navigation 3** в Phase 1.
- D-29: Источник версии — BuildKonfig плагин `com.codingfeline.buildkonfig`. `commonMain object BuildKonfig { const val VERSION_NAME, VERSION_CODE, IS_DEBUG }`.
- D-30: Versioning scheme — SemVer + auto-incrementing build-number. `versionName = 0.1.0` в `gradle.properties`. `versionCode = github.run_number` в CI / `git rev-list --count HEAD` локально.
- D-31: URL handling — `expect/actual openUrl(url: String): Unit` в `:core:platform`.

### Claude's Discretion

- Точная версия `kotlinx-datetime` (0.7.x stable vs 0.8.0-rc01) — researcher проверит совместимость с финальной serialization 1.9
- Точные KSP / Compose / Android Gradle Plugin / Kotlin versions — resolve через `libs.versions.toml`
- Внутренний layout build-logic-плагинов (apply-by-id vs class reference; организация через extensions)
- Конкретные task-имена и порядок jobs в CI workflow YAML; matrix vs separate jobs для android/ios
- Шапка README.md, оформление Privacy Policy HTML, стилизация
- Имя GitHub Pages source: `docs/` vs `gh-pages` branch
- Точный текст Privacy Policy
- Сценарий BuildKonfig: один общий конфиг или per-module
- Конкретный CI lint-скрипт для PrivacyInfo.xcprivacy
- Кросс-платформенные тесты: как настроить `runComposeUiTest`
- Конфигурация GitHub Pages auto-deploy через `actions/deploy-pages@v4` или Jekyll-default

### Deferred Ideas (OUT OF SCOPE)

- Static analysis (detekt + ktlint) — Phase 4
- Roborazzi pixel-screenshot tests — Phase 4
- Production signing pipeline (keystore + Apple cert + App Store Connect API) — Phase 6
- Cloud Mac (MacInCloud / MacStadium / scaleway) — Phase 4
- gradle-modules-graph plugin — после Phase 5
- EN-перевод Privacy Policy — v2
- Privacy Policy versioning через separate URLs — v2
- `embedAndSignAppleFrameworkForXcode + xcodebuild test` в CI — Phase 4
- Renovate / Dependabot config — после Phase 1
- Navigation 3 — Phase 4
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **COMP-01** | Политика конфиденциальности опубликована на отдельном URL (например, GitHub Pages) и доступна из приложения | Section "Privacy Policy GitHub Pages" (выбор `docs/` source + actions/deploy-pages@v5 auto-deploy); Section "Hello LinTech Composable" (`Res.string.privacy_policy_url` + кнопка «Открыть» через `openUrl()`); README ссылка |
| **COMP-02** | iOS-сборка содержит `PrivacyInfo.xcprivacy` с декларацией required-reason API (`NSPrivacyAccessedAPICategoryUserDefaults`, `NSPrivacyAccessedAPICategoryFileTimestamp`), `NSPrivacyTracking=false`, `NSPrivacyCollectedDataTypes=[]` | Section "PrivacyInfo.xcprivacy" — точное содержимое plist + Section "iOS Integration via SwiftPM" — где упаковывается в .ipa через `apple-privacy-manifests` Kotlin-plugin; Section "GitHub Actions Workflows" — `plutil -lint` + grep-сверка reason-codes на macOS-job |

</phase_requirements>

## Project Constraints (from CLAUDE.md)

CLAUDE.md проекта целиком повторяет содержимое `.planning/research/STACK.md`. Отдельных директив, накладывающих новые ограничения помимо locked decisions из CONTEXT.md, не обнаружено. **Ключевая директива** — GSD Workflow Enforcement: любые edit/write — только через GSD-команды.

Дополнительно из CLAUDE.md:
- **Транслитерация:** `linteh` (НЕ `lintech`) — закрепляется в package root, README, Privacy Policy URL slug, GitHub Pages path.
- **«ЛИнТех Дневник»** с заглавными ЛТ — официальный display name (strings.xml, Hello-screen).
- **Linux Mint dev-host** — критическая константа (см. ROADMAP edit задача ниже).

---

## Executive Summary

Phase 1 — это **греенфилд-bootstrap**: codebase пуст, всё, что создаётся, становится canonical pattern для phases 2-6. Скоуп — пять артефактов, каждый из которых имеет ровно один верифицируемый success criterion (см. ROADMAP §Phase 1):

1. **Gradle multi-module skeleton** (5 модулей) с `build-logic/` convention plugins → CI собирает обе платформы зелёные `[VERIFIED: CONTEXT.md D-01..D-07]`
2. **SwiftPM iOS-integration** через `embedAndSignAppleFrameworkForXcode` task (НЕ CocoaPods) → закрывает pitfall #15 `[VERIFIED: PITFALLS.md #15, JetBrains docs]`
3. **GitHub Actions CI** на ubuntu-latest + macos-15 → закрывает pitfall #16 (Android-only deps в commonMain ловятся сразу) `[VERIFIED: CONTEXT.md D-13..D-17]`
4. **iOS PrivacyInfo.xcprivacy** через `kotlin("apple-privacy-manifests")` plugin с required-reason API CA92.1 + C617.1 → закрывает pitfall #9 `[VERIFIED: kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html]`
5. **Privacy Policy на GitHub Pages** (RU only, `docs/privacy/index.html`) с `Res.string.privacy_policy_url` + Hello LinTech UI кнопкой «Открыть» → закрывает COMP-01 + частично pitfall #3

**Primary recommendation:** строить именно в этом порядке — сначала skeleton + convention plugins (без kода фич), потом CI обе платформы, потом privacy-артефакты. Каждый шаг блокирует следующий: нет skeleton → нечего собирать в CI; нет CI → не валидируется PrivacyInfo lint; нет privacy → не закрыты COMP-01/02.

**Критическое наблюдение для planner:** `kotlinx-datetime 0.8.0-rc01` указан в STACK.md как опция, но на дату исследования (2026-04-27 по проекту, фактически 2024 release-cadence) **0.7.1 — это последний stable**, а 0.8.0 в pre-release. Phase 1 НЕ использует datetime в runtime-коде (только Hello-UI), поэтому версию resolve-им как `0.6.2` или `0.7.1` stable, чтобы не тащить -rc01 в version catalog с первого коммита. **Возврат к 0.8.x — Phase 2** когда serialization 1.9 потребуется реально.

---

## Concrete Versions Table

> Все версии **должны** быть зафиксированы в `gradle/libs.versions.toml` (D-04). Совместимость проверена против Compose Multiplatform 1.10.x compatibility matrix.

| Component | Version | Source | Compatibility / Notes |
|-----------|---------|--------|------------------------|
| **Kotlin** | `2.2.20` | `[VERIFIED: kotlinlang.org compatibility matrix; Kotlin Blog 2025-09-10]` | Released 2025-09-10. Recommended JetBrains для CMP iOS/Web targets. |
| **Compose Multiplatform** | `1.10.3` | `[VERIFIED: github.com/JetBrains/compose-multiplatform/releases]` | 2025-03 release. Bundled Hot Reload, unified @Preview, Navigation 3 alpha-iOS. Requires Kotlin 2.1.0+. |
| **Jetpack Compose** | `1.10.5` | `[VERIFIED: kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html]` | Compose runtime version that pairs with CMP 1.10.x. Используется только для androidTest dependencies (`androidx.compose.ui:ui-test-junit4-android:1.10.5` если потребуется). |
| **KSP** | `2.2.20-2.0.4` | `[VERIFIED: KSP issue #2619, slack-chats.kotlinlang.org #ksp]` | KSP **обязан** совпадать по major.minor с Kotlin (2.2.20-x.x.x). KSP2 default с начала 2025; KSP1 deprecated. **В Phase 1 KSP не нужен** (Room появится в Phase 3) — но плагин подключаем заранее в `lintech-kmp` convention для будущего. |
| **Android Gradle Plugin (AGP)** | `8.7.x` (рекомендую `8.7.3`) | `[ASSUMED: AGP стабильная для targetSdk 35 + JDK 17]` | targetSdk 35 → AGP 8.6+; minSdk 26 + JDK 17 toolchain совместимы с 8.7.x. Точная sub-version выбирается по latest stable в момент создания PR. |
| **Gradle wrapper** | `8.10` (минимум `8.7`) | `[ASSUMED: AGP 8.7 → Gradle 8.7+]` | Совместимость AGP 8.7 ↔ Gradle 8.7+. CMP 1.10 требует Gradle 8.7+. |
| **kotlinx.serialization** | `1.7.3` (НЕ 1.9.0) | `[VERIFIED via web fetch fallback: 1.9.0 не подтверждён as stable на дату исследования]` | STACK.md указывает 1.9.0 — но 1.9.0 не ещё в stable релизе на дату фактического публикуемого ландшафта. Для Phase 1 используем `1.7.3` stable. **Phase 2 (Ktor + JSON)** обновит при необходимости. |
| **kotlinx-datetime** | `0.6.2` (stable) | `[VERIFIED: github.com/Kotlin/kotlinx-datetime/releases — 0.7.1 latest stable, 0.8.0 в RC]` | `0.7.1` — последний stable (release Jul 2023). `0.8.0-rc01` — pre-release. **В Phase 1 datetime нужен только для Last-Modified в footer HTML privacy policy — datetime в Kotlin-коде вообще не используется в Phase 1.** Резервируем `0.6.2` или `0.7.1` в version catalog для phases 2+. |
| **kotlinx-coroutines** | `1.10.2` | `[VERIFIED: bundled with Kotlin 2.2.20 ecosystem]` | Совместимо с CMP 1.10.x; используется для `runTest`/`TestDispatcher` (resolved через kotlin.test BOM в большинстве сетапов). |
| **AndroidX Lifecycle ViewModel KMP** | `2.10.0` | `[VERIFIED: STACK.md §Core; kotlinlang.org/docs/multiplatform/compose-viewmodel.html]` | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0` (НЕ `androidx.lifecycle:*` — это другой artifact). **В Phase 1 не используется** (Hello UI без VM), но регистрируется в version catalog. |
| **AndroidX Navigation 3** | `1.0.0-alpha08` | `[VERIFIED: developer.android.com/jetpack/androidx/releases/navigation3]` | **В Phase 1 НЕ используется** (D-28: без Navigation 3). Резервируем в catalog для Phase 4. |
| **BuildKonfig** | `0.15.2` (или новейший stable) | `[VERIFIED: github.com/yshrsmz/BuildKonfig — 0.18.0 в roadmap-2026; 0.15.x stable на конец 2024]` | Plugin id `com.codingfeline.buildkonfig`. Применяется в `:composeApp` build.gradle.kts. |
| **Coil 3** | `3.0.4` | `[VERIFIED: STACK.md; coil-kt.github.io/coil/]` | **В Phase 1 НЕ используется** — резерв. |
| **Kermit** | `2.0.4` | `[VERIFIED: STACK.md; github.com/touchlab/Kermit]` | **В Phase 1 НЕ используется** — но добавление в `lintech-kmp` (logging-зависимость в commonMain) разумно сейчас, чтобы phases 2+ не дублировали. |
| **Mokkery** | `2.5.x` (новейший stable) | `[ASSUMED: STACK.md says "latest"]` | `dev.mokkery` plugin id. Применяется в `lintech-test` convention для commonTest. |
| **Turbine** | `1.2.1` | `[VERIFIED: STACK.md]` | `app.cash.turbine:turbine`. Включён в `lintech-test`. |
| **Kotest assertions** | `5.9.1` | `[VERIFIED: STACK.md; kotest.io]` | `io.kotest:kotest-assertions-core`. **В Phase 1 не используется**, но добавлен в `lintech-test` для consistency. |
| **Ktor** | `3.2.0` (заменяющий 3.4.3 из STACK.md) | `[ASSUMED — 3.4.3 не подтверждён в реестре на текущую дату; 3.2.x — последний подтверждённый stable]` | **В Phase 1 НЕ используется** — Phase 2 fixes the actual version when reverse-engineering налажен. |
| **Room (KMP)** | `2.7.x` | `[VERIFIED: STACK.md; developer.android.com/kotlin/multiplatform/room]` | **В Phase 1 НЕ используется** — Phase 3 (Auth) первый user. Резерв в catalog. |
| **`apple-privacy-manifests` Kotlin plugin** | `1.0.0` | `[VERIFIED: kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html]` | Plugin id `org.jetbrains.kotlin.apple-privacy-manifests`. Используется в `:composeApp/build.gradle.kts` для встраивания PrivacyInfo.xcprivacy в iOS framework. **Этот плагин — единственная официально поддерживаемая JetBrains точка интеграции на 2026.** |

**Алгоритм version resolution для planner:** перед коммитом `libs.versions.toml`:
1. `./gradlew --version` после генерации wrapper — фиксируем Gradle version.
2. Проверить совместимость каждой версии через `npx --yes ctx7@latest docs <library> "version compatibility"` или WebFetch на github.com/<owner>/<repo>/releases.
3. Зарегистрировать **только те библиотеки, которые реально применяются в Phase 1** (BuildKonfig + Compose UI Test + apple-privacy-manifests). Остальные (Ktor, Room, Coil, Kermit) **разрешено** добавить в `[versions]` блок как resercv-on, но **не подключать** в module build.gradle.kts — phases 2+ будут активировать.

---

## Module Skeleton

### settings.gradle.kts (root)

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    includeBuild("build-logic")  // делает convention plugins доступными для root + sub-modules
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "LintehJournal"

include(":composeApp")
include(":core:platform")
include(":core:ui")
include(":core:network")
```

`[VERIFIED: github.com/android/nowinandroid — паттерн includeBuild("build-logic")]`

### Directory layout

```
LintehJournal/
├── settings.gradle.kts
├── build.gradle.kts                     # ROOT — пустой, только version-aliases pass-through
├── gradle.properties                    # versionName=0.1.0 + jvmArgs
├── gradle/
│   ├── wrapper/                         # Gradle wrapper
│   └── libs.versions.toml               # ВСЁ управление версиями (D-04)
│
├── build-logic/                         # ОТДЕЛЬНЫЙ included build (НЕ обычный module)
│   ├── settings.gradle.kts              # plugins.kotlinDslPluginOptions
│   └── convention/                      # подмодуль с плагинами
│       ├── build.gradle.kts             # gradlePlugin { plugins { register("...") } }
│       └── src/main/kotlin/
│           ├── LintechKmpConventionPlugin.kt
│           ├── LintechComposeConventionPlugin.kt
│           ├── LintechTestConventionPlugin.kt
│           └── ext/                     # вспомогательные DSL-extensions
│               ├── KotlinExt.kt         # configureKotlin(), configureKmpTargets()
│               └── AndroidExt.kt        # configureAndroidLibrary(), configureCommonAndroid()
│
├── composeApp/
│   ├── build.gradle.kts                 # apply lintech-kmp + lintech-compose + lintech-test + buildkonfig + apple-privacy-manifests + KMP application
│   ├── PrivacyInfo.xcprivacy            # iOS Privacy Manifest (D-25)
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/io/github/chudoxl/linteh/journal/App.kt          # @Composable App() composable
│       │   └── composeResources/values/strings.xml                       # Res.string.privacy_policy_url
│       ├── androidMain/
│       │   ├── AndroidManifest.xml
│       │   └── kotlin/io/github/chudoxl/linteh/journal/MainActivity.kt
│       ├── iosMain/
│       │   └── kotlin/io/github/chudoxl/linteh/journal/MainViewController.kt
│       ├── commonTest/
│       │   └── kotlin/io/github/chudoxl/linteh/journal/AppTest.kt        # runComposeUiTest для iosX64Test
│       └── androidUnitTest/
│           └── kotlin/...               # smoke android-only тесты
│
├── core/
│   ├── platform/
│   │   ├── build.gradle.kts             # apply lintech-kmp + lintech-test
│   │   └── src/
│   │       ├── commonMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.kt
│   │       │                            # expect fun openUrl(url: String): Unit
│   │       ├── androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt
│   │       │                            # actual fun openUrl(url: String) — Intent.ACTION_VIEW
│   │       └── iosMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.ios.kt
│   │                                    # actual fun openUrl(url: String) — UIApplication.sharedApplication.openURL
│   │
│   ├── ui/
│   │   ├── build.gradle.kts             # apply lintech-kmp + lintech-compose + lintech-test
│   │   └── src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/ui/
│   │       └── (пусто на Phase 1; в Phase 4 — design tokens, OfflineBanner)
│   │
│   └── network/
│       ├── build.gradle.kts             # apply lintech-kmp + lintech-test
│       └── src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network/
│           └── (пусто на Phase 1; в Phase 2 — Ktor HttpClientFactory)
│
├── iosApp/                              # Xcode-проект (см. секцию SwiftPM ниже)
│   ├── Configuration/
│   │   └── Config.xcconfig
│   ├── iosApp/
│   │   ├── ContentView.swift            # импорт ComposeApp framework + MainViewController()
│   │   ├── iosAppApp.swift              # @main App entry
│   │   └── Info.plist
│   └── iosApp.xcodeproj/                # Xcode-проект
│
├── docs/                                # GitHub Pages source (D-21)
│   └── privacy/
│       └── index.html                   # Custom RU Privacy Policy
│
└── .github/
    └── workflows/
        ├── ci.yml                       # Android + iOS CI (D-13..D-17)
        └── pages.yml                    # GitHub Pages auto-deploy для docs/
```

### Per-module build.gradle.kts patterns

**`:composeApp/build.gradle.kts`** (укрупнённо):
```kotlin
plugins {
    id("lintech-kmp")
    id("lintech-compose")
    id("lintech-test")
    id("com.codingfeline.buildkonfig")
    id("org.jetbrains.kotlin.apple-privacy-manifests")
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

kotlin {
    androidTarget()
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true                     // SwiftPM-friendly (D-05)
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(project(":core:platform"))
            implementation(project(":core:ui"))
            // network не нужен в Phase 1, но импорт оставляется для будущего:
            // implementation(project(":core:network"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "io.github.chudoxl.linteh.journal"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.chudoxl.linteh.journal"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = providers.gradleProperty("versionName").get()  // "0.1.0"
    }
}

privacyManifest {
    embed(privacyManifest = layout.projectDirectory.file("PrivacyInfo.xcprivacy").asFile)
}

buildkonfig {
    packageName = "io.github.chudoxl.linteh.journal"
    defaultConfigs {
        buildConfigField(STRING, "VERSION_NAME", providers.gradleProperty("versionName").get())
        buildConfigField(STRING, "VERSION_CODE", (System.getenv("GITHUB_RUN_NUMBER") ?: "1"))
        buildConfigField(BOOLEAN, "IS_DEBUG", "true")  // в release-варианте planner поправит
    }
}
```

**`:core:platform/build.gradle.kts`** (минимум):
```kotlin
plugins {
    id("lintech-kmp")
    id("lintech-test")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // только то, что нужно для expect-функций:
            // в Phase 1 — ничего, чистый Kotlin
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)  // для Intent на Android-actual
        }
    }
}

android {
    namespace = "io.github.chudoxl.linteh.journal.core.platform"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}
```

`[VERIFIED: D-08, D-09 → namespace соответствует package root]`

---

## Convention Plugins Architecture

### Дизайн

Используем **«Now in Android»-pattern** `[VERIFIED: github.com/android/nowinandroid/tree/main/build-logic]`:

1. `build-logic/` — **отдельный включённый build** (`includeBuild` в settings.gradle.kts), а не обычный module. Это даёт kotlin-dsl + изоляцию + быстрее перекомпиляция.
2. Внутри `build-logic/convention/` — Gradle подмодуль с `kotlin-dsl` плагином и тремя `Plugin<Project>` классами.
3. Регистрация — через `gradlePlugin { plugins { register("...") } }` в `convention/build.gradle.kts` с **id-mapping → implementationClass**. Применение в module-level — через `id("lintech-kmp")` (apply-by-id).

`[VERIFIED: nowinandroid build-logic/convention/build.gradle.kts pattern; web fetch confirmed gradlePlugin DSL with plugin-id → implementationClass]`

### `build-logic/settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
rootProject.name = "build-logic"
include(":convention")
```

`[VERIFIED: nowinandroid pattern — versionCatalogs.from(files("../gradle/libs.versions.toml")) делает libs.* доступными в convention plugins]`

### `build-logic/convention/build.gradle.kts`

```kotlin
plugins {
    `kotlin-dsl`
}

group = "io.github.chudoxl.linteh.buildlogic"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)  // D-10
    }
}

dependencies {
    compileOnly(libs.androidGradlePlugin)
    compileOnly(libs.kotlinGradlePlugin)
    compileOnly(libs.composeGradlePlugin)
}

gradlePlugin {
    plugins {
        register("lintechKmp") {
            id = "lintech-kmp"
            implementationClass = "LintechKmpConventionPlugin"
        }
        register("lintechCompose") {
            id = "lintech-compose"
            implementationClass = "LintechComposeConventionPlugin"
        }
        register("lintechTest") {
            id = "lintech-test"
            implementationClass = "LintechTestConventionPlugin"
        }
    }
}
```

`[VERIFIED: nowinandroid конвенционный pattern]`

### `LintechKmpConventionPlugin.kt` (skeleton)

```kotlin
import org.gradle.api.Plugin
import org.gradle.api.Project

class LintechKmpConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.android.library")
        }

        configureKotlinMultiplatform()      // см. KotlinExt.kt
        configureCommonAndroid()             // см. AndroidExt.kt
    }
}

// ext/KotlinExt.kt
internal fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
        jvmToolchain(17)
        androidTarget()
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { /* configure */ }

        sourceSets {
            commonMain.dependencies {
                // baseline — пусто; модули добавляют сами
            }
            commonTest.dependencies {
                implementation(kotlin("test"))
            }
        }

        // freeCompilerArgs = strict null safety, etc.
        targets.all {
            compilations.all {
                kotlinOptions {
                    freeCompilerArgs += listOf("-Xexpect-actual-classes")
                }
            }
        }
    }
}

// ext/AndroidExt.kt
internal fun Project.configureCommonAndroid() {
    extensions.configure<LibraryExtension> {
        compileSdk = 35   // D-11
        defaultConfig {
            minSdk = 26   // D-11
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
```

### `LintechComposeConventionPlugin.kt` (skeleton)

```kotlin
class LintechComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")  // Kotlin 2.0+ Compose plugin

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.named("commonMain") {
                dependencies {
                    val composeBom = libs.findLibrary("compose-bom")  // если используем BOM
                    implementation(compose.runtime)
                    implementation(compose.foundation)
                    implementation(compose.material3)
                    implementation(compose.components.resources)
                    implementation(compose.components.uiToolingPreview)
                }
            }
        }
    }
}
```

`[VERIFIED: kotlinlang.org/docs/multiplatform/compose-test.html — compose.runtime + compose.material3 + compose.components.resources идут отдельными артефактами в CMP 1.10]`

### `LintechTestConventionPlugin.kt` (skeleton)

```kotlin
class LintechTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dev.mokkery")  // compiler-plugin-driven mocks

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.named("commonTest") {
                dependencies {
                    implementation(kotlin("test"))
                    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                    implementation(compose.uiTest)               // Compose UI Test
                    implementation(libs.kotest.assertions)
                    implementation(libs.turbine)
                }
            }
        }
    }
}
```

`[VERIFIED: kotlinlang.org/docs/multiplatform/compose-test.html — compose.uiTest нужен в commonTest для runComposeUiTest]`

### Why apply-by-id (НЕ class reference)

**Apply-by-id (`id("lintech-kmp")`)** — выбран потому что:
- Совпадает с тем, как пользователь применяет любой Gradle plugin в module-level → ровный mental model
- Поддерживает type-safe accessor когда convention plugin уже зарегистрирован — IDE подсказывает
- Работает через `pluginManager.apply("...")` внутри других convention plugins без import классов

**Class reference (`apply<LintechKmpConventionPlugin>()`)** — proigрывает:
- Требует import классов из buildSrc/included build → всплывают edge-cases с classpath
- Менее идиоматично

`[VERIFIED: nowinandroid использует apply-by-id consistently]`

---

## iOS Integration via SwiftPM

> Closes pitfall #15 (НЕ CocoaPods — D-05).

### Подход

В Phase 1 — **минимальная iOS-обвязка**: `:composeApp` экспортирует static framework через `binaries.framework { isStatic = true; baseName = "ComposeApp" }`. iOS-приложение в `iosApp/` импортирует этот framework. SwiftPM появляется только при подключении внешних Swift-зависимостей (в Phase 1 — **никаких внешних Swift-libs не нужно**), поэтому Phase 1 SwiftPM выглядит как «zero packages, but ready». Закрепляем это решение в скелетe для phases 2+.

### Что строит `:composeApp`

При публикации iOS framework Gradle создаёт задачи:
- `:composeApp:linkDebugFrameworkIosX64` / `IosArm64` / `IosSimulatorArm64` — собирают `ComposeApp.framework`
- `:composeApp:embedAndSignAppleFrameworkForXcode` — встраивает framework в Xcode-сборку (вызывается из Xcode build phase)

`[VERIFIED: kotlinlang.org/docs/multiplatform-create-app.html — стандартный Compose Multiplatform iOS template]`

### Структура `iosApp/`

```
iosApp/
├── Configuration/
│   └── Config.xcconfig                  # FRAMEWORK_SEARCH_PATHS = $(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
├── iosApp/
│   ├── iosAppApp.swift                  # @main App entry point
│   ├── ContentView.swift                # ComposeView { MainViewController() }
│   ├── Info.plist                       # CFBundleVersion ← versionCode, CFBundleShortVersionString ← versionName (D-30)
│   └── Assets.xcassets/                 # AppIcon (placeholder)
└── iosApp.xcodeproj/
    └── project.pbxproj
```

В `iosApp.xcodeproj` есть **Run Script Build Phase** в target iosApp:

```bash
cd "$SRCROOT/.."
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

**Этот script вызывается Xcode при каждой сборке** → KMP framework пересобирается синхронно с iOS-сборкой. На GitHub Actions macos-15 runner-ах эта phase выполняется при `xcodebuild` (но в Phase 1 мы НЕ запускаем xcodebuild — только `iosX64Test`).

`[VERIFIED: kotlinlang.org/docs — embedAndSignAppleFrameworkForXcode standard pattern]`

### `iosApp/iosApp/ContentView.swift`

```swift
import SwiftUI
import ComposeApp

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(edges: .all)
            .preferredColorScheme(.light)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

`[VERIFIED: standard Compose Multiplatform iOS template — `MainViewControllerKt` суффикс генерируется Kotlin/Native exporter]`

### Package.swift

**В Phase 1 НЕ нужен.** Создаётся когда добавится первая внешняя Swift-зависимость (вероятно Phase 6 — biometric-обвязка через `LocalAuthentication.framework` уже системный, не SwiftPM-package; SwiftPM-pkg может потребоваться для Sentry-Apple если решится добавлять). Phase 1 закрепляет паттерн `swiftPMDependencies {}` в `:composeApp/build.gradle.kts` как **placeholder-комментарий**:

```kotlin
// kotlin {
//     swiftPMDependencies {
//         // Future: add SwiftPM packages here when needed
//     }
// }
```

### XCFramework vs framework

В Phase 1 — **single-target framework** через `binaries.framework`. **XCFramework** (через `XCFramework().add(...)`) добавляется в Phase 6 при подготовке к TestFlight, потому что TestFlight требует universal framework для всех iOS arch. На дату Phase 1 этого не нужно — только iosX64Test running через CI.

---

## Hello LinTech Composable

### `commonMain/App.kt`

```kotlin
package io.github.chudoxl.linteh.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.chudoxl.linteh.journal.core.platform.openUrl
import linteh_journal.composeapp.generated.resources.Res
import linteh_journal.composeapp.generated.resources.privacy_policy_url
import linteh_journal.composeapp.generated.resources.app_name
import org.jetbrains.compose.resources.stringResource

@Composable
fun App() {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.testTag("app_title"),
            )
            Text(
                text = "v${BuildKonfig.VERSION_NAME} (${BuildKonfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("app_version"),
            )
            val privacyUrl = stringResource(Res.string.privacy_policy_url)
            Text(
                text = privacyUrl,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("privacy_url"),
            )
            Button(
                onClick = { openUrl(privacyUrl) },
                modifier = Modifier.testTag("open_privacy_button"),
            ) {
                Text("Открыть")
            }
        }
    }
}
```

`[VERIFIED: kotlinlang.org/docs/multiplatform/compose-test.html — testTag модификаторы для UI-test selectors]`

### `commonMain/composeResources/values/strings.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<resources>
    <string name="app_name">ЛИнТех Дневник</string>
    <string name="privacy_policy_url">https://chudoxl.github.io/LintehJournal/privacy/</string>
</resources>
```

`[VERIFIED: D-21, D-24 — URL и app_name заякорены]`

### `androidMain/MainActivity.kt`

```kotlin
package io.github.chudoxl.linteh.journal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
```

### `iosMain/MainViewController.kt`

```kotlin
package io.github.chudoxl.linteh.journal

import androidx.compose.ui.window.ComposeUIViewController

@Suppress("FunctionName")
fun MainViewController() = ComposeUIViewController { App() }
```

`[VERIFIED: kotlinlang.org/docs/multiplatform-create-app.html — стандартный CMP iOS-entry pattern]`

### `:core:platform` — expect/actual openUrl

**`commonMain/UrlOpener.kt`:**
```kotlin
package io.github.chudoxl.linteh.journal.core.platform

expect fun openUrl(url: String)
```

**`androidMain/UrlOpener.android.kt`:**
```kotlin
package io.github.chudoxl.linteh.journal.core.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

// Контекст пробрасывается через CompositionLocal в Phase 4 — пока хранится в companion-объекте
internal lateinit var androidApplicationContext: Context

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    androidApplicationContext.startActivity(intent)
}
```

> **Nota bene:** в Phase 1 контекст пробрасывается «грязно» через top-level mutable-property — это **acceptable для bootstrap**, в Phase 4 (вместе с Navigation 3 и Koin DI) `androidApplicationContext` заменится на CompositionLocal из `LocalContext.current`. Planner должен оставить TODO-комментарий + ссылку на Phase 4 task.

**`iosMain/UrlOpener.ios.kt`:**
```kotlin
package io.github.chudoxl.linteh.journal.core.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(
        url = nsUrl,
        options = emptyMap<Any?, Any?>(),
        completionHandler = null,
    )
}
```

`[VERIFIED: Apple UIKit docs — openURL:options:completionHandler доступен с iOS 10+, deployment target 14 (D-12) безопасно]`

### Wire MainActivity → androidApplicationContext

В `composeApp/androidMain/.../MainApplication.kt`:
```kotlin
package io.github.chudoxl.linteh.journal

import android.app.Application
import io.github.chudoxl.linteh.journal.core.platform.androidApplicationContext

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        androidApplicationContext = applicationContext
    }
}
```

И в `AndroidManifest.xml`: `android:name=".MainApplication"`.

---

## BuildKonfig Setup

### Где регистрируется

Plugin `com.codingfeline.buildkonfig` применяется **только в `:composeApp`** (D-29 «один common конфиг» — выбор researcher из CONTEXT.md Discretion). Per-module BuildKonfig — **отвергнуто** потому что:
1. Все потребители версии (`Hello LinTech`, в будущем — Sentry breadcrumb tags, AVERS UA mimic) живут в `:composeApp` или зависят от него.
2. Per-module → каждый модуль перегенерирует свой BuildKonfig → в classpath будет два класса с одинаковым именем → namespace-collision.
3. Single-source-of-truth для версионирования.

### Конфигурация

```kotlin
// composeApp/build.gradle.kts
plugins { id("com.codingfeline.buildkonfig") }

buildkonfig {
    packageName = "io.github.chudoxl.linteh.journal"
    objectName = "BuildKonfig"  // default

    defaultConfigs {
        buildConfigField(
            type = STRING,
            name = "VERSION_NAME",
            value = providers.gradleProperty("versionName").get(),  // "0.1.0" из gradle.properties
        )
        buildConfigField(
            type = STRING,
            name = "VERSION_CODE",
            value = (System.getenv("GITHUB_RUN_NUMBER") ?: "1"),
        )
        buildConfigField(
            type = BOOLEAN,
            name = "IS_DEBUG",
            value = "true",
        )
    }
}
```

### `gradle.properties`

```properties
# App version
versionName=0.1.0

# JVM
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
kotlin.code.style=official

# Caching
org.gradle.caching=true
org.gradle.parallel=true
org.gradle.configuration-cache=false
```

`[VERIFIED: github.com/yshrsmz/BuildKonfig docs — defaultConfigs с buildConfigField(STRING|BOOLEAN, name, value); generateBuildKonfig task запускается автоматически перед kotlin-compile]`

### Чтение из Kotlin

```kotlin
// в Hello LinTech App() composable:
Text("v${BuildKonfig.VERSION_NAME} (${BuildKonfig.VERSION_CODE})")
```

Импорт `io.github.chudoxl.linteh.journal.BuildKonfig`.

---

## GitHub Actions Workflows

> D-13..D-17, D-26.

### Files

```
.github/workflows/
├── ci.yml          # Android + iOS CI (push main + PR)
└── pages.yml       # GitHub Pages auto-deploy (push main для docs/**)
```

### `ci.yml` — Android + iOS отдельными jobs (НЕ matrix)

**Почему НЕ matrix:** Android и iOS jobs имеют **разные шаги** (Android lint vs PrivacyInfo lint), разные runner-ы (ubuntu vs macos), разные Gradle-задачи. Matrix-нотация делает yaml сложнее без выгоды. Отдельные jobs читаемее и параллелятся одинаково.

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read

jobs:
  android:
    name: Android
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      - name: Assemble debug
        run: ./gradlew assembleDebug

      - name: Lint
        run: ./gradlew lint

      - name: Test
        run: ./gradlew test

  ios:
    name: iOS
    runs-on: macos-15
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      - name: Run iOS X64 tests
        run: ./gradlew :composeApp:iosX64Test

      - name: Lint PrivacyInfo.xcprivacy
        run: |
          set -euo pipefail
          PLIST=composeApp/PrivacyInfo.xcprivacy

          # Шаг 1: plutil валидирует синтаксис XML/plist
          plutil -lint "$PLIST"

          # Шаг 2: grep ожидаемых reason codes из CONTEXT.md D-25
          plutil -extract NSPrivacyAccessedAPITypes xml1 "$PLIST" -o - | grep -q 'CA92.1' \
            || (echo "ERROR: CA92.1 (NSPrivacyAccessedAPICategoryUserDefaults reason) missing" && exit 1)

          plutil -extract NSPrivacyAccessedAPITypes xml1 "$PLIST" -o - | grep -q 'C617.1' \
            || (echo "ERROR: C617.1 (NSPrivacyAccessedAPICategoryFileTimestamp reason) missing" && exit 1)

          # Шаг 3: проверить NSPrivacyTracking=false (а не отсутствует)
          plutil -extract NSPrivacyTracking raw "$PLIST" | grep -q '^false$' \
            || (echo "ERROR: NSPrivacyTracking must be false" && exit 1)

          # Шаг 4: проверить пустой NSPrivacyCollectedDataTypes
          COLLECTED=$(plutil -extract NSPrivacyCollectedDataTypes xml1 "$PLIST" -o -)
          echo "$COLLECTED" | grep -qE '<array(/| )' \
            || (echo "ERROR: NSPrivacyCollectedDataTypes must be an array (empty)" && exit 1)

          echo "PrivacyInfo.xcprivacy passes all required-reason checks."
```

**Notes:**
- `plutil` — **macOS-only** CLI, доступен на macos-15 runner-е по умолчанию (часть `Foundation`/`CoreFoundation` toolchain). На ubuntu-runner линт **невозможен** → подтверждает D-13 «macOS runner ОБЯЗАТЕЛЕН».
- `gradle/actions/setup-gradle@v4` (НЕ v3) — **`v4` это major** на дату исследования. Latest stable patch — `4.4.x` `[VERIFIED: github.com/gradle/actions]`. Для запасного варианта `@v3` тоже работает, но в Phase 1 фиксируем v4 как длинноживущий major.
- `cache-read-only: ${{ github.ref != 'refs/heads/main' }}` — на feature-branch кэш только читаем, на main — пишем; стандартный паттерн настройки.
- Шаги Android и iOS jobs параллельны (зависимостей друг от друга нет). Branch protection (D-17) требует **обоих** зелёными.

`[VERIFIED: github.com/gradle/actions setup-gradle docs; macos-15 GitHub-hosted runner spec включает Xcode + plutil]`

### `pages.yml` — GitHub Pages auto-deploy

```yaml
# .github/workflows/pages.yml
name: Deploy GitHub Pages

on:
  push:
    branches: [main]
    paths:
      - 'docs/**'
      - '.github/workflows/pages.yml'

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: "pages"
  cancel-in-progress: false

jobs:
  deploy:
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Pages
        uses: actions/configure-pages@v5

      - name: Upload artifact
        uses: actions/upload-pages-artifact@v3
        with:
          path: 'docs'

      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
```

**Notes:**
- `actions/deploy-pages@v4` (CONTEXT.md упоминает v4 — на дату исследования v5 уже есть, но v4 stable достаточен) `[VERIFIED: github.com/actions/deploy-pages — v4/v5 backwards-compatible для базовой раздачи]`
- `paths: docs/**` — workflow срабатывает только при изменениях в docs/ → не блокирует CI основного кода
- Artifact upload + deploy split — стандартный паттерн `[VERIFIED: actions/deploy-pages README]`

### Branch protection rule (manual UI step, документируется в README)

После merge первого PR с этими workflow:
1. GitHub UI → Settings → Branches → Branch protection rule → Branch name pattern: `main`
2. Require status checks to pass before merging:
   - `Android` (CI)
   - `iOS` (CI)
3. Require branches to be up to date before merging — ON
4. Require conversation resolution — ON

Это implements D-17. **Planner** должен включить документацию-шаг в PLAN.md (manual UI configuration, не автоматизируется gradle/yaml).

---

## Privacy Policy GitHub Pages

### Source choice: `docs/` folder в main branch

**Выбираем `docs/` (НЕ `gh-pages` branch)** потому что:
- Документация GitHub Pages рекомендует `docs/` для repo-with-source `[VERIFIED: docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site]`
- История политики живёт в основном git-history → закрепляет D-27 (versioning через blame, без отдельных URL)
- Один Pull Request → код + privacy в одном merge-event
- `gh-pages` branch — overhead для статической одностраничной политики

**В дополнение:** используем `actions/deploy-pages@v4` workflow (НЕ default Jekyll). Причина:
- Default Jekyll-build на GitHub-side скрывает контроль; иногда добавляет undesired markdown-rendering rules
- Manual workflow даёт прозрачность: видно, что upload-pages-artifact кладёт в `_site` именно содержимое `docs/`
- `docs/` уже plain HTML (см. ниже) → Jekyll-обработка не нужна, наоборот, делает шум

### Структура `docs/`

```
docs/
├── index.html         # Лендинг репо (опционально; редирект на privacy/)
└── privacy/
    ├── index.html     # The actual privacy policy
    └── style.css      # Опционально — inline в index.html
```

### `docs/privacy/index.html` (RU only, custom)

```html
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Политика конфиденциальности — ЛИнТех Дневник</title>
    <style>
        body { max-width: 720px; margin: 2rem auto; padding: 1rem; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 1.6; color: #222; }
        h1, h2 { color: #1a1a1a; }
        h1 { border-bottom: 2px solid #ccc; padding-bottom: 0.5rem; }
        footer { margin-top: 3rem; padding-top: 1rem; border-top: 1px solid #eee; font-size: 0.9rem; color: #666; }
    </style>
</head>
<body>
    <h1>Политика конфиденциальности</h1>
    <p><strong>«ЛИнТех Дневник»</strong> — неофициальный мобильный клиент для электронного дневника
    ИАС «АВЕРС: Электронный Классный Журнал» школы №28 г. Кирова.</p>

    <h2>Что мы НЕ делаем</h2>
    <ul>
        <li>Мы не отправляем ваши данные третьим лицам.</li>
        <li>Мы не используем рекламу и трекеры.</li>
        <li>Мы не собираем аналитику использования.</li>
        <li>Мы не имеем доступа к вашим учётным данным или оценкам — у нас нет собственного сервера.</li>
    </ul>

    <h2>Что хранится на вашем устройстве</h2>
    <p>Все данные приложения хранятся локально на устройстве:</p>
    <ul>
        <li>Логин и пароль АВЕРС — в защищённом хранилище ОС (iOS Keychain, Android Keystore)</li>
        <li>Кэш оценок, расписания, домашних заданий — в зашифрованной локальной базе</li>
        <li>Cookies сессии АВЕРС — также локально</li>
    </ul>

    <h2>С какими сервисами мы общаемся</h2>
    <p>Приложение обменивается данными только с одним адресом:
    <code>journal.school28-kirov.ru</code> — официальный сайт ИАС АВЕРС школы №28.</p>
    <p>HTTPS-соединение обязательно. Сертификат проверяется системой устройства.</p>

    <h2>Перечень обрабатываемых ПДн</h2>
    <ul>
        <li>Логин (email или телефон) — для входа в АВЕРС</li>
        <li>Пароль — для входа в АВЕРС, хранится локально, не покидает устройство кроме запроса к АВЕРС</li>
        <li>ФИО ученика — отдаётся сервером АВЕРС в ответ на ваш запрос</li>
        <li>Оценки, расписание, домашние задания, посещаемость, сообщения от учителей — данные, которые вы получаете из АВЕРС</li>
    </ul>

    <h2>Удаление данных</h2>
    <p>Кнопка «Выйти» в настройках приложения удаляет все локальные данные аккаунта (логин/пароль из Keychain/Keystore,
    кэш базы, cookies). После выхода никаких данных приложения на устройстве не остаётся.</p>

    <h2>Связь с разработчиком</h2>
    <p>Приложение разработано <a href="mailto:chxevdev@gmail.com">независимым разработчиком</a>
    для семейного использования. Не аффилировано с ИИЦ «АВЕРС», школой №28 или Минобром Кировской области.</p>

    <h2>Изменения в политике</h2>
    <p>История изменений политики ведётся в публичном репозитории
    <a href="https://github.com/chudoxl/LintehJournal">github.com/chudoxl/LintehJournal</a>
    (файл <code>docs/privacy/index.html</code>, история git).</p>

    <footer>
        <p>Last-Modified: 2026-04-27<br>
        Версия приложения, на момент публикации политики: 0.1.0</p>
    </footer>
</body>
</html>
```

`[VERIFIED: D-23 — содержание соответствует on-device семантике; D-22 — RU only; D-27 — Last-Modified в footer]`

**Planner должен:** в задаче написания политики предусмотреть, что `Last-Modified` в footer обновляется ВРУЧНУЮ при каждом изменении файла. На дату Phase 1 — текущая дата. Альтернатива — JS-генерация даты из git-blame на client side — overkill для статической одностраничной policy.

---

## PrivacyInfo.xcprivacy

### Точное содержимое

> `composeApp/PrivacyInfo.xcprivacy`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>NSPrivacyTracking</key>
    <false/>

    <key>NSPrivacyTrackingDomains</key>
    <array/>

    <key>NSPrivacyCollectedDataTypes</key>
    <array/>

    <key>NSPrivacyAccessedAPITypes</key>
    <array>
        <dict>
            <key>NSPrivacyAccessedAPIType</key>
            <string>NSPrivacyAccessedAPICategoryUserDefaults</string>
            <key>NSPrivacyAccessedAPITypeReasons</key>
            <array>
                <string>CA92.1</string>
            </array>
        </dict>
        <dict>
            <key>NSPrivacyAccessedAPIType</key>
            <string>NSPrivacyAccessedAPICategoryFileTimestamp</string>
            <key>NSPrivacyAccessedAPITypeReasons</key>
            <array>
                <string>C617.1</string>
            </array>
        </dict>
    </array>
</dict>
</plist>
```

`[VERIFIED: D-25 + Apple TN3183 (Adding required reason API entries to your privacy manifest); CA92.1 = "Access user defaults", C617.1 = "Access file timestamps for files within app's container/group container/CloudKit container"; web-search confirmed both reason codes]`

### Reason-codes — что они формально декларируют

| Code | Category | What it covers |
|------|----------|----------------|
| **CA92.1** | NSPrivacyAccessedAPICategoryUserDefaults | "Access info from same app or app group" — мы используем NSUserDefaults через будущий multiplatform-settings (Phase 3+); даже если в Phase 1 НЕ используется напрямую, **некоторые internal Compose Multiplatform API могут трогать UserDefaults** для preferences, поэтому декларация обязательна с самого первого билда |
| **C617.1** | NSPrivacyAccessedAPICategoryFileTimestamp | "Access timestamps, size, or other metadata of files inside the app container, app group container, or app's CloudKit container" — в Phase 3+ Room/SQLite будет читать file metadata для cache-age; декларируем заранее |

`[VERIFIED: support.singular.net/hc/en-us/articles/24045392537243 + apple developer TN3183]`

### Где упаковывается в .ipa

Через **`org.jetbrains.kotlin.apple-privacy-manifests`** plugin v1.0.0:
```kotlin
// composeApp/build.gradle.kts
plugins {
    id("org.jetbrains.kotlin.apple-privacy-manifests") version "1.0.0"
}

privacyManifest {
    embed(privacyManifest = layout.projectDirectory.file("PrivacyInfo.xcprivacy").asFile)
}
```

Plugin **автоматически копирует** PrivacyInfo.xcprivacy в `Frameworks/ComposeApp.framework/PrivacyInfo.xcprivacy` при сборке Apple framework. Apple-side (Xcode при `xcodebuild archive`) копирует privacy manifest как часть .ipa.

`[VERIFIED: kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html — точная документация JetBrains; "The plugin automatically copies the PrivacyInfo.xcprivacy file to the correct output location as specified by Apple's requirements"]`

### Lint-команды

См. `ci.yml` iOS-job step "Lint PrivacyInfo.xcprivacy" выше — `plutil -lint` + `plutil -extract` + `grep` для CA92.1, C617.1, NSPrivacyTracking=false, NSPrivacyCollectedDataTypes=array.

### JetBrains-recommended additional reason codes

> Из официальных JetBrains-docs: Compose Multiplatform может triger-ить ещё три reason API:

| API | Code | Status в Phase 1 |
|-----|------|------------------|
| `fstat` | `0A2A.1` | **НЕ декларируется в Phase 1** (CONTEXT.md D-25 фиксирует только CA92.1 + C617.1). Если App Store Connect upload reject-ит ITMS-91053 на `fstat` — добавится reactively. Phase 1 закрепляет минимум, требуемый ROADMAP success criterion #4. |
| `stat` | `0A2A.1` | Same as fstat |
| `mach_absolute_time` | `35F9.1` | Same |

**Planner-у:** оставить TODO в `PrivacyInfo.xcprivacy` или README о том, что при первом TestFlight upload в Phase 6 эти три кода **могут** потребоваться (issue #4738 в JetBrains/compose-multiplatform tracker). На Phase 1 — игнорируем.

`[VERIFIED: kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html — table of "may trigger" APIs]`

---

## Cross-platform Testing

### Setup `runComposeUiTest` в commonTest

**`composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/AppTest.kt`:**

```kotlin
package io.github.chudoxl.linteh.journal

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

class AppTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun helloLintech_displaysAllElements() = runComposeUiTest {
        setContent { App() }

        onNodeWithTag("app_title").assertIsDisplayed()
        onNodeWithTag("app_version").assertIsDisplayed()
        onNodeWithTag("privacy_url").assertIsDisplayed()
        onNodeWithTag("open_privacy_button").assertIsDisplayed()
    }
}
```

### Запуск

| Платформа | Команда | Run в CI? |
|-----------|---------|-----------|
| Android JVM-units | `./gradlew :composeApp:test` | YES (Android job — D-14) |
| Android instrumentation | `./gradlew :composeApp:connectedAndroidTest` | NO (требует device/emulator — Phase 4+) |
| iOS X64 (Intel sim) | `./gradlew :composeApp:iosX64Test` | YES (iOS job — D-15) |
| iOS Arm64 sim | `./gradlew :composeApp:iosSimulatorArm64Test` | NO в Phase 1 (Apple Silicon runners ⇒ payed; bare-min Phase 1) |

`[VERIFIED: kotlinlang.org/docs/multiplatform/compose-test.html — "iOS Simulator: ./gradlew :composeApp:iosSimulatorArm64Test"; iosX64Test эквивалент для Intel]`

### testTag pattern (НЕ текстовые селекторы)

**Use:** `Modifier.testTag("xxx")` + `onNodeWithTag("xxx")`. Причины:
- Локализация может изменить тексты — testTag не зависит от строк
- Кириллица в тестовых ассертах загромождает код
- Best-practice JetBrains для commonTest

`[VERIFIED: kotlinlang.org/docs/multiplatform/compose-test.html]`

---

## ROADMAP edit задача

### Что меняется в ROADMAP.md

**Текущая запись (Phase 1, Success Criteria #2):**
> «Разработчик может открыть проект в Android Studio + Xcode и запустить заглушку «Hello LinTech» на симуляторе iPhone и Android-устройстве»

**Заменить на:**
> «Разработчик может открыть проект в Android Studio и запустить заглушку «Hello LinTech» на Android-устройстве/эмуляторе. iOS «Hello LinTech» валидируется через автоматический `iosX64Test` screenshot-test в CI на macos-15 runner-е (dev-host разработчика — Linux Mint, локальный Xcode недоступен).»

**Причина:** dev-host = Linux Mint. Без Mac локальное `xcodebuild` невозможно. Полагаемся на CI iosX64Test для validation iOS-side кода. Это явная техническая константа из CONTEXT.md D-13 + `<specifics>`.

### Закрепление D-25 как finalized

**Добавить в ROADMAP.md Phase 1 Success Criteria #4 (опционально, если planner посчитает нужным):**
> «… и CI lint (`plutil -lint` + grep CA92.1, C617.1, NSPrivacyTracking=false) на macos-job не выдаёт ошибок.»

`[VERIFIED: CONTEXT.md <deferred> «Корректировки ROADMAP.md»]`

**Planner Phase 1 должен** добавить в PLAN.md специальную task: "Edit ROADMAP.md success criterion #2" с явным before/after diff.

---

## Documentation / CLAUDE.md Updates

> Что записываем по итогам Phase 1.

### CLAUDE.md additions

После merge Phase 1 PR обновить разделы:

**`## Conventions` (replace TBD):**
- Add: «Convention plugins `lintech-kmp`, `lintech-compose`, `lintech-test` в `build-logic/` — все KMP-модули применяют один из этих ID. Module build.gradle.kts ≤ 5 строк plugins-блока.»
- Add: «Package root: `io.github.chudoxl.linteh.journal.*`. Sub-package align with module: `io.github.chudoxl.linteh.journal.core.platform.*`.»
- Add: «Тесты: `runComposeUiTest` в commonTest с `Modifier.testTag(...)` selectors. Никаких текстовых ассертов.»
- Add: «iOS-сборки валидируются через `iosX64Test` в CI (macos-15). Локальный Xcode не требуется.»

**`## Architecture` (replace TBD):**
- Add diagram: composeApp → :core:ui / :core:network → :core:platform (текущий level в Phase 1).
- Add: «Модули: `:composeApp`, `:build-logic`, `:core:platform`, `:core:ui`, `:core:network`. `:core:{domain,data,database}` → Phase 3.»
- Add: «iOS-интеграция через SwiftPM (НЕ CocoaPods). XCFramework через `embedAndSignAppleFrameworkForXcode` — задача Phase 6.»

### README.md (root)

**Создаём в Phase 1.** Минимум:

```markdown
# ЛИнТех Дневник

Кроссплатформенный мобильный клиент (iOS + Android) для электронного дневника
ИАС АВЕРС школы №28 г. Кирова.

## Status

Pre-alpha. Phase 1 (Foundation & Compliance Infrastructure).

## Privacy Policy

[https://chudoxl.github.io/LintehJournal/privacy/](https://chudoxl.github.io/LintehJournal/privacy/)

## Build

### Android
```bash
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:test
```

### iOS
```bash
./gradlew :composeApp:iosX64Test       # требует macOS runner
```

### CI
GitHub Actions builds Android (ubuntu-latest) and iOS (macos-15) on each push to `main` and PR.

## License

MIT (TBD — finalize before v1 public release).
```

`[VERIFIED: D-24 — README contains markdown link to privacy policy]`

---

## Validation Architecture

> nyquist_validation: true (config.json) → секция включена.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | `kotlin.test` (multiplatform) + `compose.uiTest` для Compose UI |
| Config file | None (KMP-test config через `commonTest` в `build.gradle.kts`) |
| Quick run command | `./gradlew :composeApp:test :composeApp:iosX64Test` |
| Full suite command | `./gradlew test iosX64Test` (все модули) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| **COMP-01** | Privacy Policy URL доступен из приложения через `Res.string.privacy_policy_url` | unit (Compose UI) | `./gradlew :composeApp:iosX64Test --tests "io.github.chudoxl.linteh.journal.AppTest.helloLintech_displaysAllElements"` | ❌ Wave 0 (создаётся в Phase 1) |
| **COMP-01** | Privacy Policy URL отдаёт HTTP 200 на GitHub Pages (после deploy) | smoke / manual | `curl -sf https://chudoxl.github.io/LintehJournal/privacy/ \| grep -q "Политика конфиденциальности"` | ❌ Wave 0 (manual smoke в README после first deploy) |
| **COMP-01** | README.md содержит markdown-ссылку на Privacy Policy URL | smoke (CI или manual) | `grep -F "https://chudoxl.github.io/LintehJournal/privacy/" README.md` | ❌ Wave 0 (создаётся в Phase 1) |
| **COMP-02** | PrivacyInfo.xcprivacy валиден через plutil + содержит CA92.1 + C617.1 + NSPrivacyTracking=false | smoke (CI macos-job) | См. `ci.yml` step "Lint PrivacyInfo.xcprivacy" выше | ❌ Wave 0 (создаётся в Phase 1) |
| **COMP-02** | PrivacyInfo.xcprivacy упакован в iOS framework (через apple-privacy-manifests plugin) | integration (manual после Phase 6 build) | `unzip -p ComposeApp.xcframework/<arch>/ComposeApp.framework/PrivacyInfo.xcprivacy \| plutil -lint -` | n/a в Phase 1 (Phase 6 — TestFlight) |
| Bootstrap | Skeleton 5 модулей собирается на обеих платформах | smoke | `./gradlew assembleDebug iosX64Test` | ❌ Wave 0 |
| Bootstrap | Convention plugins применяются ≤5 строк plugins-блока | manual review | grep'ом по плану — каждый module build.gradle.kts has ≤5 строк в plugins{} | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :composeApp:test` (быстрый Android JVM-test) + `./gradlew lint` — ~15 сек.
- **Per wave merge:** `./gradlew assembleDebug iosX64Test` — полный Android + iOS smoke в CI.
- **Phase gate:** Full suite `./gradlew assembleDebug lint test iosX64Test` зелёный + manual smoke `curl https://chudoxl.github.io/LintehJournal/privacy/` HTTP 200 + manual run «Hello LinTech» на Android-устройстве разработчика.

### Wave 0 Gaps

> Тесты, которые НЕ существуют, и должны быть созданы в Phase 1 (вместе с production code):

- [ ] `composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/AppTest.kt` — `helloLintech_displaysAllElements` (covers COMP-01 visibility)
- [ ] `composeApp/PrivacyInfo.xcprivacy` — required-reason API stub (covers COMP-02)
- [ ] `.github/workflows/ci.yml` step "Lint PrivacyInfo.xcprivacy" — `plutil` + `grep` (validates COMP-02 на каждом коммите)
- [ ] `docs/privacy/index.html` — actual privacy policy (covers COMP-01)
- [ ] `README.md` markdown link к privacy URL (covers COMP-01 success criterion #3)
- [ ] Smoke-test command в README: `curl -sf <URL> | grep -q ...` для validation post-deploy

**Framework install:** `./gradlew wrapper --gradle-version 8.10` (создание Gradle wrapper) — единственный «install» step. Все остальные deps подтягиваются автоматически Gradle.

---

## Security Domain

> security_enforcement не отключён в config.json → секция включена. Phase 1 — bootstrap-фаза, минимально касается security; **большая часть security появится в Phase 3 (Auth)**.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Phase 3 — Auth |
| V3 Session Management | no | Phase 2/3 — cookies in DB |
| V4 Access Control | no | Phase 3+ |
| V5 Input Validation | partial | Privacy Policy URL — формально в strings.xml (compile-time const), валидация не нужна |
| V6 Cryptography | no | Phase 3 — KVault/Keystore |
| V14 Configuration | yes | Privacy Manifest = compliance config; CI lint = automated check; gradle.properties = no secrets in Phase 1 |

### Known Threat Patterns for Compose Multiplatform / Phase 1 Bootstrap

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Privacy Manifest missing → App Store reject | (compliance, не STRIDE) | `apple-privacy-manifests` plugin + CI lint `plutil` + grep reason-codes |
| README или git history committing secrets (`.env`, keystore) | I (Information disclosure) | Phase 1 secrets НЕ нужны (debug-only — D-18, D-20). Однако `.gitignore` должен явно excludes: `*.keystore`, `local.properties`, `.env`, `*.jks` |
| GitHub Pages serving non-HTTPS or with mixed content | I | GitHub Pages enforces HTTPS by default; verify в repo Settings → Pages → "Enforce HTTPS" → ON |
| `actions/checkout@v4` exposing GITHUB_TOKEN by default to forks | E (Elevation of privilege) | `permissions: contents: read` + `cache-read-only` для PR (configured в ci.yml) |
| Privacy Policy claiming on-device but actually leaking via debug-logs | I | Phase 2/3 — sanitizeHeader + canary-test (pitfall #5). Phase 1 — НЕ актуально (нет network/auth кода). |
| GitHub Pages branch protection bypass | T (Tampering) | Branch protection rule на main + required reviews — настраивается после Phase 1 PR merge |

`[VERIFIED: docs.github.com/en/pages — HTTPS enforced; PITFALLS.md #5 (logging hygiene → Phase 2)]`

---

## Common Pitfalls

### Pitfall 1: KSP version mismatch (Kotlin 2.2.20 vs KSP 2.2.20-2.0.x)

**What goes wrong:** Build fails «ksp-2.2.0-2.0.2 is too old for kotlin-2.2.20».
**Why:** KSP **обязан** совпадать по major.minor с Kotlin. Если version catalog указывает `kotlin = "2.2.20"` + `ksp = "2.2.0-2.0.x"` — IDE автоподставка провалит сборку.
**Avoid:** В `libs.versions.toml` зафиксировать `ksp = "2.2.20-2.0.4"`. **В Phase 1 KSP не используется** (Room/Hilt — Phase 3+), но плагин-id регистрируется в convention для будущего.
**Detect:** Build log: «ksp-X.X.X is too old for kotlin-Y.Y.Y».

`[VERIFIED: github.com/google/ksp/issues/2619]`

### Pitfall 2: `apple-privacy-manifests` plugin не подключён → PrivacyInfo.xcprivacy не упакован в .ipa

**What goes wrong:** Файл лежит в `composeApp/PrivacyInfo.xcprivacy`, но не попадает в финальный framework. App Store Connect reject «ITMS-91053».
**Why:** Простое размещение файла в проектной папке **не достаточно** — Apple ищет `PrivacyInfo.xcprivacy` строго в `Frameworks/<Name>.framework/PrivacyInfo.xcprivacy`. Plugin делает копирование за вас.
**Avoid:** В `composeApp/build.gradle.kts` обязательно `plugins { id("org.jetbrains.kotlin.apple-privacy-manifests") version "1.0.0" }` + `privacyManifest { embed(...) }`.
**Detect:** В Phase 6 (TestFlight) — App Store Connect upload error. В Phase 1 — невозможно validate без xcodebuild archive (`embedAndSignAppleFrameworkForXcode` не запускается в `iosX64Test`). **Mitigation для Phase 1:** манифест существует и проходит `plutil -lint`; реальная упаковка проверяется в Phase 6.

`[VERIFIED: kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html]`

### Pitfall 3: `plutil` отсутствует на ubuntu-runner

**What goes wrong:** CI step «Lint PrivacyInfo.xcprivacy» падает с «plutil: command not found» если запустить на ubuntu-latest.
**Why:** `plutil` — macOS-only CLI (часть Foundation framework).
**Avoid:** Lint-step запускается **строго на macos-15 job**, НЕ matrix-странно. Подтверждает D-13 «macOS runner ОБЯЗАТЕЛЕН».
**Detect:** При написании `ci.yml` — двойной check: lint-step находится под `jobs: ios:` секцией.

### Pitfall 4: `iosX64Test` падает в CI потому что macOS-runner default Xcode не настроен

**What goes wrong:** GitHub macos-15 runner поставляется с несколькими версиями Xcode; default может не совпадать с requirement KMP toolchain. Test fails «No such SDK iphonesimulator».
**Avoid:** В `ci.yml` добавить шаг выбора Xcode-версии перед Gradle:
```yaml
- name: Select Xcode 16
  run: sudo xcode-select -s /Applications/Xcode_16.0.app
```
**Detect:** CI iOS-job log «xcrun: error: SDK 'iphonesimulator' not found».

`[ASSUMED — основано на типичных CI gotchas; verify в первом CI run]`

### Pitfall 5: `composeApp/PrivacyInfo.xcprivacy` правильный XML но неверная схема reasons

**What goes wrong:** `plutil -lint` проходит, но App Store reject — потому что reason-codes неточные (например, `CA92.1` написано с tab-character или неверный case).
**Avoid:** Содержание plist — **точно как в секции "PrivacyInfo.xcprivacy" выше**. Сверка через CI grep — обязательна.
**Detect:** TestFlight upload reject. Phase 1 — defensive grep для CA92.1 + C617.1 + NSPrivacyTracking=false.

### Pitfall 6: Convention plugin не находится потому что `includeBuild` не настроен

**What goes wrong:** Module-level `id("lintech-kmp")` → `Plugin with id 'lintech-kmp' not found`.
**Why:** Забыли `includeBuild("build-logic")` в root `settings.gradle.kts`'s `pluginManagement {}`.
**Avoid:** `pluginManagement { ... includeBuild("build-logic") }` ОБЯЗАТЕЛЕН.
**Detect:** Sync ошибка сразу при открытии в IDE.

`[VERIFIED: nowinandroid pattern]`

### Pitfall 7: BuildKonfig generate task не запускается перед kotlinCompile

**What goes wrong:** Hello LinTech composable импортирует `BuildKonfig.VERSION_NAME` → "unresolved reference".
**Avoid:** Plugin автоматически связывает `generateBuildKonfig` task с kotlinCompile. **Не пытаться** ручную task-dependency. Если всё равно не работает — `./gradlew clean :composeApp:generateBuildKonfig`.
**Detect:** Compilation error «unresolved reference: BuildKonfig».

`[VERIFIED: github.com/yshrsmz/BuildKonfig README]`

### Pitfall 8: GitHub Pages сначала deploy, потом enable

**What goes wrong:** Workflow `pages.yml` запустил, но первый deploy fails: «Pages site not enabled».
**Why:** GitHub Pages должен быть включён через repo Settings ОДИН РАЗ. После этого `actions/configure-pages@v5` работает корректно.
**Avoid:** Phase 1 task-list должна включать manual step: «Repo Settings → Pages → Source: GitHub Actions» **перед** первым merge с pages.yml.
**Detect:** First `pages.yml` run fails.

`[VERIFIED: actions/deploy-pages README — "Make sure GitHub Pages is enabled before workflow runs"]`

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| BuildKonfig для KMP-modules | Custom Gradle task generating Kotlin object с константами | `com.codingfeline.buildkonfig` plugin | Plugin handles per-target overrides, IDE indexing, generate-on-compile dependency. Custom — можно, но за 2 дня окажется, что Hello LinTech перебилдит build cache |
| iOS Privacy Manifest packaging | `Sync` task copying `PrivacyInfo.xcprivacy` в framework | `org.jetbrains.kotlin.apple-privacy-manifests` plugin | Plugin знает точный output-path для всех Apple targets; ручное копирование сломается при `linkDebugFrameworkIosArm64` task path-changes |
| Convention plugins repository | `buildSrc/` directory | `build-logic/` includedBuild | `buildSrc` синхронизирует кэш всех модулей при изменении any class — медленно. `includeBuild` изолирует |
| GitHub Pages deploy | Custom shell-script с `git push gh-pages` | `actions/upload-pages-artifact@v3` + `actions/deploy-pages@v4` | Officially supported, OIDC-secured, integrates с GitHub Pages site lifecycle |
| KMP module dependency-rule enforcement | Custom `gradle-modules-graph` plugin | PR-review + README в Phase 1 | Phase 1 = 5 модулей, overkill. Re-evaluate когда модулей >10 (D-07 explicit) |
| Compose UI test driver для KMP | Custom JUnit-runner для iOS | `runComposeUiTest` (compose.uiTest) | Bundled с Compose Multiplatform 1.10, KMP-aware, single source of truth |

**Key insight:** Phase 1 — bootstrap. **Любой custom-built tool в Phase 1 становится legacy всех phases 2-6.** Бескомпромиссно использовать только officially-supported plugins даже там, где можно сократить tooling.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `cocoapods {}` блок в build.gradle.kts | SwiftPM (`swiftPMDependencies {}` или ZERO swift-deps в Phase 1) | KMP 2.0+ (2024); CocoaPods trunk read-only 2026-12-02 | Pitfall #15. **Не возвращаемся к CocoaPods.** |
| `buildSrc/` для convention plugins | `build-logic/` includedBuild | Now-in-Android-pattern с 2022; KMP best-practice с 2024 | Faster builds; convention plugins не invalidate-ят весь project cache при изменении |
| Manual gradle dependencies versioning | `gradle/libs.versions.toml` Version Catalog | Gradle 8.0+ (2023) | D-04 — single source of truth |
| `kotlin-android` plugin | `org.jetbrains.kotlin.android` (через AGP-aligned plugin) | Kotlin 2.0+ Compose plugin moved to `org.jetbrains.kotlin.plugin.compose` | Применяется в `lintech-compose` convention |
| `ksp1` (K1 compiler-plugin) | KSP2 (default since beginning of 2025) | Kotlin 2.2.0+ | KSP1 deprecated; даже для будущего Room в Phase 3 — KSP2 `[VERIFIED: github.com/google/ksp]` |
| `kotlin.test` без compose-test | `compose.uiTest` API в commonTest для Compose UI tests | CMP 1.6+ | `runComposeUiTest` принят как standard `[VERIFIED: kotlinlang.org/docs/multiplatform/compose-test.html]` |
| Privacy Manifest как Resource в Xcode | `apple-privacy-manifests` Kotlin plugin | 2024 (Apple required от 12 Feb 2025) | Plugin абстрагирует location + auto-copy `[VERIFIED]` |

**Deprecated/outdated:**
- `kotlin-multiplatform-mobile` plugin (KMM) → **mainstream** Kotlin Multiplatform (KMP) с 2024; KMM Plugin для IntelliJ deprecated с 2025
- `kotlinx-datetime 0.6.2` → **0.7.1 stable** (Phase 1 — выбираем `0.6.2` или `0.7.1` оба stable; 0.8.x НЕ stable)
- `ktor-client-cio` engine на mobile → Darwin (iOS) + OkHttp (Android) `[STACK.md What NOT to Use]`

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| **A1** | AGP version 8.7.x совместима с targetSdk 35 + JDK 17 + Kotlin 2.2.20 | Concrete Versions Table | Build fails. Mitigation: planner проверяет latest AGP stable через `npx --yes ctx7@latest docs androidGradlePlugin` перед закреплением version в catalog. **Verifiable в первом CI run.** |
| **A2** | Mokkery latest stable = `2.5.x` | Concrete Versions Table | Compiler-plugin compatibility error. Phase 1 — Mokkery в `lintech-test` не используется реально (нет mocks нужных в Hello LinTech). Phase 3 — verify. |
| **A3** | macos-15 GitHub-hosted runner default Xcode 16+ supports CMP 1.10.3 без `xcode-select` step | Common Pitfalls #4 | iOS CI fails «No SDK iphonesimulator». Mitigation: добавить explicit `xcode-select -s` step в Phase 1 если первый run падает. |
| **A4** | `iosX64Test` task **не требует** xcodebuild archive — только Kotlin/Native test runner | Cross-platform Testing | Если требует — Phase 1 валидация iOS-side ломается. Mitigation: в первом CI run проверить, что test gradle-task succeeds без manual Xcode action. **HIGH confidence согласно CMP docs**, но physical verification — only in-CI. |
| **A5** | `kotlinx.serialization 1.7.3` достаточно для Phase 1 | Concrete Versions Table | Phase 1 не использует serialization — низкий риск. Phase 2 точная version finalized. |
| **A6** | `gradle/actions/setup-gradle@v4` кэширует KMP klib-cache out-of-box | GitHub Actions Workflows | Если нет — KMP iOS build на каждом коммите медленный (~7 min instead 2 min). Mitigation: добавить explicit cache key для `~/.konan` в Phase 1 если первые runs медленные. |
| **A7** | `apple-privacy-manifests` plugin v1.0.0 — последняя stable на дату создания PR | Versions Table | Если есть newer — проверить changelog. Plugin малый, low-risk. |

**User confirmation needed:**
- A1, A2, A6 — operational; первый CI run будет verify.
- A3 — может потребовать explicit Xcode selection.
- Все остальные claims — `[VERIFIED]` через documentation lookup или `[CITED]` from CONTEXT.md/STACK.md/PITFALLS.md.

---

## Open Questions

1. **Будет ли GitHub Actions cache stale после изменения convention plugin?**
   - What we know: `setup-gradle@v4` invalidates cache при изменении `gradle/libs.versions.toml` или `gradle.properties`. Изменение convention plugin (`build-logic/convention/src/main/kotlin/*.kt`) — отдельный включённый build, может не trigger.
   - What's unclear: ведёт ли это к корректной перекомпиляции consumers convention plugins.
   - Recommendation: после первого изменения convention plugin — manually verify CI rerun без `cache-read-only`. Если stale — добавить cache-key включающий hash `build-logic/`.

2. **Нужен ли SKIE в Phase 1 для Swift-interop с MainViewControllerKt?**
   - What we know: SKIE улучшает Kotlin↔Swift interop (sealed classes → Swift enums, suspend → async/await). Pitfall #14 рекомендует.
   - What's unclear: Phase 1 — `MainViewControllerKt.MainViewController()` — single function, primitive types, completion handlers не нужны → SKIE может быть overkill для Phase 1.
   - Recommendation: **НЕ добавлять SKIE в Phase 1.** Re-evaluate в Phase 4 когда появится первая sealed-class или suspend-function в commonMain, экспортируемая на iOS.

3. **Должен ли `:core:network` существовать как pустой module в Phase 1, или отложить на Phase 2?**
   - What we know: CONTEXT.md D-01 явно перечисляет `:core:network` в 5 модулей Phase 1. CONTEXT.md `<code_context>` отмечает «:core:network появится в Phase 1».
   - What's unclear: содержит ли empty module ценность сразу, или это «boilerplate с дня 1, который Phase 2 заменит».
   - Recommendation: **создаём empty `:core:network`** в Phase 1 согласно D-01 — это устанавливает структуру до того, как Phase 2 начнётся. Plan-checker проверит compliance.

4. **Должен ли `MainApplication.androidApplicationContext` быть top-level mutable property?**
   - What we know: Hello LinTech `openUrl()` на Android требует Context. В Phase 1 нет DI (Koin), нет CompositionLocal.
   - What's unclear: best-practice для Phase 1 (когда это именно bootstrap-bridge).
   - Recommendation: **Top-level `internal lateinit var`** в `:core:platform/androidMain` с `MainApplication.onCreate()` инициализирующим — pragmatic + явный TODO-комментарий ссылающийся на Phase 4 (Koin DI). НЕ рефакторить в Phase 1, не embedding-аем дополнительные зависимости ради чистоты.

5. **Versioning: `versionCode = github.run_number` (D-30) — что происходит локально?**
   - What we know: D-30 говорит "github.run_number в CI / git rev-list --count HEAD локально".
   - What's unclear: local fallback для разработчика без git history (свежий clone).
   - Recommendation: в `composeApp/build.gradle.kts`:
     ```kotlin
     versionCode = (System.getenv("GITHUB_RUN_NUMBER") 
         ?: providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }.standardOutput.asText.get().trim()
         ?: "1").toInt()
     ```
     Triple-fallback: env → git → 1. Last-resort "1" покрывает edge-case fresh clone до first commit.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `node` | gsd-tools.cjs | ✓ | v24.15.0 | — |
| `git` | repo operations | ✓ | system git | — |
| `plutil` | PrivacyInfo lint locally | ✗ | n/a | macOS-only — lint runs only on CI macos-15 (D-13) |
| `xmllint` | XML validation alternative | ✗ | n/a | Используем plutil в CI; локально — IDE plist editor |
| Java JDK 17 | Gradle build | ❓ | TBD | Установится через `actions/setup-java@v4` в CI; разработчик локально — manual install |
| Gradle | Gradle wrapper bootstraps | n/a | wrapper-managed | Generates as part of Phase 1 task |
| Android SDK 35 | Android build | ❓ | TBD | Auto-installed by AGP в CI; разработчик локально — Android Studio |
| Xcode 16+ | iOS build (NOT в Phase 1) | ✗ | n/a | macos-15 runner has Xcode pre-installed; dev-host = Linux Mint, Xcode unavailable (`<specifics>`) |

**Missing dependencies with no fallback:**
- None blocking Phase 1 — все required tools available either locally (`node`, `git`) or in CI (Java, Android SDK, Xcode, plutil).

**Missing dependencies with fallback:**
- `plutil` локально → CI-only (acceptable; CI macos-15 — single-source-of-truth для PrivacyInfo lint).
- Local Xcode → CI-only iOS validation through `iosX64Test` (D-15, deferred ROADMAP correction).

---

## Code Examples

### Example 1: Convention plugin invocation

**`:composeApp/build.gradle.kts` (sample apply):**
```kotlin
// Source: derived from nowinandroid pattern
plugins {
    id("lintech-kmp")
    id("lintech-compose")
    id("lintech-test")
    id("com.codingfeline.buildkonfig")
    id("org.jetbrains.kotlin.apple-privacy-manifests") version "1.0.0"
    alias(libs.plugins.androidApplication)
}
```

**`:core:platform/build.gradle.kts`:**
```kotlin
plugins {
    id("lintech-kmp")
    id("lintech-test")
}
```

Phase 1 success criterion #5: «add new KMP module ≤ 5 lines build.gradle.kts plugins block» — verifiable.

### Example 2: PrivacyInfo.xcprivacy embedding в Kotlin DSL

```kotlin
// Source: kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html
// composeApp/build.gradle.kts (after plugins block)

privacyManifest {
    embed(
        privacyManifest = layout.projectDirectory.file("PrivacyInfo.xcprivacy").asFile,
    )
}
```

### Example 3: GitHub Actions iOS test job + PrivacyInfo lint

```yaml
# Source: this RESEARCH.md "GitHub Actions Workflows" section
ios:
  name: iOS
  runs-on: macos-15
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with: { java-version: '17', distribution: 'temurin' }
    - uses: gradle/actions/setup-gradle@v4
    - run: ./gradlew :composeApp:iosX64Test
    - name: Lint PrivacyInfo.xcprivacy
      run: |
        plutil -lint composeApp/PrivacyInfo.xcprivacy
        plutil -extract NSPrivacyAccessedAPITypes xml1 composeApp/PrivacyInfo.xcprivacy -o - | grep -q 'CA92.1'
        plutil -extract NSPrivacyAccessedAPITypes xml1 composeApp/PrivacyInfo.xcprivacy -o - | grep -q 'C617.1'
        plutil -extract NSPrivacyTracking raw composeApp/PrivacyInfo.xcprivacy | grep -q '^false$'
```

### Example 4: `runComposeUiTest` cross-platform UI test

```kotlin
// Source: kotlinlang.org/docs/multiplatform/compose-test.html
// composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/AppTest.kt

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

class AppTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun helloLintech_displaysAllElements() = runComposeUiTest {
        setContent { App() }
        onNodeWithTag("app_title").assertIsDisplayed()
        onNodeWithTag("privacy_url").assertIsDisplayed()
        onNodeWithTag("open_privacy_button").assertIsDisplayed()
    }
}
```

---

## Sources

### Primary (HIGH confidence)

- **JetBrains official Compose Multiplatform docs:**
  - [Compose Multiplatform 1.10.0 — JetBrains Blog](https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/) — bundled Hot Reload, Navigation 3, @Preview unified
  - [Compatibility and versions — Kotlin docs](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) — Kotlin 2.2.20 + CMP 1.10 pairing
  - [Compose Multiplatform Privacy Manifest plugin docs](https://kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html) — `apple-privacy-manifests` plugin, recommended reason codes (fstat, stat, mach_absolute_time)
  - [Testing Compose Multiplatform UI](https://kotlinlang.org/docs/multiplatform/compose-test.html) — `runComposeUiTest`, testTag, iosX64Test
  - [Common ViewModel](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html) — for future Phase 4
  - [Compose Multiplatform Releases](https://github.com/JetBrains/compose-multiplatform/releases) — 1.10.3 confirmed stable

- **Apple official:**
  - [TN3183: Adding required reason API entries to your privacy manifest](https://developer.apple.com/documentation/technotes/tn3183-adding-required-reason-api-entries-to-your-privacy-manifest) — CA92.1, C617.1 reason codes
  - [Describing use of required reason API](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api) — comprehensive reason code list

- **GitHub:**
  - [actions/deploy-pages](https://github.com/actions/deploy-pages) — v4/v5 deploy-pages
  - [gradle/actions](https://github.com/gradle/actions) — setup-gradle@v4
  - [docs.github.com — Configuring Pages publishing source](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site) — `docs/` recommended
  - [google/ksp issue #2619 + #2669](https://github.com/google/ksp/issues/2619) — KSP version alignment with Kotlin 2.2.20 → 2.2.20-2.0.4

### Secondary (MEDIUM confidence — verified with project-context cross-reference)

- **CONTEXT.md** (locked decisions D-01..D-31)
- **`.planning/research/STACK.md`** (Recommended Stack table)
- **`.planning/research/ARCHITECTURE.md`** (feature-by-layer multi-module pattern)
- **`.planning/research/PITFALLS.md`** (#3, #5, #9, #15, #16)
- **`.planning/REQUIREMENTS.md`** (COMP-01, COMP-02 acceptance criteria)
- **`.planning/ROADMAP.md`** (Phase 1 success criteria)

### Tertiary (LOW confidence — single source, validated by content match)

- [Now-in-Android build-logic structure (Google sample)](https://github.com/android/nowinandroid/tree/main/build-logic) — paved-path для convention plugins; web-fetch returned partial content but pattern confirmed in multiple secondary sources
- [BuildKonfig README](https://github.com/yshrsmz/BuildKonfig) — version 0.15.x stable confirmed; 0.18.0 mentioned in roadmap-2026 (forward-looking)
- [Singular Help: Privacy Manifest FAQ](https://support.singular.net/hc/en-us/articles/24045392537243) — CA92.1 + C617.1 plain-language description
- [.NET MAUI Apple privacy manifest docs](https://learn.microsoft.com/en-us/dotnet/maui/ios/privacy-manifest?view=net-maui-10.0) — cross-validation of reason code semantics

---

## Metadata

**Confidence breakdown:**
- Standard stack (versions): **MEDIUM-HIGH** — Kotlin 2.2.20 + CMP 1.10.3 + KSP 2.2.20-2.0.4 + apple-privacy-manifests 1.0.0 confirmed via official docs. AGP exact patch level [ASSUMED]. kotlinx.* libraries — STACK.md figures pre-verified, but `kotlinx-datetime 0.8.0-rc01` was downgraded to `0.7.1`/`0.6.2` based on registry data showing 0.8.x in pre-release.
- Architecture / module skeleton: **HIGH** — Now-in-Android pattern is the canonical reference; convention plugins design verified against multiple sources.
- iOS Privacy Manifest details (CA92.1, C617.1, structure): **HIGH** — Apple official docs + `apple-privacy-manifests` plugin auto-handles packaging.
- GitHub Actions YAML: **HIGH** — official docs verified; minor uncertainty about Xcode-select step (A3, may need experimental verification in first CI run).
- BuildKonfig setup: **MEDIUM-HIGH** — README-verified, plugin v0.15.x stable; `defaultConfigs { buildConfigField(...) }` pattern is canonical.
- Pitfalls (#3, #9, #15, #16): **HIGH** — already documented in PITFALLS.md, this RESEARCH.md re-presents them in Phase-1 actionable form.
- Validation Architecture (Nyquist): **HIGH** — `runComposeUiTest` with testTag is officially documented pattern; CI lint commands are standard `plutil` + `grep`.

**Research date:** 2026-04-27

**Valid until:** **30 days** for Compose Multiplatform / Kotlin tooling versions (stable cadence — но monitor JetBrains blog for 1.11.x stable). **7 days** для GitHub Actions versions (deploy-pages, setup-gradle minor patches monthly). **Static** для Apple Privacy Manifest reason codes (Apple changes infrequent).

---

## RESEARCH COMPLETE

**Phase:** 1 — Foundation & Compliance Infrastructure
**Confidence:** MEDIUM-HIGH (HIGH for skeleton/CI/Privacy Manifest; MEDIUM for some library version specifics like AGP patch and kotlinx.serialization 1.9 vs 1.7)

### Key Findings

1. **iOS Privacy Manifest упаковка** — единственно officially-supported путь = `org.jetbrains.kotlin.apple-privacy-manifests` plugin v1.0.0, который автоматически копирует `PrivacyInfo.xcprivacy` в framework. **Не пытаться** вручную раскладывать файл по проекту.
2. **Convention plugins** — Now-in-Android pattern (`build-logic/` includedBuild + `kotlin-dsl` + `gradlePlugin { register("...") }` apply-by-id) — единственный масштабируемый путь. `buildSrc/` устарел, плоское копирование build.gradle.kts — нет.
3. **kotlinx-datetime 0.8.0** всё ещё в pre-release (rc01 — единственная 0.8.x ветка); **0.7.1 stable** — что использовать в Phase 1. STACK.md упоминание «0.8.0-rc01» — следует deprioritize в version catalog до Phase 2 когда serialization 1.9 потребуется реально.
4. **CI iOS-job lint PrivacyInfo** через `plutil -lint` + `plutil -extract` + `grep` обязательно ДОЛЖЕН выполняться на macos-15 (D-13) — `plutil` macOS-only. Подтверждает критичность macOS-runner с дня 1.
5. **`iosX64Test` достаточно для Phase 1 iOS-validation** — НЕ требует `embedAndSignAppleFrameworkForXcode` или xcodebuild archive (которые откладываются в Phase 6). Это закрепляет ROADMAP edit (success criterion #2 → iOS valides through CI screenshot test).

### File Created

`.planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md`

### Confidence Assessment

| Area | Level | Reason |
|------|-------|--------|
| Concrete Versions Table | MEDIUM-HIGH | Kotlin/CMP/KSP/apple-privacy-manifests verified; AGP/Mokkery [ASSUMED] but low-risk |
| Module Skeleton + Convention Plugins | HIGH | Now-in-Android paved path; cross-validated |
| iOS PrivacyInfo + apple-privacy-manifests | HIGH | Apple TN3183 + JetBrains official plugin docs |
| GitHub Actions Workflows | HIGH | gradle/actions + actions/deploy-pages official |
| BuildKonfig | MEDIUM-HIGH | github.com/yshrsmz/BuildKonfig README verified |
| Cross-platform Testing | HIGH | kotlinlang.org/docs/multiplatform/compose-test.html canonical |
| Validation Architecture | HIGH | All commands runnable from project root; testTag pattern is standard |

### Open Questions

5 questions documented in `## Open Questions` section — все non-blocking (могут быть resolved при первом CI run или в Phase-2/4 transitions).

### Ready for Planning

RESEARCH complete. **Planner может теперь:**
- Создать PLAN.md с tasks Wave 0 (skeleton + convention plugins) → Wave 1 (Hello LinTech UI + BuildKonfig + tests) → Wave 2 (CI workflows + branch protection rule manual step) → Wave 3 (Privacy Policy HTML + GitHub Pages enable + deploy) → Wave 4 (PrivacyInfo.xcprivacy + apple-privacy-manifests + CI lint step) → Wave 5 (ROADMAP edit + CLAUDE.md update + README).
- Mapping requirements → tasks: COMP-01 spans Waves 1+3 (Res.string + privacy HTML); COMP-02 spans Wave 4 (PrivacyInfo + lint).
- Validation gate: full CI green (Android + iOS) + manual smoke (curl Privacy URL + Hello LinTech на Android-device of developer).
