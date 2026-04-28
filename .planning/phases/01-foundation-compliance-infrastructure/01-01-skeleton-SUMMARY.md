---
phase: 01-foundation-compliance-infrastructure
plan: 01
subsystem: infra
tags: [gradle, kmp, kotlin-multiplatform, compose-multiplatform, convention-plugins, version-catalog, android, ios]

requires: []

provides:
  - Reproducible Gradle multi-module build (4 KMP modules: composeApp + core:platform + core:ui + core:network)
  - Three convention plugins (lintech-kmp, lintech-compose, lintech-test) registered via includeBuild("build-logic")
  - Single-source-of-truth version catalog (gradle/libs.versions.toml) с pinned версиями (Kotlin 2.2.20, Compose Multiplatform 1.10.3, AGP 8.7.3, KSP 2.2.20-2.0.4)
  - Source set scaffold (commonMain + androidMain + iosMain + commonTest) для всех 4 модулей
  - Gradle wrapper 8.13 (bumped from 8.10 — Rule 1 deviation для совместимости kotlin-dsl с Compose Gradle Plugin 1.10.3)

affects: [01-02-hello-linteh, 01-03-ci-workflows, 01-04-privacy-policy, 01-05-privacy-manifest, 01-06-docs, all future phases (Wave 0 canonical pattern)]

tech-stack:
  added:
    - "Gradle 8.13 (wrapper)"
    - "Kotlin 2.2.20 (multiplatform)"
    - "Compose Multiplatform 1.10.3"
    - "Android Gradle Plugin 8.7.3"
    - "KSP 2.2.20-2.0.4 (registered, not yet consumed)"
    - "Mokkery 2.5.1 (test mocking)"
    - "Kotest 5.9.1 (assertions)"
    - "Turbine 1.2.1 (Flow tests)"
    - "BuildKonfig 0.15.2 (registered, applied in Plan 02)"
    - "apple-privacy-manifests 1.0.0 (registered, applied in Plan 05)"
  patterns:
    - "Now in Android-style includeBuild(\"build-logic\") + convention plugins (apply-by-id)"
    - "Hierarchical module naming :core:* (NOT flat)"
    - "Version catalog as SSoT — no version literals in module build.gradle.kts"
    - "Convention plugins composable: lintech-kmp + lintech-compose + lintech-test"
    - "iOS static frameworks (SwiftPM-friendly, NOT CocoaPods — D-05)"

key-files:
  created:
    - "settings.gradle.kts (4 modules + includeBuild)"
    - "build.gradle.kts (root — alias plugins apply false)"
    - "gradle.properties (versionName=0.1.0, JDK17, KMP defaults)"
    - "gradle/libs.versions.toml (single source of truth для versions)"
    - "gradle/wrapper/gradle-wrapper.{jar,properties} (Gradle 8.13)"
    - "gradlew, gradlew.bat (wrapper scripts)"
    - ".gitignore (secrets + build artifacts)"
    - "build-logic/settings.gradle.kts"
    - "build-logic/convention/build.gradle.kts (gradlePlugin registry)"
    - "build-logic/convention/src/main/kotlin/LintechKmpConventionPlugin.kt"
    - "build-logic/convention/src/main/kotlin/LintechComposeConventionPlugin.kt"
    - "build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt"
    - "build-logic/convention/src/main/kotlin/ext/KotlinExt.kt"
    - "build-logic/convention/src/main/kotlin/ext/AndroidExt.kt"
    - "composeApp/build.gradle.kts (KMP application)"
    - "composeApp/src/androidMain/AndroidManifest.xml (stub)"
    - "core/platform/build.gradle.kts (KMP library, 2-line plugins block)"
    - "core/ui/build.gradle.kts (KMP library + compose, 3-line plugins block)"
    - "core/network/build.gradle.kts (KMP library, 2-line plugins block)"
    - "Source set scaffolding (10 .gitkeep entries) — commonMain/commonTest/androidMain/iosMain/composeResources/values"
  modified:
    - ".planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md (Per-Task Verification Map filled with 12 task rows; wave_0_complete: true)"

