package io.github.chudoxl.linteh.journal.core.network.cookies

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.chudoxl.linteh.journal.core.database.JournalDatabase
import kotlinx.coroutines.Dispatchers

/**
 * iOS Native in-memory builder — Room 2.8 KMP supplies a no-context overload.
 * `BundledSQLiteDriver` works natively on iOS (links against bundled SQLite). Tests run via
 * `./gradlew :core:network:iosX64Test` on macOS CI; locally on Linux we rely on
 * `:core:network:compileTestKotlinIosX64` for compile-time validation only.
 */
actual fun newInMemoryJournalDatabase(): JournalDatabase =
    Room.inMemoryDatabaseBuilder<JournalDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
