package io.github.chudoxl.linteh.journal.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * iOS Native in-memory builder — Room 2.8 KMP supplies a no-context overload.
 * `BundledSQLiteDriver` works natively on iOS (links against bundled SQLite). Tests run via
 * `./gradlew :core:database:iosX64Test` on macOS CI (Plan 02-08 wires CI; locally on Linux
 * we rely on `:core:database:compileKotlinIosX64` for compile-time validation only).
 */
actual fun newInMemoryDatabase(): JournalDatabase =
    Room.inMemoryDatabaseBuilder<JournalDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
