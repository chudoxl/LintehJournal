package io.github.chudoxl.linteh.journal.core.network.cookies

import io.github.chudoxl.linteh.journal.core.database.entity.CookieEntity
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.util.date.GMTDate

/**
 * Bidirectional mapper between Ktor [Cookie] (HTTP wire model) and Room [CookieEntity]
 * (persistence model).
 *
 * - `expires`: Ktor uses `GMTDate` (UTC + millisecond precision); we store as epoch millis Long
 *   for cross-platform Room portability (Long is universally serializable).
 * - `domain` / `path`: Ktor allows null; we default to `requestUrl.host` / `"/"` per RFC 6265
 *   "if Domain attribute is missing, host-only flag is set" (we approximate by storing the host
 *   as domain).
 * - `accountId`: not part of Cookie; supplied by [RoomCookiesStorage] from its own scope.
 */
fun Cookie.toEntity(accountId: String, defaultDomain: String): CookieEntity = CookieEntity(
    accountId = accountId,
    name = name,
    value = value,
    domain = domain ?: defaultDomain,
    path = path ?: "/",
    expiresAtEpochMillis = expires?.timestamp, // GMTDate.timestamp = epoch millis
    httpOnly = httpOnly,
    secure = secure,
)

fun CookieEntity.toKtorCookie(): Cookie = Cookie(
    name = name,
    value = value,
    encoding = CookieEncoding.URI_ENCODING, // safe default for AVERS session cookies
    maxAge = 0, // we use `expires` instead of relative max-age
    expires = expiresAtEpochMillis?.let { GMTDate(timestamp = it) },
    domain = domain,
    path = path,
    secure = secure,
    httpOnly = httpOnly,
)
