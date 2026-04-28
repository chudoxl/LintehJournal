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
        }
    }
}

android {
    namespace = "io.github.chudoxl.linteh.journal.core.api.avers.v4"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}
