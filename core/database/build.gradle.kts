plugins {
    id("lintech-kmp")
    id("lintech-test")
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidxRoom)
}

kotlin {
    androidTarget()
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "database"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(project(":core:platform"))
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Per-target KSP — required for Room KMP (Pitfall #3)
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosX64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
}

android {
    namespace = "io.github.chudoxl.linteh.journal.core.database"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}
