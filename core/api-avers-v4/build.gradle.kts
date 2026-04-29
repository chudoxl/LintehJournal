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

android {
    namespace = "io.github.chudoxl.linteh.journal.core.api.avers.v4"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}

// Provide the project-root sanitized HAR fixture path to JVM tests via system property —
// the HarReplayMockEngine reads it on Android JVM (Robolectric). iOS Native loading is
// deferred to Plan 02-09 (resource bundling); EndpointsContractTest is @Ignored on iosTest.
tasks.withType<Test>().configureEach {
    systemProperty(
        "fixtures.dir",
        rootProject.projectDir.resolve("fixtures/sanitized").absolutePath,
    )
}
