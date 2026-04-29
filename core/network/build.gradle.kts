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

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kermit)
            implementation(project(":core:platform"))
            implementation(project(":core:database"))
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
            // Room runtime is `implementation` in :core:database, so it is not transitive
            // to consumers — RoomCookiesStorageSpec's commonTest needs the `RoomDatabase`
            // supertype reference for `JournalDatabase` (and Room.inMemoryDatabaseBuilder
            // is consumed in platform actuals). Adding here propagates to all test targets.
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        val androidUnitTest by getting {
            dependencies {
                // Plan 02-05 Task 3 — RoomCookiesStorageTestAndroid uses Robolectric to provide
                // a synthetic Application context for ApplicationProvider.getApplicationContext()
                // in InMemoryJournalDatabase.android.kt. Mirrors Plan 02-03 :core:database test deps.
                implementation(libs.robolectric)
                implementation(libs.androidx.test.ext.junit)
                // Robolectric cannot load BundledSQLiteDriver's JNI .so — use AndroidSQLiteDriver
                // (sqlite-framework) for Android JVM tests. iOS Native tests keep
                // BundledSQLiteDriver via iosTest actual.
                implementation(libs.androidx.sqlite.framework)
            }
        }
    }
}

android {
    namespace = "io.github.chudoxl.linteh.journal.core.network"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}
