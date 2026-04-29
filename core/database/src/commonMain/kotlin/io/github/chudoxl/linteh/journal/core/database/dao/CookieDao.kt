package io.github.chudoxl.linteh.journal.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.chudoxl.linteh.journal.core.database.entity.CookieEntity

/**
 * Cookie DAO consumed by `:core:network/RoomCookiesStorage` (Plan 04).
 *
 * `findFor` uses RFC 6265-style domain-suffix matching (Pitfall #7 mitigation):
 *   - `:host LIKE '%' || domain` matches both exact host and subdomain (e.g., a cookie
 *      with `domain="school28-kirov.ru"` is sent to `journal.school28-kirov.ru`).
 *   - `:path LIKE path || '%'` matches the cookie's path prefix.
 *
 * `deleteByAccount` is the wipe primitive used by `AccountDataPurger.purge(accountId)`
 * (Plan 05). All DAO queries filter by `accountId` — per-account isolation invariant
 * (D-22). Cross-account leak (Pitfall #6) is structurally impossible at the query level.
 */
@Dao
interface CookieDao {
    @Query(
        "SELECT * FROM cookies " +
            "WHERE accountId = :accountId " +
            "AND :host LIKE '%' || domain " +
            "AND :path LIKE path || '%'"
    )
    suspend fun findFor(accountId: String, host: String, path: String): List<CookieEntity>

    @Upsert
    suspend fun upsert(cookie: CookieEntity)

    @Query("DELETE FROM cookies WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: String): Int

    @Query("DELETE FROM cookies WHERE accountId = :accountId AND domain = :domain")
    suspend fun deleteByDomain(accountId: String, domain: String): Int

    /** Test-only: count rows for sanity assertions. */
    @Query("SELECT COUNT(*) FROM cookies WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: String): Int
}