key-decisions:
  - "Gradle wrapper bumped 8.10 → 8.13 для совместимости kotlin-dsl (Kotlin 2.0.21 в 8.13) с Compose Gradle Plugin 1.10.3 binary metadata (Kotlin 2.0+)"
  - "Root build.gradle.kts применяет ВСЕ used plugins через alias(...) apply false, чтобы convention plugins могли вызывать pluginManager.apply(\"dev.mokkery\") без resolve-ошибок"
  - "composeApp НЕ применяет lintech-kmp (это application module, не library): использует raw plugins из version catalog; lintech-test применяется напрямую"
  - "core/* модули применяют convention plugins ровно по 2-3 строки (success criterion #5: ≤5 строк на модуль выполнен)"
  - "ext/KotlinExt.kt использует compileTaskProvider.configure { compilerOptions {} } (новый API), НЕ deprecated compilerOptions.configure"
  - "local.properties добавлен с sdk.dir=~/Android/Sdk (gitignored, разработческая машина-специфичный файл)"

patterns-established:
  - "Convention plugin pattern: добавление нового KMP-модуля = создать build.gradle.kts с id(\"lintech-kmp\") + опционально id(\"lintech-compose\") + id(\"lintech-test\") + минимальный kotlin{} блок с targets и android{} с namespace"
  - "Plugin registration: все плагины из libs.versions.toml [plugins] объявляются в root build.gradle.kts с apply false → доступны convention plugins через pluginManager.apply(\"<id>\") без classpath гимнастики"
  - "iOS framework export: iosX64 + iosArm64 + iosSimulatorArm64, isStatic=true, baseName per module — paved path для SwiftPM-интеграции в Plan 02+"
  - "Package root namespace = io.github.chudoxl.linteh.journal[.{module-suffix}] — D-08/D-09"

requirements-completed: [COMP-01, COMP-02]

duration: 15min
completed: 2026-04-28
---

# Phase 01 Plan 01: Multi-Module Gradle Skeleton Summary

**Воспроизводимый Gradle multi-module skeleton с 4 KMP-модулями, convention plugins (lintech-kmp/-compose/-test), version catalog с pinned Kotlin 2.2.20 + Compose Multiplatform 1.10.3 — `assembleDebug` и `compileKotlinIosX64` собираются на Linux Mint без Xcode**

## Performance

- **Duration:** 15 min
- **Started:** 2026-04-28T03:10:28Z
- **Completed:** 2026-04-28T03:25:22Z
- **Tasks:** 3
- **Files created:** 30 (включая 10 .gitkeep stubs)
- **Files modified:** 1 (VALIDATION.md)

## Accomplishments

- **Convention plugins работают end-to-end:** `:core:platform/build.gradle.kts` имеет ровно 2 строки `id("lintech-kmp")` + `id("lintech-test")`, `:core:ui` — 3 строки (+`id("lintech-compose")`), `:core:network` — 2 строки. Success criterion #5 (≤5 строк) выполнен.
- **Воспроизводимая сборка обоих таргетов:** `./gradlew assembleDebug` (Android, 131 tasks, 2m 51s cold) и `./gradlew compileKotlinIosX64` (iOS X64, 46s после warm-up Konan) проходят на Linux Mint без Xcode. Это закрывает критерий A4 (iOS X64 compile pipeline валидируется без Mac).
- **Version catalog как SSoT:** `gradle/libs.versions.toml` содержит 18 versions + 12 libraries + 10 plugins. Никаких version literals в module build.gradle.kts — все ссылки через `libs.versions.*` или `libs.plugins.*`.
- **Wave 0 разблокирован:** `01-VALIDATION.md` имеет `wave_0_complete: true` в frontmatter; Per-Task Verification Map заполнена 12 task-rows для всех 6 phase plans (01-01..01-06). `nyquist_compliant: false` остаётся до Plan 06 close-out (BLOCKER 5 contract).

## Task Commits

1. **Task 1: Initialize Gradle wrapper, version catalog, root build files, .gitignore** — `5b883e1` (feat)
2. **Task 2: Create build-logic includedBuild with three convention plugins** — `8315dcf` (feat) — включает Rule 1 deviation (Gradle bump 8.10→8.13)
3. **Task 3: Create 4 KMP module skeletons + verify full Gradle assembly** — `e737e4f` (feat) — включает Rule 3 deviation (local.properties для Android SDK)

## Files Created/Modified

