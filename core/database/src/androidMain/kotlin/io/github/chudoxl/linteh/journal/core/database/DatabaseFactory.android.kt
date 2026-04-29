package io.github.chudoxl.linteh.journal.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import co.touchlab.kermit.Logger
import io.github.chudoxl.linteh.journal.core.platform.getApplicationContext
import kotlinx.coroutines.Dispatchers

private const val TAG = "DatabaseFactory"

/**
 * Android actual: uses `Context.getDatabasePath(...)` to resolve under the app's private
 * files dir (already non-world-readable; ADB backup disabled by D-21 manifest fragment).
 */
actual object DatabaseFactory {
    actual fun create(accountId: String): JournalDatabase {
        val context = getApplicationContext()
        val dbFile = context.getDatabasePath("journal_${accountId}.db")
        Logger.i(TAG) { "Creating Android DB at ${dbFile.absolutePath}" }
        return Room.databaseBuilder<JournalDatabase>(
            context = context,
            name = dbFile.absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}
