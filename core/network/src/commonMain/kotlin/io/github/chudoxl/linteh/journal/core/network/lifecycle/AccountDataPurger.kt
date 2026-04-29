package io.github.chudoxl.linteh.journal.core.network.lifecycle

import co.touchlab.kermit.Logger
import io.github.chudoxl.linteh.journal.core.database.dao.CookieDao
import io.github.chudoxl.linteh.journal.core.network.HttpClientFactory

private const val TAG = "AccountDataPurger"

/**
 * D-22 wipe API for per-account data hygiene. Phase 3 logout invokes this; Phase 5
 * "Delete Account" UX invokes this.
 *
 * Three-step orchestration:
 *  1. [HttpClientFactory.evict] — close the in-flight HttpClient (cancels open requests,
 *     releases connections). MUST run first so subsequent steps don't race against active
 *     cookie writes.
 *  2. [CookieDao.deleteByAccount] — drop all cookie rows for accountId.
 *  3. [DatabaseFileResolver.delete] — physically delete `journal_${accountId}.db` (and
 *     SQLite -shm/-wal sidecars).
 *
 * If any step throws, the error is logged but the next step still runs (T-02-29 mitigation:
 * partial cleanup is preferred over abort — next purge attempt finishes the job).
 *
 * Security: validates accountId is `[A-Za-z0-9_-]+` (T-02-30 path-traversal mitigation)
 * before delegating to [DatabaseFileResolver].
 */
interface AccountDataPurger {
    suspend fun purge(accountId: String)
}

class DefaultAccountDataPurger(
    private val httpClientFactory: HttpClientFactory,
    private val cookieDao: CookieDao,
    private val databaseFileResolver: DatabaseFileResolver,
) : AccountDataPurger {

    private val accountIdRegex = Regex("^[A-Za-z0-9_-]+$")

    override suspend fun purge(accountId: String) {
        require(accountIdRegex.matches(accountId)) {
            "Invalid accountId '$accountId' — must match [A-Za-z0-9_-]+ (T-02-30 mitigation)"
        }
        Logger.i(TAG) { "Purging accountId=$accountId" }

        // Step 1: close HTTP client
        try {
            httpClientFactory.evict(accountId)
        } catch (e: Throwable) {
            Logger.w(TAG, e) { "factory.evict failed for accountId=$accountId — continuing purge" }
        }

        // Step 2: drop cookies
        val rowsDeleted = try {
            cookieDao.deleteByAccount(accountId)
        } catch (e: Throwable) {
            Logger.w(TAG, e) { "deleteByAccount failed for accountId=$accountId — continuing purge" }
            -1
        }

        // Step 3: drop DB file
        val fileDeleted = try {
            databaseFileResolver.delete(accountId)
        } catch (e: Throwable) {
            Logger.w(TAG, e) { "DatabaseFileResolver.delete failed for accountId=$accountId" }
            false
        }

        Logger.i(TAG) {
            "Purged accountId=$accountId (cookies=$rowsDeleted, file=$fileDeleted)"
        }
    }
}