### Build configuration
- `settings.gradle.kts` — root settings: `pluginManagement.includeBuild("build-logic")` + 4 modules
- `build.gradle.kts` — root build: alias plugins с `apply false` (kotlinMultiplatform, kotlinAndroid, kotlinComposeCompiler, composeMultiplatform, androidApplication, androidLibrary, mokkery)
- `gradle.properties` — versionName=0.1.0, JVM args, caching/parallel, KMP `applyDefaultHierarchyTemplate=true`
- `gradle/libs.versions.toml` — version catalog: 18 versions + 12 libraries + 10 plugins (всё pinned per RESEARCH.md "Concrete Versions Table")
- `gradle/wrapper/gradle-wrapper.{jar,properties}` — Gradle 8.13 wrapper
- `gradlew`, `gradlew.bat` — wrapper scripts
- `.gitignore` — Gradle/Android/iOS/secrets patterns

### Build-logic (convention plugins)
- `build-logic/settings.gradle.kts` — versionCatalogs.from(`../gradle/libs.versions.toml`)
- `build-logic/convention/build.gradle.kts` — kotlin-dsl + JDK17 + gradlePlugin registry (3 plugins: lintech-kmp, lintech-compose, lintech-test)
- `build-logic/convention/src/main/kotlin/LintechKmpConventionPlugin.kt` — applies kotlin.multiplatform + android.library, calls configureKotlinMultiplatform() + configureAndroidLibrary()
- `build-logic/convention/src/main/kotlin/LintechComposeConventionPlugin.kt` — applies compose + kotlin.plugin.compose, добавляет runtime/foundation/material3/resources/uiToolingPreview
- `build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt` — applies dev.mokkery, добавляет kotlin.test + kotest-assertions + turbine в commonTest. **NB:** compose.uiTest здесь НЕ добавляется (BLOCKER 1 mitigation — добавится в composeApp вручную в Plan 02 Task 2)
- `build-logic/convention/src/main/kotlin/ext/KotlinExt.kt` — configureKotlinMultiplatform(): jvmToolchain(17), -Xexpect-actual-classes
- `build-logic/convention/src/main/kotlin/ext/AndroidExt.kt` — configureAndroidLibrary(): compileSdk=35, minSdk=26, JDK17 source/target

### KMP modules
- `composeApp/build.gradle.kts` — KMP application: alias plugins (kotlinMultiplatform + androidApplication + kotlinComposeCompiler + composeMultiplatform) + `id("lintech-test")`; targets android+iosX64+iosArm64+iosSimulatorArm64 (isStatic=true, baseName="ComposeApp"); commonMain wires :core:platform + :core:ui; namespace + applicationId="io.github.chudoxl.linteh.journal"
- `composeApp/src/androidMain/AndroidManifest.xml` — stub `<application>` (Activity появится в Plan 02)
- `core/platform/build.gradle.kts` — `id("lintech-kmp") + id("lintech-test")`; targets android+ios×3; namespace=`...core.platform`
- `core/ui/build.gradle.kts` — `id("lintech-kmp") + id("lintech-compose") + id("lintech-test")`; namespace=`...core.ui`
- `core/network/build.gradle.kts` — `id("lintech-kmp") + id("lintech-test")`; namespace=`...core.network`
- 10 `.gitkeep` файлов в `commonMain/commonTest/androidMain/iosMain` source set directories для всех 4 модулей

### Documentation
- `.planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md` — frontmatter `wave_0_complete: true`; Per-Task Verification Map populated с 12 rows (01-01-01..01-06-01)

## Decisions Made

