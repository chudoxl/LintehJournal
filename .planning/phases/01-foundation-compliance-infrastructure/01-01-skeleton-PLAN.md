---
phase: 01-foundation-compliance-infrastructure
plan: 01
type: execute
wave: 0
depends_on: []
files_modified:
  - settings.gradle.kts
  - build.gradle.kts
  - gradle.properties
  - gradle/libs.versions.toml
  - gradle/wrapper/gradle-wrapper.properties
  - gradle/wrapper/gradle-wrapper.jar
  - gradlew
  - gradlew.bat
  - build-logic/settings.gradle.kts
  - build-logic/convention/build.gradle.kts
  - build-logic/convention/src/main/kotlin/LintechKmpConventionPlugin.kt
  - build-logic/convention/src/main/kotlin/LintechComposeConventionPlugin.kt
  - build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt
  - build-logic/convention/src/main/kotlin/ext/KotlinExt.kt
  - build-logic/convention/src/main/kotlin/ext/AndroidExt.kt
  - composeApp/build.gradle.kts
  - composeApp/src/androidMain/AndroidManifest.xml
  - core/platform/build.gradle.kts
  - core/ui/build.gradle.kts
  - core/network/build.gradle.kts
  - .gitignore
  - .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md
autonomous: true
requirements:
  - COMP-01
  - COMP-02
must_haves:
  truths:
    - "./gradlew tasks завершается с exit 0"
    - "./gradlew assembleDebug собирает все 4 KMP-модуля без ошибок"
    - "./gradlew compileKotlinIosX64 успешен (KMP iosX64 target подключён)"
    - "Convention plugins lintech-kmp / lintech-compose / lintech-test регистрируются и применяются в module-level build.gradle.kts через id(...)"
    - "Добавление нового KMP-модуля = ≤5 строк plugins-блока в build.gradle.kts (success criterion #5)"
    - "Иерархическое именование `:core:*` (Now in Android-style, не плоское) — D-02"
  artifacts:
    - path: "settings.gradle.kts"
      provides: "Multi-module Gradle build с 4 модулями + includeBuild(\"build-logic\")"
      contains: "includeBuild(\"build-logic\")"
    - path: "gradle/libs.versions.toml"
      provides: "Single-source-of-truth для всех версий"
      contains: "kotlin = \"2.2.20\""
    - path: "build-logic/convention/src/main/kotlin/LintechKmpConventionPlugin.kt"
      provides: "Convention plugin lintech-kmp"
      exports: ["LintechKmpConventionPlugin"]
    - path: "build-logic/convention/src/main/kotlin/LintechComposeConventionPlugin.kt"
      provides: "Convention plugin lintech-compose"
      exports: ["LintechComposeConventionPlugin"]
    - path: "build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt"
      provides: "Convention plugin lintech-test"
      exports: ["LintechTestConventionPlugin"]
    - path: "composeApp/build.gradle.kts"
      provides: "Single composeApp KMP application module (commonMain + androidMain + iosMain)"
      contains: "id(\"lintech-kmp\")"
    - path: "core/platform/build.gradle.kts"
      provides: "core:platform KMP library skeleton"
      min_lines: 5
    - path: "core/ui/build.gradle.kts"
      provides: "core:ui KMP library skeleton (compose-enabled)"
      min_lines: 5
    - path: "core/network/build.gradle.kts"
      provides: "core:network KMP library skeleton"
      min_lines: 5
    - path: "gradlew"
      provides: "Gradle wrapper Unix script"
  key_links:
    - from: "settings.gradle.kts"
      to: "build-logic/"
      via: "pluginManagement.includeBuild"
      pattern: "includeBuild\\(\\s*\"build-logic\"\\s*\\)"
    - from: "build-logic/convention/build.gradle.kts"
      to: "LintechKmpConventionPlugin"
      via: "gradlePlugin.plugins.register"
      pattern: "id\\s*=\\s*\"lintech-kmp\""
    - from: "core/platform/build.gradle.kts"
      to: "lintech-kmp convention plugin"
      via: "plugins block id reference"
      pattern: "id\\(\"lintech-kmp\"\\)"
---

<objective>
Создать минимальный, но воспроизводимый Gradle multi-module skeleton проекта ЛИнТех Дневник: Gradle wrapper, version catalog со всеми pinned версиями, build-logic includedBuild с тремя convention plugins (lintech-kmp, lintech-compose, lintech-test), 4 пустых KMP-модуля (`:composeApp`, `:core:platform`, `:core:ui`, `:core:network`), `.gitignore`. Greenfield: codebase пуст, всё создаётся с нуля.

Purpose: Wave 0 устанавливает canonical pattern для всех будущих модулей фаз 2-6. Convention plugins → одно место для KMP/Compose/Test boilerplate; libs.versions.toml → single source of truth для версий; includeBuild("build-logic") → плагины доступны по id во всех модулях. Закрывает success criteria #5 (≤5 строк plugins-блока для нового модуля) и подготавливает почву для Hello LinTech (Plan 02).

Output: Воспроизводимый Gradle build, проходящий `./gradlew assembleDebug compileKotlinIosX64` локально на Linux Mint.
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
@.planning/research/STACK.md
@.planning/research/ARCHITECTURE.md
@.planning/research/PITFALLS.md
@CLAUDE.md

