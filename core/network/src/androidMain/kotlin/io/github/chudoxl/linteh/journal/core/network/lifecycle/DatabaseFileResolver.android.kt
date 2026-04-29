package io.github.chudoxl.linteh.journal.core.network.lifecycle

import co.touchlab.kermit.Logger
import io.github.chudoxl.linteh.journal.core.platform.getApplicationContext
import java.io.File

private const val TAG = "DatabaseFileResolver"

/**
 * Android actual: resolves the canonical Room file path via
 * [android.content.Context.getDatabasePath] (`/data/data/<pkg>/databases/journal_${accountId}.db`)
 * and deletes the main file plus SQLite WAL sidecars (`-shm`, `-wal`).
 */
actual interface DatabaseFileResolver {
    actual suspend fun delete(accountId: String): Boolean
}

internal class AndroidDatabaseFileResolver : DatabaseFileResolver {
    override suspend fun delete(accountId: String): Boolean {
        val context = getApplicationContext()
        val dbFile = context.getDatabasePath("journal_${accountId}.db")
        if (!dbFile.exists()) {
            Logger.i(TAG) { "DB file not found, nothing to delete: ${dbFile.absolutePath}" }
            return false
        }

        // Also delete -shm and -wal sidecars (SQLite WAL mode); ignore failures
        val sidecars = listOf(
            File(dbFile.absolutePath + "-shm"),
            File(dbFile.absolutePath + "-wal"),
        )
        val mainDeleted = dbFile.delete()
        sidecars.forEach { sidecar ->
            if (sidecar.exists()) sidecar.delete()
        }
        Logger.i(TAG) { "Deleted DB file ${dbFile.absolutePath} (existed=$mainDeleted)" }
        return mainDeleted
    }
}

actual fun defaultDatabaseFileResolver(): DatabaseFileResolver = AndroidDatabaseFileResolver()