- **Gradle 8.10 → 8.13**: kotlin-dsl plugin в Gradle 8.10 использует Kotlin 1.9.24, что вызывает binary metadata incompatibility с Compose Gradle Plugin 1.10.3 (compiled with Kotlin 2.1.0+). Gradle 8.13 имеет Kotlin 2.0.21 в kotlin-dsl — резолвит. RESEARCH.md "Concrete Versions Table" указывал минимум 8.7 и "рекомендую 8.10"; теперь baseline = 8.13.
- **Root plugins block с `apply false`**: convention plugin `lintech-test` применяет `dev.mokkery`, но Gradle plugin resolution требует, чтобы plugin был известен в pluginManagement. Способ зарегистрировать его — объявить в root build.gradle.kts через `alias(libs.plugins.mokkery) apply false`. Это same pattern как Now in Android. Применили ко всем используемым плагинам (mokkery + KMP + compose + Android).
- **`compileTaskProvider.configure { compilerOptions {} }` вместо `compilerOptions.configure {}`**: Kotlin Gradle Plugin 2.2 deprecated старый API; новый — через `compileTaskProvider`. Применили в `KotlinExt.kt`.
- **composeApp НЕ применяет lintech-kmp**: convention plugin lintech-kmp применяет `com.android.library`, а composeApp — application module (`com.android.application`). Используем raw plugins (alias) в composeApp; lintech-test применяется напрямую (он не зависит от android-library).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Bumped Gradle wrapper 8.10 → 8.13 (Kotlin metadata incompatibility)**
- **Found during:** Task 2 (`./gradlew :build-logic:convention:assemble`)
- **Issue:** Compose Gradle Plugin 1.10.3 (compiled with Kotlin 2.1.0) и Compose Hot Reload Gradle Plugin 1.0.0 (compiled with Kotlin 2.1.0) не совместимы с binary metadata version 1.9.0 (Kotlin 1.9.x) — а Gradle 8.10 kotlin-dsl собран на Kotlin 1.9.24. Convention plugins не компилируются.
- **Fix:** `./gradlew wrapper --gradle-version 8.13 --distribution-type bin` — Gradle 8.13 имеет Kotlin 2.0.21 в kotlin-dsl, что совместимо с Kotlin 2.1.0+ binary metadata.
- **Files modified:** `gradle/wrapper/gradle-wrapper.{jar,properties}`, `gradlew`, `gradlew.bat`
- **Verification:** `./gradlew :build-logic:convention:assemble` → BUILD SUCCESSFUL
- **Committed in:** `8315dcf` (Task 2 commit)

**2. [Rule 3 - Blocking] Created `local.properties` pointing to ~/Android/Sdk**
- **Found during:** Task 3 (`./gradlew assembleDebug`)
- **Issue:** AGP не находит SDK location, ругается «Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local properties file». Без этого assembleDebug не может скомпилировать Android-таргет.
- **Fix:** Создан `local.properties` с `sdk.dir=/home/chudoxl/Android/Sdk`. Файл уже в `.gitignore` (Task 1).
- **Files modified:** `local.properties` (gitignored — не commit-ится)
- **Verification:** `./gradlew assembleDebug` → BUILD SUCCESSFUL (131 tasks, 2m 51s)
- **Committed in:** N/A (gitignored)

**3. [Rule 2 - Quality fix] Replaced deprecated `compilerOptions.configure {}` with `compileTaskProvider.configure { compilerOptions {} }`**
- **Found during:** Task 2 (compile of convention plugins emitted deprecation warning)
- **Issue:** Kotlin Gradle Plugin 2.2 deprecated `HasCompilerOptions<*>` direct configure pattern. Сохранение старого API оставит deprecation warning во всех module builds в долгосрочной перспективе.
- **Fix:** Обновлён `build-logic/convention/src/main/kotlin/ext/KotlinExt.kt` использовать `compilation.compileTaskProvider.configure { compilerOptions { ... } }` (новый рекомендуемый API).
- **Verification:** Повторный `./gradlew :build-logic:convention:assemble` — BUILD SUCCESSFUL без warnings.
- **Committed in:** `8315dcf` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (1 bug, 1 blocking, 1 quality)
**Impact on plan:** Все 3 deviations необходимы для корректности и устойчивости. Никакого scope creep — все правки в рамках Wave 0 baseline.

## Issues Encountered

- **Mokkery 2.5.1 compiled с Kotlin 2.0.21, runtime — Kotlin 2.2.20**: Gradle выдаёт warning «Mokkery was compiled against Kotlin 2.0.21, but the current version is 2.2.20! It might cause compatibility issues». Сборка прошла, но warning стоит мониторить. Если появятся NoSuchMethodError — обновить Mokkery до версии, скомпилированной на Kotlin 2.2 (на момент Phase 1 такой нет в реестре). Можно подавить через `dev.mokkery.versionWarnings=false` в gradle.properties в будущем.
- **`Kotlin/Native bundle directory ~/.konan/kotlin-native-prebuilt-linux-x86_64-2.2.20 is not empty`**: Gradle вывод сообщения «Native bundle files will be overwritten» — это нормальное поведение при первом запуске Konan на чистой dev-машине. Не блокирует.

## User Setup Required

None — local.properties настроен автоматически (sdk.dir=~/Android/Sdk). Никакой ручной настройки внешних сервисов в Phase 1 не требуется.