<interfaces>
<!-- Convention plugins создаются в этом плане; downstream модули применяют их по id. -->
<!-- Plugin id → implementation class mapping (для регистрации в gradlePlugin {}): -->

```kotlin
// build-logic/convention/build.gradle.kts
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

Module-level apply pattern (используется в Plans 02, 04, 05):
```kotlin
plugins {
    id("lintech-kmp")
    id("lintech-compose")  // только для UI-модулей
    id("lintech-test")
}
```
</interfaces>

<versions>
<!-- Все версии из RESEARCH.md "Concrete Versions Table". Phase 1 lock — НЕ relitigate. -->

| Component | Version |
|-----------|---------|
| Kotlin | 2.2.20 |
| Compose Multiplatform | 1.10.3 |
| KSP | 2.2.20-2.0.4 |
| Android Gradle Plugin | 8.7.3 |
| Gradle wrapper | 8.10 |
| kotlinx.serialization | 1.7.3 |
| kotlinx-datetime | 0.6.2 |
| kotlinx-coroutines | 1.10.2 |
| AndroidX Lifecycle ViewModel KMP | 2.10.0 |
| AndroidX Navigation 3 | 1.0.0-alpha08 |
| BuildKonfig | 0.15.2 |
| Coil 3 | 3.0.4 |
| Kermit | 2.0.4 |
| Mokkery | 2.5.1 |
| Turbine | 1.2.1 |
| Kotest assertions | 5.9.1 |
| apple-privacy-manifests | 1.0.0 |
| AndroidX Activity Compose | 1.9.3 |
| AndroidX Core KTX | 1.13.1 |
</versions>
</context>

## Tasks

<tasks>

<task type="auto">
  <name>Task 1: Initialize Gradle wrapper, version catalog, root build files, .gitignore</name>
  <files>
    gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.jar, gradle/wrapper/gradle-wrapper.properties,
    settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, .gitignore
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (sections "Concrete Versions Table", "settings.gradle.kts (root)", "Directory layout", "BuildKonfig Setup → gradle.properties")
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (D-01..D-12 module skeleton, D-04 version catalog, D-08 package root, D-10 JDK17, D-29..D-30 versioning)
    - .planning/research/STACK.md (Recommended Stack table — версии locked)
    - CLAUDE.md (project instructions, GSD enforcement)
  </read_first>
  <action>
    1. Запустить `gradle wrapper --gradle-version 8.10 --distribution-type bin` локально. Это создаст: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`. Если локально нет Gradle — установить через SDKMAN или скачать distribution-zip 8.10. Verify: `./gradlew --version` выводит `Gradle 8.10`.

    2. Создать `settings.gradle.kts` в корне репозитория (verbatim, per RESEARCH.md `Module Skeleton → settings.gradle.kts`):
    ```kotlin
    pluginManagement {
        repositories {
            gradlePluginPortal()
            mavenCentral()
            google()
        }
        includeBuild("build-logic")
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
    Pitfall #6 mitigation: `includeBuild("build-logic")` — обязателен. Без него `id("lintech-kmp")` в module-level не находится.

    3. Создать `build.gradle.kts` в корне (пустой, только версии-aliases pass-through):
    ```kotlin
    // Root build script — модули используют convention plugins из build-logic/.
    // Дополнительный root-level config не требуется в Phase 1.
    ```

    4. Создать `gradle.properties` (verbatim, per RESEARCH.md `BuildKonfig Setup → gradle.properties`, плюс package-root намёк):
    ```properties
    # App version (D-30: SemVer)
    versionName=0.1.0

    # Package root (D-08)
    # io.github.chudoxl.linteh.journal.*

    # JVM
    org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
    kotlin.code.style=official

    # Caching (D-16)
    org.gradle.caching=true
    org.gradle.parallel=true
    org.gradle.configuration-cache=false

    # Android
    android.useAndroidX=true
    android.nonTransitiveRClass=true

    # KMP
    kotlin.mpp.androidSourceSetLayoutVersion=2
    kotlin.mpp.applyDefaultHierarchyTemplate=true
    ```

    5. Создать `gradle/libs.versions.toml` (per RESEARCH.md "Concrete Versions Table" — все версии pinned). Формат:
    ```toml
    [versions]
    kotlin = "2.2.20"
    ksp = "2.2.20-2.0.4"
    composeMultiplatform = "1.10.3"
    agp = "8.7.3"
    # NOTE: kotlinx-serialization 1.7.3 + datetime 0.6.2 — Phase 1 baseline (не используется в Phase 1).
    # CLAUDE.md рекомендует 1.9.0 / 0.8.0-rc01; Phase 2 planner re-evaluates когда Ktor + JSON intergrated.
    kotlinxSerialization = "1.7.3"
    kotlinxDatetime = "0.6.2"
    kotlinxCoroutines = "1.10.2"
    androidxLifecycleViewModel = "2.10.0"
    androidxNavigation3 = "1.0.0-alpha08"
    androidxActivityCompose = "1.9.3"
    androidxCoreKtx = "1.13.1"
    buildkonfig = "0.15.2"
    coil = "3.0.4"
    kermit = "2.0.4"
    mokkery = "2.5.1"
    turbine = "1.2.1"
    kotest = "5.9.1"
    applePrivacyManifests = "1.0.0"

    [libraries]
    # Plugin classpath dependencies (used in build-logic/convention)
    androidGradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
    kotlinGradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
    composeGradlePlugin = { module = "org.jetbrains.compose:compose-gradle-plugin", version.ref = "composeMultiplatform" }
    kotlinComposeCompilerGradlePlugin = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }

    # AndroidX (Phase 1: composeApp androidMain only)
    androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidxActivityCompose" }
    androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidxCoreKtx" }

    # Reserved for Phase 2+ (registered, not yet consumed):
    androidx-lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidxLifecycleViewModel" }
    kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
    kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinxDatetime" }
    kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
    kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }
    coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }

    # Test
    turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
    kotest-assertions = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }

    [plugins]
    kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
    kotlinAndroid = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
    kotlinComposeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
    composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
    androidApplication = { id = "com.android.application", version.ref = "agp" }
    androidLibrary = { id = "com.android.library", version.ref = "agp" }
    ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
    buildkonfig = { id = "com.codingfeline.buildkonfig", version.ref = "buildkonfig" }
    mokkery = { id = "dev.mokkery", version.ref = "mokkery" }
    applePrivacyManifests = { id = "org.jetbrains.kotlin.apple-privacy-manifests", version.ref = "applePrivacyManifests" }
    ```

    6. Создать `.gitignore` (per RESEARCH.md security domain — exclude secrets + build artifacts):
    ```
    # Gradle
    .gradle/
    build/
    !gradle/wrapper/gradle-wrapper.jar
    out/

    # IDE
    .idea/
    *.iml
    *.ipr
    *.iws

    # Kotlin
    .kotlin/

    # Android
    local.properties
    *.apk
    *.aab
    *.keystore
    *.jks

    # iOS / Xcode
    xcuserdata/
    *.xcworkspace/xcuserdata/
    DerivedData/
    Pods/

    # Secrets
    .env
    .env.*
    *.pem
    secrets/

    # OS
    .DS_Store
    Thumbs.db

    # Logs
    *.log
    ```
    Решение D-18, D-20: Phase 1 секретов нет, но `.gitignore` готов к Phase 6.
  </action>
  <verify>
    <automated>./gradlew --version 2>&1 | grep -q "Gradle 8.10" && grep -q "includeBuild(\"build-logic\")" settings.gradle.kts && grep -q "kotlin = \"2.2.20\"" gradle/libs.versions.toml && grep -q "ksp = \"2.2.20-2.0.4\"" gradle/libs.versions.toml && grep -q "applePrivacyManifests = \"1.0.0\"" gradle/libs.versions.toml && grep -q "versionName=0.1.0" gradle.properties && grep -q "^\\*\\.keystore$" .gitignore</automated>
  </verify>
  <acceptance_criteria>
    - File `gradle/wrapper/gradle-wrapper.properties` contains `distributionUrl=https\\://services.gradle.org/distributions/gradle-8.10-bin.zip`
    - `./gradlew --version` exits 0 and outputs `Gradle 8.10`
    - File `settings.gradle.kts` contains exact line `includeBuild("build-logic")` inside `pluginManagement {` block
    - File `settings.gradle.kts` contains all four `include(":composeApp")`, `include(":core:platform")`, `include(":core:ui")`, `include(":core:network")` lines
    - File `settings.gradle.kts` contains `rootProject.name = "LintehJournal"`
    - File `gradle/libs.versions.toml` contains `kotlin = "2.2.20"` (exact match) in `[versions]` block
    - File `gradle/libs.versions.toml` contains `ksp = "2.2.20-2.0.4"` (KSP must align with Kotlin major.minor — pitfall #1)
    - File `gradle/libs.versions.toml` contains `composeMultiplatform = "1.10.3"`, `agp = "8.7.3"`, `applePrivacyManifests = "1.0.0"`, `buildkonfig = "0.15.2"`
    - File `gradle/libs.versions.toml` registers plugins: `kotlinMultiplatform`, `androidApplication`, `androidLibrary`, `composeMultiplatform`, `kotlinComposeCompiler`, `buildkonfig`, `applePrivacyManifests`, `mokkery`, `ksp`
    - File `gradle.properties` contains `versionName=0.1.0` (D-30)
    - File `gradle.properties` contains `org.gradle.caching=true` (D-16)
    - File `.gitignore` contains lines matching `^\.gradle/$`, `^build/$`, `^local\.properties$`, `^\*\.keystore$`, `^\.env$`
  </acceptance_criteria>
  <done>
    Gradle wrapper bootstrapped, version catalog ready with all pinned versions, settings.gradle.kts correctly references build-logic + 4 modules, .gitignore protects future secrets, gradle.properties exposes versionName=0.1.0 для BuildKonfig consumption.
  </done>
</task>

<task type="auto">
  <name>Task 2: Create build-logic includedBuild with three convention plugins</name>
  <files>
    build-logic/settings.gradle.kts,
    build-logic/convention/build.gradle.kts,
    build-logic/convention/src/main/kotlin/LintechKmpConventionPlugin.kt,
    build-logic/convention/src/main/kotlin/LintechComposeConventionPlugin.kt,
    build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt,
    build-logic/convention/src/main/kotlin/ext/KotlinExt.kt,
    build-logic/convention/src/main/kotlin/ext/AndroidExt.kt
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (section "Convention Plugins Architecture" целиком, включая `LintechKmpConventionPlugin.kt`, `LintechComposeConventionPlugin.kt`, `LintechTestConventionPlugin.kt` skeletons; section "Code Examples → Example 1")
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (D-03 convention plugins list, D-06 KMP targets, D-10 JDK17, D-11 minSdk26 + targetSdk35)
    - gradle/libs.versions.toml (создан в Task 1; будет читаться через `versionCatalogs.from(files("../gradle/libs.versions.toml"))`)
    - settings.gradle.kts (создан в Task 1)
  </read_first>
  <action>
    1. Создать `build-logic/settings.gradle.kts` (verbatim per RESEARCH.md "Convention Plugins Architecture → build-logic/settings.gradle.kts"):
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

    2. Создать `build-logic/convention/build.gradle.kts` (verbatim per RESEARCH.md):
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
        compileOnly(libs.kotlinComposeCompilerGradlePlugin)
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

    3. Создать `build-logic/convention/src/main/kotlin/ext/KotlinExt.kt` — DSL-extensions для Kotlin Multiplatform-конфигурации:
    ```kotlin
    import org.gradle.api.JavaVersion
    import org.gradle.api.Project
    import org.gradle.kotlin.dsl.configure
    import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

    internal fun Project.configureKotlinMultiplatform() {
        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(17)  // D-10

            // D-06: android + iosX64 + iosArm64 + iosSimulatorArm64
            // applyDefaultHierarchyTemplate включён через kotlin.mpp.applyDefaultHierarchyTemplate=true
            // в gradle.properties; targets configure-ются в module-level build.gradle.kts.

            // freeCompilerArgs — strict expect/actual matching (Kotlin 2.0+)
            targets.configureEach {
                compilations.configureEach {
                    compilerOptions.configure {
                        freeCompilerArgs.add("-Xexpect-actual-classes")
                    }
                }
            }

            sourceSets.named("commonTest") {
                dependencies {
                    implementation(kotlin("test"))
                }
            }
        }
    }
    ```

    4. Создать `build-logic/convention/src/main/kotlin/ext/AndroidExt.kt`:
    ```kotlin
    import com.android.build.gradle.LibraryExtension
    import org.gradle.api.JavaVersion
    import org.gradle.api.Project
    import org.gradle.kotlin.dsl.configure

    internal fun Project.configureAndroidLibrary() {
        extensions.configure<LibraryExtension> {
            compileSdk = 35  // D-11
            defaultConfig {
                minSdk = 26  // D-11
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17  // D-10
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
    ```

    5. Создать `build-logic/convention/src/main/kotlin/LintechKmpConventionPlugin.kt`:
    ```kotlin
    import org.gradle.api.Plugin
    import org.gradle.api.Project

    class LintechKmpConventionPlugin : Plugin<Project> {
        override fun apply(target: Project) = with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.library")
            }

            configureKotlinMultiplatform()
            configureAndroidLibrary()
        }
    }
    ```

    6. Создать `build-logic/convention/src/main/kotlin/LintechComposeConventionPlugin.kt`:
    ```kotlin
    import org.gradle.api.Plugin
    import org.gradle.api.Project
    import org.gradle.kotlin.dsl.configure
    import org.jetbrains.compose.ComposePlugin
    import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

    class LintechComposeConventionPlugin : Plugin<Project> {
        override fun apply(target: Project) = with(target) {
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")  // Kotlin 2.0+ Compose compiler

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.named("commonMain") {
                    val compose = ComposePlugin.Dependencies(target)
                    dependencies {
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

    7. Создать `build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt`:
    ```kotlin
    import org.gradle.api.Plugin
    import org.gradle.api.Project
    import org.gradle.kotlin.dsl.configure
    import org.gradle.kotlin.dsl.getByType
    import org.gradle.api.artifacts.VersionCatalogsExtension
    import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

    class LintechTestConventionPlugin : Plugin<Project> {
        override fun apply(target: Project) = with(target) {
            // Mokkery — KMP-friendly mocking (D-03; replaces MockK on iOS targets)
            pluginManager.apply("dev.mokkery")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.named("commonTest") {
                    dependencies {
                        implementation(kotlin("test"))
                        implementation(libs.findLibrary("kotest-assertions").get())
                        implementation(libs.findLibrary("turbine").get())
                    }
                }
            }
        }
    }
    ```
    Notes:
    - **Important — circular dependency mitigation (BLOCKER 1 fix):** Compose UI Test (`compose.uiTest`) НЕ добавляется здесь. Non-UI модули (`:core:platform`, `:core:network`) применяют `lintech-test` БЕЗ `lintech-compose` → если бы `compose.uiTest` был здесь, его resolve через `ComposePlugin.Dependencies(target)` упал бы на configuration-time fail в этих модулях. **Решение:** `compose.uiTest` добавляется вручную в `composeApp/build.gradle.kts` через явный `commonTest.dependencies` block (см. Plan 02 Task 2) — composeApp уже применяет `org.jetbrains.compose` plugin. Этот convention plugin остаётся универсальным для UI и non-UI модулей.

    8. Run `./gradlew :build-logic:convention:tasks` — verify convention plugins регистрируются. Должны появиться tasks `:convention:assemble`, без compile errors.
  </action>
  <verify>
    <automated>./gradlew :build-logic:convention:assemble 2>&1 | grep -qE "(BUILD SUCCESSFUL|UP-TO-DATE)" && grep -q 'id = "lintech-kmp"' build-logic/convention/build.gradle.kts && grep -q 'id = "lintech-compose"' build-logic/convention/build.gradle.kts && grep -q 'id = "lintech-test"' build-logic/convention/build.gradle.kts && grep -q 'class LintechKmpConventionPlugin' build-logic/convention/src/main/kotlin/LintechKmpConventionPlugin.kt && grep -q 'class LintechComposeConventionPlugin' build-logic/convention/src/main/kotlin/LintechComposeConventionPlugin.kt && grep -q 'class LintechTestConventionPlugin' build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt</automated>
  </verify>
  <acceptance_criteria>
    - File `build-logic/settings.gradle.kts` exists and contains `versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }`
    - File `build-logic/convention/build.gradle.kts` contains `plugins { ` followed by `` `kotlin-dsl` `` (kotlin-dsl plugin applied)
    - File `build-logic/convention/build.gradle.kts` contains `JavaLanguageVersion.of(17)` (D-10 toolchain)
    - File `build-logic/convention/build.gradle.kts` registers exactly three plugins: `lintechKmp`, `lintechCompose`, `lintechTest` with implementationClass references
    - File `build-logic/convention/src/main/kotlin/LintechKmpConventionPlugin.kt` exists, defines `class LintechKmpConventionPlugin : Plugin<Project>` and applies `org.jetbrains.kotlin.multiplatform` + `com.android.library`
    - File `build-logic/convention/src/main/kotlin/LintechComposeConventionPlugin.kt` exists, applies `org.jetbrains.compose` and `org.jetbrains.kotlin.plugin.compose` (Kotlin 2.0+ Compose compiler — D-03)
    - File `build-logic/convention/src/main/kotlin/LintechTestConventionPlugin.kt` exists, applies `dev.mokkery` and adds `kotlin.test` + `kotest-assertions` + `turbine` to commonTest (NO `compose.uiTest` — circular dependency mitigation; добавляется в composeApp build.gradle.kts вручную — см. Plan 02 Task 2)
    - File `build-logic/convention/src/main/kotlin/ext/KotlinExt.kt` defines `internal fun Project.configureKotlinMultiplatform()` with `jvmToolchain(17)` and `freeCompilerArgs.add("-Xexpect-actual-classes")`
    - File `build-logic/convention/src/main/kotlin/ext/AndroidExt.kt` defines `internal fun Project.configureAndroidLibrary()` with `compileSdk = 35` and `minSdk = 26`
    - `./gradlew :build-logic:convention:assemble` exits 0 (convention plugins compile clean)
  </acceptance_criteria>
  <done>
    build-logic/ includedBuild bootstrapped, three convention plugins (lintech-kmp/-compose/-test) compile and register; ready to be applied in module-level build.gradle.kts files of composeApp, core:platform, core:ui, core:network.
  </done>
</task>

<task type="auto">
  <name>Task 3: Create 4 KMP module skeletons (composeApp + core:platform + core:ui + core:network) and verify full Gradle assembly</name>
  <files>
    composeApp/build.gradle.kts,
    composeApp/src/androidMain/AndroidManifest.xml,
    core/platform/build.gradle.kts,
    core/ui/build.gradle.kts,
    core/network/build.gradle.kts,
    .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md
  </files>
  <read_first>
    - .planning/phases/01-foundation-compliance-infrastructure/01-RESEARCH.md (sections "Module Skeleton → Per-module build.gradle.kts patterns", "Directory layout", "Code Examples → Example 1: Convention plugin invocation")
    - .planning/phases/01-foundation-compliance-infrastructure/01-CONTEXT.md (D-01..D-12 module skeleton, D-07 dependency rules: composeApp → :core:ui → :core:network → :core:platform)
    - .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md (всю файл — будет обновлён в этом task)
    - build-logic/convention/src/main/kotlin/LintechKmpConventionPlugin.kt (создан в Task 2)
    - settings.gradle.kts (создан в Task 1)
    - gradle/libs.versions.toml (создан в Task 1)
  </read_first>
  <action>
    1. Создать `core/platform/build.gradle.kts` (минимум — applies lintech-kmp + lintech-test, без UI/compose):
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
            // commonMain — пусто в Phase 1; expect-функция openUrl добавится в Plan 02
            // androidMain dependencies — Intent.ACTION_VIEW, добавятся в Plan 02:
            // implementation(libs.androidx.core.ktx)
        }
    }

    android {
        namespace = "io.github.chudoxl.linteh.journal.core.platform"  // D-08, D-09
        compileSdk = 35
        defaultConfig { minSdk = 26 }
    }
    ```

    2. Создать `core/ui/build.gradle.kts` (compose-enabled, но без кода в Phase 1):
    ```kotlin
    plugins {
        id("lintech-kmp")
        id("lintech-compose")
        id("lintech-test")
    }

    kotlin {
        androidTarget()
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "ui"
                isStatic = true
            }
        }
    }

    android {
        namespace = "io.github.chudoxl.linteh.journal.core.ui"
        compileSdk = 35
        defaultConfig { minSdk = 26 }
    }
    ```

    3. Создать `core/network/build.gradle.kts` (минимум — applies lintech-kmp + lintech-test):
    ```kotlin
    plugins {
        id("lintech-kmp")
        id("lintech-test")
    }

    kotlin {
        androidTarget()
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "network"
                isStatic = true
            }
        }
    }

    android {
        namespace = "io.github.chudoxl.linteh.journal.core.network"
        compileSdk = 35
        defaultConfig { minSdk = 26 }
    }
    ```

    4. Создать `composeApp/build.gradle.kts` (KMP application — D-05; без BuildKonfig/apple-privacy-manifests/composable code пока — те добавятся в Plans 02 + 05):
    ```kotlin
    plugins {
        alias(libs.plugins.kotlinMultiplatform)
        alias(libs.plugins.androidApplication)
        alias(libs.plugins.kotlinComposeCompiler)
        alias(libs.plugins.composeMultiplatform)
        id("lintech-test")
    }

    kotlin {
        androidTarget()
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true  // D-05: SwiftPM-friendly static framework
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
                // :core:network — НЕ импортируем в Phase 1 (Phase 2 добавит когда появится Ktor)
            }
            androidMain.dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
    }

    android {
        namespace = "io.github.chudoxl.linteh.journal"  // D-08 root namespace
        compileSdk = 35
        defaultConfig {
            applicationId = "io.github.chudoxl.linteh.journal"
            minSdk = 26
            targetSdk = 35
            versionCode = (System.getenv("GITHUB_RUN_NUMBER")
                ?: providers.exec {
                    commandLine("git", "rev-list", "--count", "HEAD")
                }.standardOutput.asText.get().trim().ifBlank { "1" }
                ).toInt()  // D-30: triple fallback (resolution per RESEARCH.md Open Question #5)
            versionName = providers.gradleProperty("versionName").get()  // "0.1.0" из gradle.properties
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    ```
    Note: НЕ применяем `lintech-kmp` и `lintech-compose` в `:composeApp` потому что это **application module** (com.android.application, не com.android.library) — convention plugins применяют com.android.library. `:composeApp` использует raw plugins из version catalog. Compose deps добавляются вручную (idempotent с `lintech-compose`).

    5. Создать `composeApp/src/androidMain/AndroidManifest.xml` (минимум; activity/application добавятся в Plan 02):
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
        <!-- Activity и Application декларируются в Plan 02 (Hello LinTech). -->
        <application
            android:label="ЛИнТех Дневник"
            android:supportsRtl="true">
        </application>
    </manifest>
    ```

    6. Создать пустые директории для commonMain каждого модуля (чтобы Gradle их видел):
    ```bash
    mkdir -p composeApp/src/commonMain/kotlin/io/github/chudoxl/linteh/journal
    mkdir -p composeApp/src/commonTest/kotlin/io/github/chudoxl/linteh/journal
    mkdir -p composeApp/src/androidMain/kotlin/io/github/chudoxl/linteh/journal
    mkdir -p composeApp/src/iosMain/kotlin/io/github/chudoxl/linteh/journal
    mkdir -p composeApp/src/commonMain/composeResources/values
    mkdir -p core/platform/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/platform
    mkdir -p core/platform/src/androidMain/kotlin/io/github/chudoxl/linteh/journal/core/platform
    mkdir -p core/platform/src/iosMain/kotlin/io/github/chudoxl/linteh/journal/core/platform
    mkdir -p core/ui/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/ui
    mkdir -p core/network/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/network
    ```
    Чтобы пустые директории остались в git, создать в каждой `.gitkeep`.

    7. Запустить `./gradlew tasks` локально → должны появиться tasks `:composeApp:assembleDebug`, `:core:platform:build`, `:core:ui:build`, `:core:network:build`.

    8. Запустить `./gradlew assembleDebug` → debug APK build для всех модулей. Pitfall mitigation: если падает на android.useAndroidX — проверить gradle.properties (создан в Task 1).

    9. Запустить `./gradlew compileKotlinIosX64` → проверка iosX64 target compile pipeline. Если плагин `kotlin.mpp.applyDefaultHierarchyTemplate=true` не применился — добавить вручную `applyDefaultHierarchyTemplate()` в каждый kotlin {} блок.

    10. **Обновить** `.planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md` `## Per-Task Verification Map` секцию: заменить placeholder row на реальные task entries для всех 6 планов фазы (с автоматической командой и threat ref):
    Для этого Wave 0 task: добавить строки:
    | 01-01-01 | 01 | 0 | infra | T-01-02 | Reproducible build | smoke (compile) | `./gradlew --version` | `gradlew` | ⬜ |
    | 01-01-02 | 01 | 0 | infra | — | Convention plugins compile | smoke | `./gradlew :build-logic:convention:assemble` | `build-logic/convention/src/main/kotlin/*Plugin.kt` | ⬜ |
    | 01-01-03 | 01 | 0 | infra | — | Multi-module skeleton builds | smoke | `./gradlew assembleDebug compileKotlinIosX64` | 4 module build.gradle.kts | ⬜ |
    | 01-02-01 | 02 | 1 | COMP-01 | — | openUrl expect/actual | unit | `./gradlew :core:platform:test` | `core/platform/src/*/kotlin/.../UrlOpener*.kt` | ⬜ |
    | 01-02-02 | 02 | 1 | COMP-01 | — | App() composable + BuildKonfig | unit/UI | `./gradlew :composeApp:iosX64Test --tests AppTest` | `composeApp/src/commonMain/kotlin/.../App.kt` | ⬜ |
    | 01-03-01 | 03 | 2 | COMP-01,COMP-02 | T-01-04 | CI Android job | smoke | gh workflow run via push | `.github/workflows/ci.yml` | ⬜ |
    | 01-03-02 | 03 | 2 | COMP-02 | — | CI iOS job + Privacy lint | smoke | gh workflow run via push (macos) | `.github/workflows/ci.yml` | ⬜ |
    | 01-04-01 | 04 | 3 | COMP-01 | T-01-01 | Privacy Policy HTML | smoke | `curl -sf $URL \| grep -q "Политика конфиденциальности"` | `docs/privacy/index.html` | ⬜ |
    | 01-04-02 | 04 | 3 | COMP-01 | — | Pages workflow auto-deploy | smoke | gh workflow run + curl URL | `.github/workflows/pages.yml` | ⬜ |
    | 01-05-01 | 05 | 4 | COMP-02 | T-01-03 | apple-privacy-manifests plugin | smoke | `./gradlew :composeApp:linkDebugFrameworkIosX64` | `composeApp/build.gradle.kts` privacyManifest{} | ⬜ |
    | 01-05-02 | 05 | 4 | COMP-02 | T-01-03 | PrivacyInfo.xcprivacy plist + CI lint | smoke | `plutil -lint composeApp/PrivacyInfo.xcprivacy` (CI macos-15) | `composeApp/PrivacyInfo.xcprivacy` | ⬜ |
    | 01-06-01 | 06 | 5 | COMP-01,COMP-02 | — | ROADMAP edit + CLAUDE.md + README | manual review | `grep "iosX64Test screenshot" .planning/ROADMAP.md` | ROADMAP.md, CLAUDE.md, README.md | ⬜ |
    Также установить `wave_0_complete: true` в frontmatter VALIDATION.md. **NOT** устанавливать `nyquist_compliant: true` — этот флаг финализируется только в Plan 06 после первого green CI run, когда все task-rows в Per-Task Verification Map получат ✅ статусы (см. BLOCKER 5 contract).
  </action>
  <verify>
    <automated>./gradlew assembleDebug 2>&1 | grep -qE "BUILD SUCCESSFUL" && ./gradlew compileKotlinIosX64 2>&1 | grep -qE "BUILD SUCCESSFUL" && grep -q 'namespace = "io.github.chudoxl.linteh.journal"' composeApp/build.gradle.kts && grep -q 'namespace = "io.github.chudoxl.linteh.journal.core.platform"' core/platform/build.gradle.kts && grep -q 'namespace = "io.github.chudoxl.linteh.journal.core.ui"' core/ui/build.gradle.kts && grep -q 'namespace = "io.github.chudoxl.linteh.journal.core.network"' core/network/build.gradle.kts && grep -q 'wave_0_complete: true' .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md && ! grep -q 'nyquist_compliant: true' .planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md</automated>
  </verify>
  <acceptance_criteria>
    - File `composeApp/build.gradle.kts` contains `applicationId = "io.github.chudoxl.linteh.journal"` (D-08)
    - File `composeApp/build.gradle.kts` contains `minSdk = 26` and `targetSdk = 35` (D-11)
    - File `composeApp/build.gradle.kts` contains `iosX64()`, `iosArm64()`, `iosSimulatorArm64()` (D-06)
    - File `composeApp/build.gradle.kts` contains `isStatic = true` for iOS framework (D-05 SwiftPM-friendly)
    - File `composeApp/build.gradle.kts` contains `versionName = providers.gradleProperty("versionName").get()` (D-30)
    - File `core/platform/build.gradle.kts` contains exactly 2 entries in `plugins {}` block: `id("lintech-kmp")` and `id("lintech-test")` — verifying ≤5 lines acceptance (success criterion #5)
    - File `core/ui/build.gradle.kts` plugins block contains `id("lintech-kmp")`, `id("lintech-compose")`, `id("lintech-test")` (3 lines)
    - File `core/network/build.gradle.kts` plugins block has 2 lines (`id("lintech-kmp")`, `id("lintech-test")`)
    - Each module file contains `namespace = "io.github.chudoxl.linteh.journal..."` matching D-08, D-09 sub-package convention
    - File `composeApp/src/androidMain/AndroidManifest.xml` exists with `<manifest>` root and `<application>` element
    - Directories `composeApp/src/{commonMain,commonTest,androidMain,iosMain}/kotlin/io/github/chudoxl/linteh/journal/` exist
    - Directories `core/{platform,ui,network}/src/commonMain/kotlin/io/github/chudoxl/linteh/journal/core/{platform,ui,network}/` exist
    - `./gradlew assembleDebug` exits 0 (Android assembly succeeds)
    - `./gradlew compileKotlinIosX64` exits 0 (iOS X64 compile succeeds without Xcode — A4 verification)
    - File `.planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md` Per-Task Verification Map contains rows for all six PLAN files (01-01 through 01-06)
    - File `.planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md` frontmatter `wave_0_complete: true` (BLOCKER 5: `nyquist_compliant` остаётся `false` до Plan 06 close-out)
  </acceptance_criteria>
  <done>
    All four KMP modules (composeApp + core:platform + core:ui + core:network) build на обоих targets (android + iosX64). Convention plugins применяются ровно по одной строке-id в каждом module-level plugins{} блоке (≤5 строк всего — success criterion #5). VALIDATION.md обновлён с реальной Per-Task Verification Map и `wave_0_complete: true`. **`nyquist_compliant` остаётся `false` до Plan 06 close-out (BLOCKER 5 contract — финализируется только после first green CI run и подтверждённых ✅ всех task-rows).**
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Local dev → repo | Developer commits build files; supply-chain risk if convention plugins pull untrusted deps |
| repo → Gradle Plugin Portal / mavenCentral / google | Plugins/libraries downloaded with version-pin only — no integrity checksum yet (Gradle wrapper-validated) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-02 | T (Tampering) — supply chain | Convention plugins (build-logic/convention) + libs.versions.toml | mitigate | Pin all versions in `libs.versions.toml` (no ranges, no LATEST). PR-review required for any version bump. Phase 1 — only well-known publishers (Google AGP, JetBrains Kotlin/Compose, codingfeline BuildKonfig, lupuuss Mokkery). |
| T-01-04-pre | T (Tampering) — branch | main branch | accept (configured Wave 2) | Branch protection rule documented in README — manual GitHub UI step (D-17). Cannot be enforced from workflow file. Configured after first merge. |
| T-01-skel-01 | I (Information disclosure) | .gitignore | mitigate | `.gitignore` excludes `local.properties`, `*.keystore`, `*.jks`, `.env`, `.env.*`, `*.pem`, `secrets/`. Phase 1 has no secrets, but pattern locked-in for Phase 6. |
</threat_model>

<verification>
- `./gradlew --version` outputs `Gradle 8.10`
- `./gradlew :build-logic:convention:assemble` succeeds (convention plugins compile)
- `./gradlew tasks` lists tasks for `:composeApp`, `:core:platform`, `:core:ui`, `:core:network`
- `./gradlew assembleDebug` succeeds (Android side green)
- `./gradlew compileKotlinIosX64` succeeds (iOS X64 compile pipeline works на Linux Mint без Xcode)
- Все четыре module build.gradle.kts files имеют ≤5 строк plugins{} блок (success criterion #5)
- `.planning/phases/01-foundation-compliance-infrastructure/01-VALIDATION.md` Per-Task Verification Map populated for all six PLAN files
</verification>

<success_criteria>
1. **Воспроизводимая сборка обеих target-семейств** — `./gradlew assembleDebug` (Android) и `./gradlew compileKotlinIosX64` (iOS X64) собирают пустые модули без ошибок (greenfield baseline).
2. **Convention plugins работают** — модуль `:core:platform` применяет ровно `id("lintech-kmp") + id("lintech-test")` в plugins{} блоке (2 строки, ≤5 — success criterion #5).
3. **Version catalog as single source of truth** — `gradle/libs.versions.toml` содержит все pinned версии (Kotlin 2.2.20, KSP 2.2.20-2.0.4, CMP 1.10.3, AGP 8.7.3, apple-privacy-manifests 1.0.0, BuildKonfig 0.15.2). No version literals в module build.gradle.kts.
4. **Validation map alive** — VALIDATION.md обновлён с реальными task entries для всех планов фазы; `wave_0_complete: true` в frontmatter (флаг `nyquist_compliant` финализируется в Plan 06 после first green CI — BLOCKER 5).
5. **Готовность к Plan 02** — `:composeApp/src/{commonMain,androidMain,iosMain,commonTest}/kotlin/...` директории существуют, `:core:platform` готов принять `expect fun openUrl(...)` в Plan 02.
</success_criteria>

<output>
After completion, create `.planning/phases/01-foundation-compliance-infrastructure/01-01-SUMMARY.md` with:
- What was built (multi-module Gradle skeleton + convention plugins + version catalog)
- Files created (полный список — settings, build-logic/, libs.versions.toml, 4 module build.gradle.kts, .gitignore, AndroidManifest.xml, VALIDATION.md update)
- Key decisions taken from `Claude's Discretion` block (точные library versions, internal layout convention plugins, gradle.properties keys)
- Verification status (`./gradlew assembleDebug compileKotlinIosX64` exit codes)
- Anything Plan 02 should know (где expect-функция должна быть положена; где App() composable должен быть; где AppTest должен быть)
</output>
