---
phase: 01-foundation-compliance-infrastructure
plan: 02
type: execute
wave: 1
depends_on:
  - 01-01-skeleton-PLAN
files_modified:
  - composeApp/build.gradle.kts
  - composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/App.kt
  - composeApp/src/commonMain/composeResources/values/strings.xml
  - composeApp/src/androidMain/AndroidManifest.xml
  - composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainActivity.kt
  - composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainApplication.kt
  - composeApp/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/MainViewController.kt
  - composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/AppTest.kt
  - core/platform/build.gradle.kts
  - core/platform/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.kt
  - core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt
  - core/platform/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.ios.kt
autonomous: true
requirements:
  - COMP-01
must_haves:
  truths:
    - "Hello LinTech-экран показывает текст «ЛИнТех Дневник», версию (BuildKonfig.VERSION_NAME + VERSION_CODE), Privacy Policy URL и кнопку «Открыть»"
    - "Кнопка «Открыть» вызывает expect/actual openUrl(...) — на Android открывает Intent.ACTION_VIEW; на iOS — UIApplication.openURL"
    - "BuildKonfig generates `object BuildKonfig { const val VERSION_NAME, VERSION_CODE, IS_DEBUG }` в commonMain perspective"
    - "AppTest через runComposeUiTest проходит на iosX64Test и Android JVM-test"
    - "Все UI-элементы имеют testTag-модификаторы (app_title, app_version, privacy_url, open_privacy_button)"
    - "MainActivity на Android и MainViewController на iOS оборачивают App() composable"
  artifacts:
    - path: "composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/App.kt"
      provides: "@Composable App() — Hello LinTech screen"
      contains: "fun App()"
      min_lines: 30
    - path: "composeApp/src/commonMain/composeResources/values/strings.xml"
      provides: "Compose Resources strings (privacy_policy_url, app_name)"
      contains: "privacy_policy_url"
    - path: "composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainActivity.kt"
      provides: "Android entry point — sets content { App() }"
      contains: "class MainActivity"
    - path: "composeApp/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/MainViewController.kt"
      provides: "iOS entry point — ComposeUIViewController { App() }"
      contains: "fun MainViewController()"
    - path: "core/platform/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.kt"
      provides: "expect fun openUrl(url: String)"
      contains: "expect fun openUrl"
    - path: "core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt"
      provides: "Android actual openUrl using Intent.ACTION_VIEW"
      contains: "actual fun openUrl"
    - path: "core/platform/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.ios.kt"
      provides: "iOS actual openUrl using UIApplication.sharedApplication.openURL"
      contains: "actual fun openUrl"
    - path: "composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/AppTest.kt"
      provides: "Compose UI smoke test для Hello LinTech (cross-platform)"
      contains: "runComposeUiTest"
  key_links:
    - from: "composeApp/src/commonMain/kotlin/.../App.kt"
      to: ":core:platform openUrl"
      via: "import + button onClick"
      pattern: "import io\\.github\\.chudoxl\\.linteh\\.journal\\.core\\.platform\\.openUrl"
    - from: "composeApp/src/commonMain/kotlin/.../App.kt"
      to: "BuildKonfig.VERSION_NAME / VERSION_CODE"
      via: "Compose composable Text"
      pattern: "BuildKonfig\\.VERSION_(NAME|CODE)"
    - from: "composeApp/src/commonMain/kotlin/.../App.kt"
      to: "Res.string.privacy_policy_url"
      via: "stringResource(...)"
      pattern: "Res\\.string\\.privacy_policy_url"
    - from: "composeApp/src/androidMain/kotlin/.../MainApplication.kt"
      to: "androidApplicationContext (in :core:platform)"
      via: "lateinit var assignment in Application.onCreate"
      pattern: "androidApplicationContext\\s*=\\s*applicationContext"
---

<objective>
Реализовать первый working экран приложения — Hello LinTech composable — с поддержкой обеих платформ. Экран показывает: название «ЛИнТех Дневник», версию из BuildKonfig (VERSION_NAME + VERSION_CODE), Privacy Policy URL текстом, кнопку «Открыть», которая открывает URL в системном браузере через expect/actual `openUrl(...)`. Параллельно: настраивается BuildKonfig plugin в `:composeApp`, expect/actual `openUrl` в `:core:platform`, `runComposeUiTest` smoke-test в `commonTest`.

Purpose: Plan 02 — первый функциональный код в проекте. Закрывает COMP-01 success criterion «линк виден из проекта» (через UI кнопку + строку ресурсов). Устанавливает canonical patterns для последующих фаз: expect/actual templates (Phase 3 KVault, Phase 6 BGAppRefreshTask будут копировать), Compose Resources как локализационный слой, BuildKonfig как version source-of-truth, testTag-based UI tests.

Output: Working «Hello LinTech» screen на Android (запускается на устройстве разработчика) и iOS-side compile-clean + iosX64Test passing в CI (Plan 03).
</objective>

