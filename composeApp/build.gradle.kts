import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinComposeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.applePrivacyManifests)
    id("lintech-test")
}

// WR-04 fix: версия code единым Provider'ом — устраняет дублирование между
// android.defaultConfig.versionCode и BuildKonfig.VERSION_CODE, делает .toInt()
// crash-safe (toIntOrNull → 1), и переводит exec-вызов на provider-API
// (configuration-cache friendly путь — re-enable когда Phase 4 включит cache).
// Triple fallback (D-30): GITHUB_RUN_NUMBER → git rev-list --count HEAD → "1".
val versionCodeProvider = providers.environmentVariable("GITHUB_RUN_NUMBER")
    .orElse(
        providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim().ifBlank { "1" } }
    )
    .map { it.toIntOrNull() ?: 1 }

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
        commonTest.dependencies {
            // BLOCKER 1 mitigation iter 1: compose.uiTest добавляется здесь, НЕ в LintechTestConventionPlugin —
            // composeApp уже применяет org.jetbrains.compose plugin, поэтому ComposePlugin.Dependencies
            // resolve-ится без circular dependency на non-UI модули (:core:platform, :core:network).
            //
            // LOAD-BEARING: Plan 05 Task 2 (PrivacyManifest plugin) должен использовать targeted Edit,
            // НЕ full rewrite — иначе этот блок исчезнет и Plan 02 fix iter 1 регрессирует.
            //
            // POLICY (WR-07): НЕ добавлять androidx.compose.ui:ui-test-* напрямую (например,
            // ui-test-junit4) в androidUnitTest или иной source set. Все Compose-test deps
            // должны идти через compose.uiTest (CMP-managed), чтобы CMP-version и Android
            // Compose UI version оставались aligned. Drift между CMP 1.10.x и
            // androidx.compose.ui-test 1.7.x → cryptic ClassDef errors at test runtime.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        // AppTest не живёт в commonTest, потому что Android JVM unit-test (без Robolectric) валится
        // с NPE в Compose `RobolectricIdlingStrategy.getHasRobolectricFingerprint`. Тест разделён:
        //   - iosTest/AppTest.kt — runComposeUiTest на Kotlin/Native (валидируется CI macos-15)
        //   - androidUnitTest/AppTestAndroid.kt — @RunWith(RobolectricTestRunner::class)
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.robolectric)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }

    // D-25 + COMP-02: PrivacyInfo.xcprivacy is embedded into iOS framework via the
    // official JetBrains apple-privacy-manifests plugin (RESEARCH "Где упаковывается в .ipa";
    // Pitfall #2 mitigation — единственный officially-supported путь).
    // Plugin копирует composeApp/PrivacyInfo.xcprivacy в
    // Frameworks/ComposeApp.framework/PrivacyInfo.xcprivacy при сборке Apple framework.
    //
    // NOTE (Rule 3 deviation, Plan 05 Task 2): apple-privacy-manifests plugin v1.0.0
    // регистрирует extension на KotlinMultiplatformExtension, а не на top-level Project.
    // План указывал размещение блока на top-level (между kotlin{} и android{}), но это
    // вызывает "Unresolved reference: privacyManifest". Корректное место — внутри
    // kotlin { ... } scope.
    privacyManifest {
        embed(
            privacyManifest = layout.projectDirectory.file("PrivacyInfo.xcprivacy").asFile,
        )
    }
}

android {
    namespace = "io.github.chudoxl.linteh.journal"  // D-08 root namespace
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.chudoxl.linteh.journal"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeProvider.get()  // WR-04: shared provider, см. top-of-file
        versionName = providers.gradleProperty("versionName").get()  // "0.1.0" из gradle.properties
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true  // Robolectric requires Android resources on classpath
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
            value = versionCodeProvider.get().toString(),  // WR-04: shared provider
        )
        buildConfigField(
            type = BOOLEAN,
            name = "IS_DEBUG",
            value = "true",
        )
    }
    // BL-01 fix: per-variant override — release-сборка получает IS_DEBUG=false.
    // BuildKonfig документирует defaultConfigs(flavor) как механизм overrides
    // (https://github.com/yshrsmz/BuildKonfig?tab=readme-ov-file#variant-aware-config).
    // До Phase 4 у нас только debug+release без flavor'ов — release-override достаточен,
    // чтобы Kermit/feature-gating не путали production-сборки с debug-сборками.
    defaultConfigs("release") {
        buildConfigField(
            type = BOOLEAN,
            name = "IS_DEBUG",
            value = "false",
        )
    }
}