## Next Phase Readiness (что должен знать Plan 02)

### Где expect-функция должна быть положена

**Plan 02 Task 1** (Hello LinTech / openUrl expect/actual):
- `core/platform/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.kt` — `expect fun openUrl(url: String): Unit`
- `core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt` — `actual fun openUrl(url: String)` — Intent.ACTION_VIEW (потребует `implementation(libs.androidx.core.ktx)` в `core/platform/build.gradle.kts` androidMain)
- `core/platform/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.ios.kt` — `actual fun openUrl(url: String)` — UIApplication.sharedApplication.openURL

Все source set directories уже существуют (с .gitkeep stubs).

### Где App() composable должен быть

**Plan 02 Task 2:**
- `composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/App.kt` — `@Composable fun App() { ... }`
- `composeApp/src/commonMain/composeResources/values/strings.xml` — `Res.string.privacy_policy_url`
- `composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainActivity.kt` — Activity host
- `composeApp/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/MainViewController.kt` — UIViewController factory

### Где AppTest должен быть

**Plan 02 Task 2 (UI smoke test):**
- `composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/AppTest.kt` — `runComposeUiTest { ... }` с testTag-селекторами `"hello-linteh-screen"` и `"open-privacy-button"`
- В composeApp/build.gradle.kts нужно добавить `compose.uiTest` в commonTest dependencies явно (BLOCKER 1 contract из PLAN.md):

```kotlin
sourceSets {
    commonTest.dependencies {
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)
    }
}
```

### Версии плагинов готовы для подключения в Plan 02 / Plan 05

- `com.codingfeline.buildkonfig` 0.15.2 — зарегистрирован в version catalog [plugins]; Plan 02 применяет в composeApp/build.gradle.kts
- `org.jetbrains.kotlin.apple-privacy-manifests` 1.0.0 — зарегистрирован; Plan 05 применяет в composeApp/build.gradle.kts (privacyManifest{} block)

### Известные ограничения для Plan 03 (CI)

- CI workflow должен установить ANDROID_HOME (например, через `android-actions/setup-android@v3`) и запустить `./gradlew assembleDebug` — local.properties не commit-ится, поэтому в CI используем env var.
- macOS-CI runner для iosX64Test обязателен — Linux Mint dev-host не имеет Xcode (D-13 / D-15).

## Self-Check: PASSED

**Files verification:**
- `settings.gradle.kts` ✅ FOUND
- `build.gradle.kts` ✅ FOUND
- `gradle.properties` ✅ FOUND
- `gradle/libs.versions.toml` ✅ FOUND
- `gradle/wrapper/gradle-wrapper.{jar,properties}` ✅ FOUND
- `gradlew`, `gradlew.bat` ✅ FOUND
- `.gitignore` ✅ FOUND
- `build-logic/settings.gradle.kts` ✅ FOUND
- `build-logic/convention/build.gradle.kts` ✅ FOUND
- `build-logic/convention/src/main/kotlin/LintechKmpConventionPlugin.kt` ✅ FOUND
- `build-logic/convention/src/main/kotlin/LintechComposeConventionPlugin.kt` ✅ FOUND
- `build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt` ✅ FOUND
- `build-logic/convention/src/main/kotlin/ext/KotlinExt.kt` ✅ FOUND
- `build-logic/convention/src/main/kotlin/ext/AndroidExt.kt` ✅ FOUND
- `composeApp/build.gradle.kts` ✅ FOUND
- `composeApp/src/androidMain/AndroidManifest.xml` ✅ FOUND
- `core/{platform,ui,network}/build.gradle.kts` ✅ FOUND (3/3)
- `.planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md` ✅ FOUND (modified)

**Commits verification:**
- `5b883e1` (Task 1) ✅ FOUND in git log
- `8315dcf` (Task 2) ✅ FOUND in git log
- `e737e4f` (Task 3) ✅ FOUND in git log

**Build verification:**
- `./gradlew --version` → Gradle 8.13 ✅ PASS
- `./gradlew :build-logic:convention:assemble` → BUILD SUCCESSFUL ✅ PASS
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (131 tasks) ✅ PASS
- `./gradlew compileKotlinIosX64` → BUILD SUCCESSFUL ✅ PASS

---

*Phase: 01-foundation-compliance-infrastructure*
*Completed: 2026-04-28*
