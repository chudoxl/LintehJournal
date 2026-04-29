package io.github.chudoxl.linteh.journal.core.network.cookies

import android.app.Application
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import io.github.chudoxl.linteh.journal.core.database.JournalDatabase
import kotlinx.coroutines.Dispatchers

/**
 * Android JVM in-memory builder — Robolectric's [ApplicationProvider] for the
 * Application context + `AndroidSQLiteDriver` (Robolectric-friendly).
 *
 * `BundledSQLiteDriver` JNI cannot load under Robolectric (UnsatisfiedLinkError) — we
 * mirror Plan 02-03's deviation: switch driver to `AndroidSQLiteDriver` for unit tests.
 * iOS Native tests continue to use `BundledSQLiteDriver` via the iosTest actual.
 */
actual fun newInMemoryJournalDatabase(): JournalDatabase =
    Room.inMemoryDatabaseBuilder<JournalDatabase>(
        ApplicationProvider.getApplicationContext<Application>(),
    )
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
