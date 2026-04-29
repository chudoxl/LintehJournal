package io.github.chudoxl.linteh.journal.core.network.lifecycle

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

private const val TAG = "DatabaseFileResolver"

/**
 * iOS actual: resolves the database file under `NSDocumentDirectory` (mirroring
 * `:core:database/DatabaseFactory.ios.kt`) and deletes the main file plus SQLite WAL sidecars.
 *
 * Per [DatabaseFactory.ios] the parent directory carries [NSFileProtectionComplete]; we do not
 * touch the protection class here — only delete the file children.
 */
actual interface DatabaseFileResolver {
    actual suspend fun delete(accountId: String): Boolean
}

@OptIn(ExperimentalForeignApi::class)
internal class IosDatabaseFileResolver : DatabaseFileResolver {
    override suspend fun delete(accountId: String): Boolean {
        val docsDir = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        ) ?: run {
            Logger.w(TAG) { "Could not resolve NSDocumentDirectory for accountId=$accountId" }
            return false
        }

        val basePath = docsDir.path ?: run {
            Logger.w(TAG) { "NSDocumentDirectory has null path for accountId=$accountId" }
            return false
        }
        val dbPath = "$basePath/journal_${accountId}.db"
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(dbPath)) {
            Logger.i(TAG) { "DB file not found, nothing to delete: $dbPath" }
            return false
        }

        // Sidecars (SQLite WAL mode); ignore failures
        listOf("$dbPath-shm", "$dbPath-wal").forEach { sidecar ->
            if (fm.fileExistsAtPath(sidecar)) {
                fm.removeItemAtPath(sidecar, error = null)
            }
        }
        val ok = fm.removeItemAtPath(dbPath, error = null)
        Logger.i(TAG) { "Deleted DB file $dbPath (success=$ok)" }
        return ok
    }
}

actual fun defaultDatabaseFileResolver(): DatabaseFileResolver = IosDatabaseFileResolver()
