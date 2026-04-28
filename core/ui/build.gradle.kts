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
