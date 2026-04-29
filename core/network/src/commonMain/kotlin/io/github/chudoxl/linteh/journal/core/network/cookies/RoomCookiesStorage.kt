package io.github.chudoxl.linteh.journal.core.network.cookies

import co.touchlab.kermit.Logger
import io.github.chudoxl.linteh.journal.core.database.dao.CookieDao
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.datetime.Clock

private const val TAG = "RoomCookiesStorage"

/**
 * D-18 custom Ktor [CookiesStorage] backed by Room [CookieDao].
 *
 * Lifecycle: instances are created per-account by `HttpClientFactory.cookiesStorageProvider`.
 * Closing this storage is a no-op — the underlying database is owned by the application
 * (closed via `JournalDatabase.close()`, orchestrated outside Phase 2 scope).
 *
 * Cookie matching:
 *  - Subdomain suffix match via DAO query `:host LIKE '%' || domain` (RFC 6265, Pitfall #7).
 *  - Path prefix match via DAO query `:path LIKE path || '%'`.
 *  - Expiry filter applied in-memory after DAO read: cookies with non-null
 *    `expiresAtEpochMillis` in the past are dropped (Pitfall: cookie expiry race).
 *  - Session cookies (`expiresAtEpochMillis == null`) are kept across HttpClient close+reopen
 *    (D-15 design — better UX than forcing re-login on every cold start).
 *
 * Per-account scope: every DAO call filters by [accountId]. Cross-account leak (Pitfall #6)
 * is structurally impossible — an instance configured for `accountId="alice"` cannot return
 * `accountId="bob"` rows.
 */
class RoomCookiesStorage(
    private val dao: CookieDao,
    private val accountId: String,
    private val clock: Clock = Clock.System,
) : CookiesStorage {

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val nowMillis = clock.now().toEpochMilliseconds()
        val rows = dao.findFor(
            accountId = accountId,
            host = requestUrl.host,
            path = requestUrl.encodedPath.ifEmpty { "/" },
        )
        val live = rows.filter { row ->
            val expiry = row.expiresAtEpochMillis
            expiry == null || expiry > nowMillis
        }
        if (rows.size != live.size) {
            Logger.i(TAG) {
                "Filtered ${rows.size - live.size} expired cookies for accountId=$accountId, host=${requestUrl.host}"
            }
        }
        return live.map { it.toKtorCookie() }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        // Skip cookies with empty name (RFC 6265: SHOULD ignore)
        if (cookie.name.isEmpty()) return
        val entity = cookie.toEntity(accountId = accountId, defaultDomain = requestUrl.host)
        dao.upsert(entity)
    }

    override fun close() {
        // No-op: DAO does not own the database. Database lifecycle managed by
        // application bootstrap / AccountDataPurger.
    }
}
