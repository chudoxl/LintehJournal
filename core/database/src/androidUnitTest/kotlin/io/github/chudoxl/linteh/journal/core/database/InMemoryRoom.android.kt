package io.github.chudoxl.linteh.journal.core.database

import android.app.Application
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers

/**
 * Android JVM in-memory builder — uses Robolectric's [ApplicationProvider] for the
 * Application context and `AndroidSQLiteDriver` (Robolectric-friendly).
 *
 * `BundledSQLiteDriver` JNI cannot load under Robolectric (UnsatisfiedLinkError) — Plan
 * 02-03 deviation: switch driver to AndroidSQLiteDriver for unit tests. iOS Native tests
 * continue to use BundledSQLiteDriver via the iosTest actual.
 */
actual fun newInMemoryDatabase(): JournalDatabase =
    Room.inMemoryDatabaseBuilder<JournalDatabase>(
        ApplicationProvider.getApplicationContext<Application>(),
    )
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
