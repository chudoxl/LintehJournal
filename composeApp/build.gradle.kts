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
        versionCode = (
            System.getenv("GITHUB_RUN_NUMBER")
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
