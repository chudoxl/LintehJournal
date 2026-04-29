package io.github.chudoxl.linteh.journal.core.database.entity

import androidx.room.Entity

/**
 * Cookie persistence entity for Ktor `HttpCookies` plugin's `CookiesStorage` adapter
 * (`:core:network/RoomCookiesStorage` — Plan 04).
 *
 * Composite primary key (accountId, name, domain, path) implements RFC 6265 cookie identity:
 * a cookie is uniquely identified by name + domain + path; per-account scope adds accountId
 * to enforce isolation invariant (D-22, ARCHITECTURE.md Pattern 3).
 *
 * @property accountId Per-account scope key. Phase 2 placeholder = "default" (D-16); Phase 3
 *    rebinds to real ID after login; Phase 5 multi-account scopes naturally flow through.
 * @property expiresAtEpochMillis null → session cookie (kept across HttpClient close+reopen
 *    by design — D-15 persistent storage even for session cookies).
 *
 * Phase 2: schema version 1, single table.
 * Phase 3: schema version 2 will add `accounts` table via Room migration.
 * Phase 4+: schema versions 3+ will add `grades`, `lessons`, `homework`, `attendance`,
 *    `messages` entities.
 */
@Entity(
    tableName = "cookies",
    primaryKeys = ["accountId", "name", "domain", "path"],
)
data class CookieEntity(
    val accountId: String,
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAtEpochMillis: Long?,
    val httpOnly: Boolean,
    val secure: Boolean,
)