<execution_context>
@/home/chudoxl/src/LintehJournal/.claude/get-shit-done/workflows/execute-plan.md
@/home/chudoxl/src/LintehJournal/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md
@.planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md
@.planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md
@.planning/phases/01-foundation-compliance-infrastructure/01-01-SUMMARY.md
@.planning/research/STACK.md
@CLAUDE.md

<interfaces>
<!-- Convention plugins установлены в Plan 01 — применяем по id. -->
<!-- Plugin id mapping (создан в Plan 01): -->
<!-- - lintech-kmp → KMP + Android library, jvmToolchain 17, freeCompilerArgs += -Xexpect-actual-classes -->
<!-- - lintech-compose → org.jetbrains.compose + kotlin-compose-compiler, добавляет compose.runtime/foundation/material3/components.resources в commonMain -->
<!-- - lintech-test → dev.mokkery, kotlin.test + kotest.assertions + turbine в commonTest (compose.uiTest добавляется отдельно в `:composeApp/build.gradle.kts` commonTest — BLOCKER 1 fix iter 1) -->

<!-- expect/actual API контракт (создаётся в Task 1): -->
```kotlin
// :core:platform/src/commonMain/kotlin/.../UrlOpener.kt
package io.github.chudoxl.linteh.journal.core.platform

expect fun openUrl(url: String)

// Android implementation pattern:
// internal lateinit var androidApplicationContext: Context  // top-level (D-31, RESEARCH Open Question #4)
// MainApplication.onCreate() инициализирует androidApplicationContext = applicationContext

// iOS implementation pattern:
// UIApplication.sharedApplication.openURL(NSURL.URLWithString(url) ?: return, options=emptyMap, completionHandler=null)
```

<!-- BuildKonfig generated object (создаётся плагином в Task 2): -->
```kotlin
// generated → io.github.chudoxl.linteh.journal.BuildKonfig
package io.github.chudoxl.linteh.journal
object BuildKonfig {
    val VERSION_NAME: String  // "0.1.0"
    val VERSION_CODE: String  // github.run_number или git rev-list count
    val IS_DEBUG: Boolean     // true в Phase 1
}
```

<!-- Compose Resources access pattern: -->
```kotlin
import linteh_journal.composeapp.generated.resources.Res
import linteh_journal.composeapp.generated.resources.app_name
import linteh_journal.composeapp.generated.resources.privacy_policy_url
import org.jetbrains.compose.resources.stringResource

stringResource(Res.string.app_name)  // "ЛИнТех Дневник"
stringResource(Res.string.privacy_policy_url)  // "https://chudoxl.github.io/LintehJournal/privacy/"
```
</interfaces>
</context>

