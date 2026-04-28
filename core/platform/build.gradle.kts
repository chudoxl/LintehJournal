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
