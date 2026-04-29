package io.github.chudoxl.linteh.journal.core.network.lifecycle

/**
 * Resolves and deletes per-account Room database files.
 *
 * Used by [DefaultAccountDataPurger] to physically remove `journal_${accountId}.db`
 * during account wipe (D-22).
 *
 * D-19 expect/actual pattern (mirrors :core:platform/UrlOpener):
 *  - Android: `Context.getDatabasePath("journal_${accountId}.db").delete()` (+ `-shm` / `-wal`
 *    sidecars when SQLite is in WAL mode).
 *  - iOS: `NSFileManager.defaultManager.removeItemAtPath("$NSDocumentDirectory/journal_${accountId}.db")`
 *    (+ sidecars).
 *
 * Security: implementation MUST NOT trust [accountId] for path construction; the validation
 * that `accountId` matches `[A-Za-z0-9_-]+` (T-02-30 path-traversal mitigation) lives in
 * [DefaultAccountDataPurger] before delegating here — keeps validation centralised.
 */
expect interface DatabaseFileResolver {
    /**
     * Delete the per-account Room database file (and its WAL sidecars) for [accountId].
     *
     * @return `true` if the main `.db` file existed and was removed, `false` if it did not
     *   exist. Throws on filesystem errors (developer-error scenarios — surfaced to
     *   [DefaultAccountDataPurger] which logs but does not propagate).
     */
    suspend fun delete(accountId: String): Boolean
}

/** Provides the platform default [DatabaseFileResolver] for production wiring. */
expect fun defaultDatabaseFileResolver(): DatabaseFileResolver
