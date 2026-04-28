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
