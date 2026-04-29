import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    id("lintech-kmp")
    id("lintech-test")
}

kotlin {
    androidTarget()
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "apiAversV4"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
            implementation(project(":core:network"))
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            // runTest{} for suspending test bodies — convention plugin only ships kotlin.test
            // + kotest-assertions + turbine. Mirror Plan 02-04's :core:network test deps.
            implementation(libs.kotlinx.coroutines.test)
        }
    }

}

// Plan 09: propagate FIXTURES_DIR env-var into Kotlin/Native test executables.
//
// JVM Test tasks read the path via System.getProperty("fixtures.dir") (see
// tasks.withType<Test> below); Native test executables read it via
// platform.posix.getenv("FIXTURES_DIR") (see
// src/iosTest/.../HarReplayMockEngine.ios.kt).
//
// Implementation note — task-level vs binary-level wiring:
//   The "binary-level" approach (targets.withType<KotlinNativeTarget>().configureEach
//   { binaries.withType<TestExecutable>().configureEach { runTask?.environment(...) } })
//   works in newer Kotlin Gradle Plugin versions where `runTask` is a public Provider on
//   TestExecutable. On Kotlin 2.2.20 the property is not exposed in the binaries DSL, so
//   we configure the task directly. KotlinNativeTest extends
//   org.gradle.process.ProcessForkOptions which exposes `environment(name, value)`.
//   This routes to the same per-target test executable run on macos-15 CI.
//
// On the Linux-Mint dev-host KotlinNativeTest tasks for iosX64Test are still
// configured (they just can't run without an Apple toolchain) — iterating via
// configureEach is safe and lazy.
tasks.withType<KotlinNativeTest>().configureEach {
    environment(
        "FIXTURES_DIR",
        rootProject.projectDir.resolve("fixtures/sanitized").absolutePath,
    )
}

android {
    namespace = "io.github.chudoxl.linteh.journal.core.api.avers.v4"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}

// Provide the project-root sanitized HAR fixture path to JVM tests via system property —
// the HarReplayMockEngine reads it on Android JVM (Robolectric). iOS Native uses the
// FIXTURES_DIR env-var set above on Kotlin/Native test executables (Plan 09).
tasks.withType<Test>().configureEach {
    systemProperty(
        "fixtures.dir",
        rootProject.projectDir.resolve("fixtures/sanitized").absolutePath,
    )
}