## Tasks

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Implement expect/actual openUrl in :core:platform with MainApplication wiring</name>
  <files>
    core/platform/build.gradle.kts,
    core/platform/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.kt,
    core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt,
    core/platform/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.ios.kt,
    composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainApplication.kt,
    composeApp/src/androidMain/AndroidManifest.xml
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section ":core:platform — expect/actual openUrl" — все три варианта commonMain/androidMain/iosMain verbatim; section "Wire MainActivity → androidApplicationContext")
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (D-31 URL handling; D-09 sub-package naming; D-12 iOS deployment target 14)
    - core/platform/build.gradle.kts (создан в Plan 01 — нужно обновить чтобы добавить androidx.core.ktx в androidMain dependencies)
    - composeApp/src/androidMain/AndroidManifest.xml (создан в Plan 01 — нужно обновить чтобы зарегистрировать MainApplication)
  </read_first>
  <behavior>
    - Test 1: openUrl("https://example.com") на Android вызывает Intent.ACTION_VIEW с FLAG_ACTIVITY_NEW_TASK через androidApplicationContext.startActivity (verifiable through MainApplication initialization OR androidUnitTest mock)
    - Test 2: openUrl("https://example.com") на iOS вызывает UIApplication.sharedApplication.openURL(NSURL.URLWithString("https://example.com"), options, completion)
    - Test 3: openUrl("not-a-valid-url") на iOS не падает (NSURL.URLWithString возвращает null → early return, no crash) — defensive behavior

    Phase 1 testing scope: expect/actual ВЫЗОВ verifiable через `runComposeUiTest` в Plan-02-Task-3 (нажатие кнопки → openUrl invoked, no exception thrown). Полный integration test (фактический Intent dispatch / UIApplication call) НЕ автоматизирован в Phase 1 — Android requires Robolectric/instrumentation, iOS requires real device. Acceptance: compile + smoke (no exception thrown).
  </behavior>
  <action>
    1. **Update `core/platform/build.gradle.kts`** — добавить androidx.core.ktx в androidMain dependencies (нужен для Intent/Uri):
    ```kotlin
    plugins {
        id("lintech-kmp")
        id("lintech-test")
    }

    kotlin {
        androidTarget()
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "platform"
                isStatic = true
            }
        }

        sourceSets {
            androidMain.dependencies {
                implementation(libs.androidx.core.ktx)
            }
        }
    }

    android {
        namespace = "io.github.chudoxl.linteh.journal.core.platform"
        compileSdk = 35
        defaultConfig { minSdk = 26 }
    }
    ```

    2. **Создать `core/platform/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.kt`** (verbatim из RESEARCH.md ":core:platform — expect/actual openUrl"):
    ```kotlin
    package io.github.chudoxl.linteh.journal.core.platform

    /**
     * Открывает URL в системном браузере.
     * Phase 1: используется кнопкой «Открыть» на Hello LinTech screen для Privacy Policy URL.
     * Phase 4+: реиспользуется для всех внешних ссылок (поддержка, AVERS-сайт, etc.).
     */
    expect fun openUrl(url: String)
    ```

    3. **Создать `core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt`** (verbatim per RESEARCH.md):
    ```kotlin
    package io.github.chudoxl.linteh.journal.core.platform

    import android.content.Context
    import android.content.Intent
    import android.net.Uri

    /**
     * Контекст пробрасывается через top-level mutable property в Phase 1 — pragmatic bootstrap-bridge.
     * Phase 4: заменится на CompositionLocal из LocalContext.current вместе с Koin DI и Navigation 3.
     * См. RESEARCH.md Open Question #4 для обоснования.
     *
     * TODO(Phase 4): Refactor to CompositionLocal-based access.
     */
    internal lateinit var androidApplicationContext: Context

    actual fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        androidApplicationContext.startActivity(intent)
    }
    ```

    4. **Создать `core/platform/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.ios.kt`** (verbatim per RESEARCH.md, с правильной сигнатурой `openURL:options:completionHandler:` для iOS 10+):
    ```kotlin
    package io.github.chudoxl.linteh.journal.core.platform

    import platform.Foundation.NSURL
    import platform.UIKit.UIApplication

    actual fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return  // defensive: invalid URL → no-op
        UIApplication.sharedApplication.openURL(
            url = nsUrl,
            options = emptyMap<Any?, Any?>(),
            completionHandler = null,
        )
    }
    ```

    5. **Создать `composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainApplication.kt`** (per RESEARCH.md "Wire MainActivity → androidApplicationContext"):
    ```kotlin
    package io.github.chudoxl.linteh.journal

    import android.app.Application
    import io.github.chudoxl.linteh.journal.core.platform.androidApplicationContext

    /**
     * Bootstrap Application — инициализирует platform-level context для openUrl().
     * Phase 4 заменит на Koin DI инициализацию.
     */
    class MainApplication : Application() {
        override fun onCreate() {
            super.onCreate()
            androidApplicationContext = applicationContext
        }
    }
    ```

    6. **Update `composeApp/src/androidMain/AndroidManifest.xml`** — register MainApplication + MainActivity (последний создаётся в Task 3):
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
        <application
            android:name=".MainApplication"
            android:label="ЛИнТех Дневник"
            android:icon="@android:drawable/sym_def_app_icon"
            android:supportsRtl="true">
            <activity
                android:name=".MainActivity"
                android:exported="true"
                android:label="ЛИнТех Дневник">
                <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" />
                </intent-filter>
            </activity>
        </application>
    </manifest>
    ```
    Note: `android:icon="@android:drawable/sym_def_app_icon"` — system placeholder; реальная иконка появится в Phase 4 (UI shell). exported=true обязательно для launcher activity на Android 12+.

    7. Verify compile: `./gradlew :core:platform:compileKotlinAndroid :core:platform:compileKotlinIosX64`. Должны пройти без unresolved reference. Проверка совместимости actual ↔ expect signature.
  </action>
  <verify>
    <automated>./gradlew :core:platform:compileKotlinAndroid 2>&1 | grep -qE "BUILD SUCCESSFUL" && ./gradlew :core:platform:compileKotlinIosX64 2>&1 | grep -qE "BUILD SUCCESSFUL" && grep -q '^expect fun openUrl' core/platform/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.kt && grep -q '^actual fun openUrl' core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt && grep -q '^actual fun openUrl' core/platform/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.ios.kt && grep -q 'class MainApplication' composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainApplication.kt && grep -q 'android:name=".MainApplication"' composeApp/src/androidMain/AndroidManifest.xml</automated>
  </verify>
  <acceptance_criteria>
    - File `core/platform/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.kt` contains exact line `expect fun openUrl(url: String)` with package `io.github.chudoxl.linteh.journal.core.platform`
    - File `core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt` contains `actual fun openUrl(url: String)` and uses `Intent.ACTION_VIEW` and `Intent.FLAG_ACTIVITY_NEW_TASK`
    - File `core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt` contains `internal lateinit var androidApplicationContext: Context` (top-level — D-31 + RESEARCH Open Question #4)
    - File `core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.android.kt` contains `TODO(Phase 4)` comment referencing CompositionLocal refactor
    - File `core/platform/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.ios.kt` contains `actual fun openUrl(url: String)` and uses `UIApplication.sharedApplication.openURL` with `options = emptyMap` and `completionHandler = null`
    - File `core/platform/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.ios.kt` contains `NSURL.URLWithString(url) ?: return` (defensive null-handling)
    - File `composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainApplication.kt` defines `class MainApplication : Application()` with `onCreate()` setting `androidApplicationContext = applicationContext`
    - File `composeApp/src/androidMain/AndroidManifest.xml` contains `android:name=".MainApplication"` in `<application>` element
    - File `composeApp/src/androidMain/AndroidManifest.xml` declares `<activity android:name=".MainActivity" android:exported="true">` with LAUNCHER intent-filter
    - File `core/platform/build.gradle.kts` contains `implementation(libs.androidx.core.ktx)` in androidMain.dependencies
    - `./gradlew :core:platform:compileKotlinAndroid :core:platform:compileKotlinIosX64` exits 0 (expect/actual matching правильный)
    - VALIDATION.md Per-Task Verification Map row `01-02-01` финализируется в Plan 06 close-out после first green CI run (BLOCKER 3 — Plan 02 closure НЕ зависит от CI run; локальный compile-clean = closure)
  </acceptance_criteria>
  <done>
    expect/actual `openUrl(url: String)` готов на обеих платформах; Android-side wiring (MainApplication + AndroidManifest) подключён; compile-clean на android + iosX64 targets. Готово к импорту из `:composeApp` App() composable.
  </done>
</task>

<task type="auto">
  <name>Task 2: Configure BuildKonfig + Compose Resources strings.xml + composeApp build.gradle.kts updates</name>
  <files>
    composeApp/build.gradle.kts,
    composeApp/src/commonMain/composeResources/values/strings.xml
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section "BuildKonfig Setup" — `composeApp/build.gradle.kts` BuildKonfig блок verbatim; section "Hello LinTech Composable → strings.xml")
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (D-21 GitHub Pages URL, D-22 RU only, D-24 strings.xml structure, D-29 BuildKonfig в :composeApp, D-30 versioning scheme)
    - composeApp/build.gradle.kts (создан в Plan 01 — обновляется здесь)
    - gradle/libs.versions.toml (создан в Plan 01 — содержит buildkonfig plugin alias)
    - gradle.properties (создан в Plan 01 — versionName=0.1.0)
  </read_first>
  <action>
    1. **Update `composeApp/build.gradle.kts`** — добавить BuildKonfig plugin + конфиг (PrivacyManifest plugin откладывается на Plan 05):
    ```kotlin
    import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
    import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING

    plugins {
        alias(libs.plugins.kotlinMultiplatform)
        alias(libs.plugins.androidApplication)
        alias(libs.plugins.kotlinComposeCompiler)
        alias(libs.plugins.composeMultiplatform)
        alias(libs.plugins.buildkonfig)
        id("lintech-test")
    }

    kotlin {
        androidTarget()
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }

        sourceSets {
            commonMain.dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(project(":core:platform"))
                implementation(project(":core:ui"))
            }
            androidMain.dependencies {
                implementation(libs.androidx.activity.compose)
            }
            commonTest.dependencies {
                // BLOCKER 1 mitigation iter 1: compose.uiTest добавляется здесь, НЕ в LintechTestConventionPlugin —
                // composeApp уже применяет org.jetbrains.compose plugin, поэтому ComposePlugin.Dependencies
                // resolve-ится без circular dependency на non-UI модули (:core:platform, :core:network).
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
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
            versionCode = (System.getenv("GITHUB_RUN_NUMBER")
                ?: providers.exec {
                    commandLine("git", "rev-list", "--count", "HEAD")
                }.standardOutput.asText.get().trim().ifBlank { "1" }
                ).toInt()
            versionName = providers.gradleProperty("versionName").get()
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    buildkonfig {
        packageName = "io.github.chudoxl.linteh.journal"
        objectName = "BuildKonfig"

        defaultConfigs {
            buildConfigField(
                type = STRING,
                name = "VERSION_NAME",
                value = providers.gradleProperty("versionName").get(),
            )
            buildConfigField(
                type = STRING,
                name = "VERSION_CODE",
                value = (System.getenv("GITHUB_RUN_NUMBER")
                    ?: providers.exec {
                        commandLine("git", "rev-list", "--count", "HEAD")
                    }.standardOutput.asText.get().trim().ifBlank { "1" }),
            )
            buildConfigField(
                type = BOOLEAN,
                name = "IS_DEBUG",
                value = "true",
            )
        }
    }
    ```

    2. **Создать `composeApp/src/commonMain/composeResources/values/strings.xml`** (RU only, D-22; URL из D-21):
    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <resources>
        <string name="app_name">ЛИнТех Дневник</string>
        <string name="privacy_policy_url">https://chudoxl.github.io/LintehJournal/privacy/</string>
        <string name="open_button">Открыть</string>
    </resources>
    ```
    Note: На момент Plan 02 GitHub Pages не deployed (Plan 04 will publish). String закрепляется заранее — компилируется в .apk/.ipa. Curl-валидация URL — в Plan 04.

    3. Verify generation: `./gradlew :composeApp:generateBuildKonfig`. После этого должен появиться файл `composeApp/build/generated/buildkonfig/commonMain/io/github/chudoxl/linteh/journal/BuildKonfig.kt` с `object BuildKonfig` content.

    4. Verify compile: `./gradlew :composeApp:compileKotlinAndroid`. БUILDKONFIG-импорт `io.github.chudoxl.linteh.journal.BuildKonfig` не должен давать unresolved reference. Pitfall #7 mitigation: если ошибка «unresolved BuildKonfig» — сначала `./gradlew clean`, затем повторить.

    5. Verify Compose Resources generation: `./gradlew :composeApp:generateComposeResClass` — должен создать `Res` object в `composeApp/build/generated/compose/.../Res.kt` с `string.app_name`, `string.privacy_policy_url`, `string.open_button`.
  </action>
  <verify>
    <automated>./gradlew :composeApp:generateBuildKonfig 2>&1 | grep -qE "BUILD SUCCESSFUL" && ./gradlew :composeApp:generateComposeResClass 2>&1 | grep -qE "BUILD SUCCESSFUL" && grep -q 'alias(libs.plugins.buildkonfig)' composeApp/build.gradle.kts && grep -q 'packageName = "io.github.chudoxl.linteh.journal"' composeApp/build.gradle.kts && grep -q 'name = "VERSION_NAME"' composeApp/build.gradle.kts && grep -q 'name = "VERSION_CODE"' composeApp/build.gradle.kts && grep -q 'name = "IS_DEBUG"' composeApp/build.gradle.kts && grep -q '<string name="app_name">ЛИнТех Дневник</string>' composeApp/src/commonMain/composeResources/values/strings.xml && grep -q '<string name="privacy_policy_url">https://chudoxl.github.io/LintehJournal/privacy/</string>' composeApp/src/commonMain/composeResources/values/strings.xml && grep -q 'commonTest.dependencies' composeApp/build.gradle.kts && grep -q 'compose.uiTest' composeApp/build.gradle.kts</automated>
  </verify>
  <acceptance_criteria>
    - File `composeApp/build.gradle.kts` plugins block contains `alias(libs.plugins.buildkonfig)` (D-29 BuildKonfig in :composeApp only)
    - File `composeApp/build.gradle.kts` plugins block does NOT yet contain `apple-privacy-manifests` (deferred to Plan 05) — important: Plan 02 build не должен зависеть от plugin Plan 05
    - File `composeApp/build.gradle.kts` contains `buildkonfig { packageName = "io.github.chudoxl.linteh.journal" }` block
    - File `composeApp/build.gradle.kts` BuildKonfig block declares three fields: `VERSION_NAME` (STRING), `VERSION_CODE` (STRING), `IS_DEBUG` (BOOLEAN)
    - File `composeApp/build.gradle.kts` `versionCode` uses triple-fallback `GITHUB_RUN_NUMBER → git rev-list count → "1"` (RESEARCH Open Question #5 resolution)
    - File `composeApp/src/commonMain/composeResources/values/strings.xml` is well-formed XML with `<resources>` root
    - File `composeApp/src/commonMain/composeResources/values/strings.xml` contains `<string name="app_name">ЛИнТех Дневник</string>` (D-22 RU display name)
    - File `composeApp/src/commonMain/composeResources/values/strings.xml` contains `<string name="privacy_policy_url">https://chudoxl.github.io/LintehJournal/privacy/</string>` (D-21 — exact URL)
    - File `composeApp/src/commonMain/composeResources/values/strings.xml` contains `<string name="open_button">Открыть</string>` (button label)
    - `./gradlew :composeApp:generateBuildKonfig` exits 0 — generated file at `composeApp/build/generated/buildkonfig/commonMain/io/github/chudoxl/linteh/journal/BuildKonfig.kt` contains `const val VERSION_NAME = "0.1.0"`
    - `./gradlew :composeApp:generateComposeResClass` exits 0 — `Res` object generated with all three string keys
    - File `composeApp/build.gradle.kts` contains `commonTest.dependencies { ... implementation(compose.uiTest) }` block (BLOCKER 1 mitigation iter 1: `compose.uiTest` добавляется здесь вручную, потому что `LintechTestConventionPlugin` не может содержать его — non-UI модули не применяют `org.jetbrains.compose` plugin). Этот блок — load-bearing; Plan 05 Task 2 не должен его удалить (BLOCKER 2 iter 2 regression-guard).
  </acceptance_criteria>
  <done>
    BuildKonfig generates `object BuildKonfig` with VERSION_NAME="0.1.0", VERSION_CODE (env or git or "1"), IS_DEBUG=true в commonMain. Compose Resources `Res.string.app_name`, `Res.string.privacy_policy_url`, `Res.string.open_button` доступны для App() composable. Готово к написанию App() в Task 3.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: Implement App() composable + MainActivity + MainViewController + AppTest</name>
  <files>
    composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/App.kt,
    composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainActivity.kt,
    composeApp/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/MainViewController.kt,
    composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/AppTest.kt
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section "Hello LinTech Composable" — App.kt verbatim; section "Cross-platform Testing" — AppTest.kt verbatim; section "Code Examples → Example 4")
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (D-28 Hello LinTech minimum+ scope, D-31 openUrl)
    - core/platform/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/platform/UrlOpener.kt (создан в Task 1 — экспортирует openUrl)
    - composeApp/src/commonMain/composeResources/values/strings.xml (создан в Task 2 — Res.string keys)
    - composeApp/build.gradle.kts (создан в Task 2 — BuildKonfig generates `io.github.chudoxl.linteh.journal.BuildKonfig`)
  </read_first>
  <behavior>
    Test cases для AppTest (cross-platform — runs both Android JVM-test и iosX64Test):
    - Test 1: helloLintech_displaysAllElements — после `setContent { App() }`:
      - Node with testTag "app_title" is displayed
      - Node with testTag "app_version" is displayed
      - Node with testTag "privacy_url" is displayed
      - Node with testTag "open_privacy_button" is displayed

    Не покрываем в этом тесте: фактический openUrl Intent dispatch (требует Robolectric/instrumentation на Android, real device на iOS — out of Phase 1 scope per VALIDATION Manual-Only). Кнопка clickable — это уже verifiable через `assertIsDisplayed`.
  </behavior>
  <action>
    1. **Создать `composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/App.kt`** (per RESEARCH.md "Hello LinTech Composable → commonMain/App.kt", verbatim — testTag-модификаторы для UI-tests):
    ```kotlin
    package io.github.chudoxl.linteh.journal

    import androidx.compose.foundation.layout.Arrangement
    import androidx.compose.foundation.layout.Column
    import androidx.compose.foundation.layout.Spacer
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.foundation.layout.height
    import androidx.compose.foundation.layout.padding
    import androidx.compose.material3.Button
    import androidx.compose.material3.MaterialTheme
    import androidx.compose.material3.Surface
    import androidx.compose.material3.Text
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.platform.testTag
    import androidx.compose.ui.unit.dp
    import io.github.chudoxl.linteh.journal.core.platform.openUrl
    import linteh_journal.composeapp.generated.resources.Res
    import linteh_journal.composeapp.generated.resources.app_name
    import linteh_journal.composeapp.generated.resources.open_button
    import linteh_journal.composeapp.generated.resources.privacy_policy_url
    import org.jetbrains.compose.resources.stringResource

    @Composable
    fun App() {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "v${BuildKonfig.VERSION_NAME} (${BuildKonfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("app_version"),
                    )
                    Spacer(Modifier.height(24.dp))
                    val privacyUrl = stringResource(Res.string.privacy_policy_url)
                    Text(
                        text = privacyUrl,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("privacy_url"),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { openUrl(privacyUrl) },
                        modifier = Modifier.testTag("open_privacy_button"),
                    ) {
                        Text(stringResource(Res.string.open_button))
                    }
                }
            }
        }
    }
    ```
    Notes:
    - **Resources package import — verify before commit (INFO fix):** Запустить `./gradlew :composeApp:generateComposeResClass` ПЕРЕД написанием App.kt и AppTest.kt. После запуска — найти generated `Res.kt` (typically `composeApp/build/generated/compose/resourceGenerator/kotlin/commonMainResClass/`) и записать **actual** package name в SUMMARY. Default expectation: `linteh_journal.composeapp.generated.resources` (имя проекта `LintehJournal` транслитерируется в snake-case). Если actual отличается (например, `io.github.chudoxl.linteh.journal.composeapp.generated.resources`) — обновить imports в App.kt + AppTest.kt сразу по factual package, до первого compile. Записать факт verification в SUMMARY.
    - `compose.material3` import idiomatic: `androidx.compose.material3.*`.
    - `BuildKonfig.VERSION_NAME` — типа String, `BuildKonfig.VERSION_CODE` — типа String (consistency с D-30; cast to String чтобы template работал на обеих платформах).

    2. **Создать `composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainActivity.kt`** (verbatim per RESEARCH.md):
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

    3. **Создать `composeApp/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/MainViewController.kt`** (verbatim per RESEARCH.md):
    ```kotlin
    package io.github.chudoxl.linteh.journal

    import androidx.compose.ui.window.ComposeUIViewController

    @Suppress("FunctionName")
    fun MainViewController() = ComposeUIViewController { App() }
    ```

    4. **Создать `composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/AppTest.kt`** (per RESEARCH.md "Cross-platform Testing → AppTest.kt"):
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

    5. Verify локальный full Android assembly: `./gradlew :composeApp:assembleDebug` — должен собрать debug APK.

    6. **Skip Android JVM tests for AppTest (BLOCKER 2 mitigation):** `./gradlew :composeApp:test` НЕ запускает AppTest напрямую — `runComposeUiTest` на Android JVM-side требует Robolectric, которого нет в Phase 1 (намеренно — overkill для smoke verification). AppTest валидируется ТОЛЬКО на iOS-side через `iosX64Test`. Android-side `commonTest` для других (non-UI) модулей в будущем будет работать без `compose.uiTest` deps (LintechTestConventionPlugin корректно не тянет UI-test deps — см. Plan 01 BLOCKER 1 fix). Если запустить `./gradlew :composeApp:test` локально для других tests — AppTest либо skip, либо fail с clear message о runtime — это acceptable behavior для Phase 1.

    7. Verify iOS-side compile: `./gradlew :composeApp:compileKotlinIosX64`. Должен пройти без unresolved openUrl reference.

    8. **Verify iOS test (best-effort, BLOCKER 3 mitigation):** `./gradlew :composeApp:compileKotlinIosX64 && (./gradlew :composeApp:iosX64Test || echo "iosX64Test deferred to CI macos-15")`. На Linux Mint dev-host `iosX64Test` обычно не запускается (требует macOS Simulator) — это acceptable. Plan 02 closure НЕ требует green `iosX64Test` локально или AFTER CI run; closure = `compileKotlinIosX64` успешно + AppTest скомпилирован. Финальная зелёная CI verification (включая `iosX64Test` на macos-15) — в Plan 03 / Plan 06 close-out (BLOCKER 3 — circular closure between Plan 02 и Plan 03 CI).
  </action>
  <verify>
    <automated>./gradlew :composeApp:assembleDebug 2>&1 | grep -qE "BUILD SUCCESSFUL" && ./gradlew :composeApp:compileKotlinIosX64 2>&1 | grep -qE "BUILD SUCCESSFUL" && grep -q '@Composable' composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/App.kt && grep -q 'fun App()' composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/App.kt && grep -qE 'testTag\("app_title"\)' composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/App.kt && grep -qE 'testTag\("open_privacy_button"\)' composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/App.kt && grep -q 'BuildKonfig.VERSION_NAME' composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/App.kt && grep -q 'openUrl(privacyUrl)' composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/App.kt && grep -q 'class MainActivity' composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainActivity.kt && grep -q 'fun MainViewController()' composeApp/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/MainViewController.kt && grep -q 'runComposeUiTest' composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/AppTest.kt && grep -q 'helloLintech_displaysAllElements' composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/AppTest.kt</automated>
  </verify>
  <acceptance_criteria>
    - File `composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/App.kt` defines `@Composable fun App()` (single-arg-less function)
    - File `App.kt` imports `io.github.chudoxl.linteh.journal.core.platform.openUrl` (key link to :core:platform)
    - File `App.kt` references `BuildKonfig.VERSION_NAME` and `BuildKonfig.VERSION_CODE` from `io.github.chudoxl.linteh.journal.BuildKonfig` (generated by Task 2)
    - File `App.kt` references `Res.string.app_name`, `Res.string.privacy_policy_url`, `Res.string.open_button` через stringResource(...)
    - File `App.kt` Button onClick lambda calls `openUrl(privacyUrl)` (D-31 wiring)
    - File `App.kt` все four UI-elements have testTag modifiers: `"app_title"`, `"app_version"`, `"privacy_url"`, `"open_privacy_button"` (D-19 testTag pattern)
    - File `composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/MainActivity.kt` defines `class MainActivity : ComponentActivity()` with `setContent { App() }` in onCreate
    - File `composeApp/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/MainViewController.kt` defines `fun MainViewController()` returning `ComposeUIViewController { App() }`
    - File `composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal/AppTest.kt` defines test method `helloLintech_displaysAllElements` using `runComposeUiTest` (with `@OptIn(ExperimentalTestApi::class)`)
    - File `AppTest.kt` asserts `assertIsDisplayed` on все four testTags: `app_title`, `app_version`, `privacy_url`, `open_privacy_button`
    - `./gradlew :composeApp:assembleDebug` exits 0 (full Android debug APK builds — including Hello LinTech UI)
    - `./gradlew :composeApp:compileKotlinIosX64` exits 0 (iOS-side compile — A4 verification)
    - `./gradlew :composeApp:iosX64Test` запускается best-effort: либо exits 0 (если macOS Simulator доступен), либо `|| echo "iosX64Test deferred to CI macos-15"` fallback (BLOCKER 3 mitigation — Plan 02 closure НЕ требует green iosX64Test локально или CI run; AppTest скомпилирован — этого достаточно для closure)
    - **Plan 02 closure DOES NOT require:** ни `./gradlew :composeApp:test` AppTest pass на Android JVM (BLOCKER 2 — требует Robolectric), ни green CI run для VALIDATION row 01-02-02 (BLOCKER 3 — circular). Финализация row 01-02-02 в Plan 03/06 после first green CI на macos-15.
  </acceptance_criteria>
  <done>
    Hello LinTech screen полностью реализован — Android запускается на устройстве разработчика (`./gradlew :composeApp:installDebug` → видимый «ЛИнТех Дневник» / версия / Privacy URL / кнопка); iOS-side compile-clean + commonTest проходит. Готово к Plan 03 CI workflows которые валидируют iosX64Test на macos-15 runner.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| App → System browser (URL handler) | openUrl передаёт URL Intent.ACTION_VIEW (Android) или UIApplication (iOS) — system trust boundary |
| App → Compose Resources | strings.xml содержит hardcoded Privacy URL — compile-time constant, no runtime input |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-02-extra | T (Tampering) — supply chain | BuildKonfig plugin (codingfeline) | mitigate | Pin version 0.15.2 in libs.versions.toml. Plugin source open, малый scope (генерация Kotlin object из properties). |
| T-01-platform-01 | I (Information disclosure) — URL injection | openUrl(url: String) | accept | Phase 1: единственный caller — Hello LinTech с compile-time const URL из strings.xml. Нет user-input → нет injection vector. Phase 4+: при добавлении user-input URLs (например, click on links в сообщениях учителей) — добавить URL validation + scheme allowlist. Документировано как Phase 4 TODO в openUrl docs. |
</threat_model>

<verification>
- `./gradlew :composeApp:assembleDebug` succeeds (full Android-side build — Hello LinTech APK)
- `./gradlew :composeApp:test` НЕ требуется для AppTest passing — BLOCKER 2: AppTest на Android JVM-side требует Robolectric (out of Phase 1 scope). AppTest валидируется только на iOS-side в CI macos-15 (Plan 03). Android-side `./gradlew :composeApp:test` для других (non-UI) commonTest tests — будет работать (когда такие tests появятся в Phase 2+).
- `./gradlew :composeApp:compileKotlinIosX64` succeeds (iOS-side compile clean)
- `./gradlew :core:platform:test` succeeds (no unit tests yet но build clean)
- Android device run: `./gradlew :composeApp:installDebug` → запускается на эмуляторе/устройстве разработчика, виден «ЛИнТех Дневник» / версия / URL / кнопка [manual — VALIDATION.md Manual-Only Verifications]
- All testTag модификаторы в App.kt — exact strings что и ожидает AppTest
- expect/actual openUrl сигнатура matches (verified by `compileKotlinAndroid` and `compileKotlinIosX64`)
</verification>

<success_criteria>
1. **COMP-01 partial achievement** — Privacy Policy URL виден из приложения (Hello LinTech screen). Финальная закрытость COMP-01 — после Plan 04 GitHub Pages deploy.
2. **expect/actual canonical pattern установлен** — `openUrl(url: String)` готов как template для Phase 3 (KVault), Phase 6 (BGTaskScheduler).
3. **BuildKonfig pipeline working** — Phase 2+ (Sentry breadcrumbs, AVERS UA mimic) могут использовать `BuildKonfig.VERSION_NAME`.
4. **Compose Resources canonical layer** — Phase 4 будет копировать паттерн `Res.string.*` для всех будущих UI-строк.
5. **runComposeUiTest pattern alive** — testTag-based selectors для всех будущих UI-tests.
6. **iOS-side не требует Mac локально** — `./gradlew :composeApp:compileKotlinIosX64` собирается на Linux Mint dev-host. Полный test runs в CI Plan 03.
</success_criteria>

<output>
After completion, create `.planning/phases/01-foundation-compliance-infrastructure/01-02-SUMMARY.md` with:
- What was built (Hello LinTech composable + expect/actual openUrl + BuildKonfig + Compose Resources + AppTest)
- Files created (полный список)
- Key decisions taken from `Claude's Discretion` (точное расположение strings.xml, BuildKonfig packageName/objectName, top-level vs CompositionLocal для androidApplicationContext в Phase 1)
- Verification status (`./gradlew :composeApp:assembleDebug compileKotlinIosX64` exit codes; `iosX64Test` best-effort)
- Generated Compose Resources package name (verify factual after `generateComposeResClass` — INFO fix)
- Locally untestable items: AppTest на Android JVM-side требует Robolectric (BLOCKER 2 — out of scope Phase 1), валидируется в CI macos-15 через `iosX64Test`. `iosX64Test` запуск локально может skip-нуться на Linux Mint — defer to CI macos-15 (Plan 03)
- VALIDATION row 01-02-02 финализация: deferred to Plan 06 close-out после first green CI (BLOCKER 3 — circular closure decoupled)
- Manual smoke run: `./gradlew :composeApp:installDebug` на Android device — captured screenshot опционально
- Anything Plan 03 should know (test command для iOS — `./gradlew :composeApp:iosX64Test` + plutil-lint step для PrivacyInfo появится в Plan 05)
- Anything Plan 05 should know: composeApp/build.gradle.kts содержит **load-bearing** `commonTest.dependencies { @OptIn(...) implementation(compose.uiTest) }` блок — Plan 05 Task 2 должен использовать **targeted Edit**, НЕ full rewrite. Иначе fix iter 1 уничтожается (BLOCKER 2 iter 2 regression-guard).
</output>
</content>
</invoke>