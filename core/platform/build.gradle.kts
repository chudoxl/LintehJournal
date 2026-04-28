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
        commonMain.dependencies {
            // WR-08: Kermit для logging fail-loud в openUrl (iOS-actual ранее
            // silently дропал malformed URL без диагностики). Логгер работает
            // out-of-box через Default config — Logcat (Android), OSLog (iOS).
            // Explicit Logger.setMinSeverity(...) init отложен в Phase 2 вместе
            // с Ktor logging plugin setup.
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
    }
}

android {
    namespace = "io.github.chudoxl.linteh.journal.core.platform"  // D-08, D-09
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}
