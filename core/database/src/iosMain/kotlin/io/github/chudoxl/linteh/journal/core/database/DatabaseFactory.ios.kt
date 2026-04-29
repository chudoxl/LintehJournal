package io.github.chudoxl.linteh.journal.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSUserDomainMask

private const val TAG = "DatabaseFactory"

/**
 * iOS actual: NSDocumentDirectory + parent-directory `NSFileProtectionComplete` attribute
 * (D-20). On iOS 17+, files inherit parent's protection class on creation.
 *
 * Phase 6 reminder: background polling (BGAppRefreshTask) cannot read `NSFileProtectionComplete`
 * files when the device is locked. Phase 6 plan must downgrade to
 * `NSFileProtectionCompleteUntilFirstUserAuthentication` — see CONTEXT Deferred Ideas.
 */
@OptIn(ExperimentalForeignApi::class)
actual object DatabaseFactory {
    actual fun create(accountId: String): JournalDatabase {
        val docsDir = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )!!

        // D-20: ensure parent directory has NSFileProtectionComplete
        // (file inherits parent's protection class on iOS 17+ when created)
        NSFileManager.defaultManager.setAttributes(
            mapOf(NSFileProtectionKey to NSFileProtectionComplete),
            ofItemAtPath = docsDir.path!!,
            error = null,
        )

        val dbPath = "${docsDir.path}/journal_${accountId}.db"
        Logger.i(TAG) { "Creating iOS DB at $dbPath (NSFileProtectionComplete)" }

        // Rule 1 auto-fix: `Dispatchers.IO` is JVM-only — `internal` on Kotlin/Native and
        // unavailable in iosMain. Room KMP guide accepts any background dispatcher; we use
        // `Dispatchers.Default` here. Future Phase 4+ may introduce an
        // `expect val ioDispatcher: CoroutineDispatcher` in :core:platform if other modules
        // need IO-class semantics on iOS.
        return Room.databaseBuilder<JournalDatabase>(name = dbPath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }
}
