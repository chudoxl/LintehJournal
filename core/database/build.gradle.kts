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
            implementation(libs.kermit)
            implementation(project(":core:platform"))
        }
        commonTest.dependencies {
            // Plan 02-03: in-memory Room contract tests in commonTest
            // (CookieDaoTest, SchemaV1Test). runTest{} requires kotlinx-coroutines-test.
            implementation(libs.kotlinx.coroutines.test)
        }
        val androidUnitTest by getting {
            dependencies {
                // Robolectric provides synthetic Application context for
                // ApplicationProvider.getApplicationContext() in InMemoryRoom.android.kt.
                // Required by CookieDaoTestAndroid + SchemaV1TestAndroid (@RunWith).
                implementation(libs.robolectric)
                implementation(libs.androidx.test.ext.junit)
                // Robolectric cannot load BundledSQLiteDriver's JNI .so — use
                // AndroidSQLiteDriver (sqlite-framework) for Android JVM tests.
                // iOS Native tests keep BundledSQLiteDriver via iosTest actual.
                implementation(libs.androidx.sqlite.framework)
            }
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
