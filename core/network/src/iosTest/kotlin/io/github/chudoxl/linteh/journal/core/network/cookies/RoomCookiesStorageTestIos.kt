package io.github.chudoxl.linteh.journal.core.network.cookies

import io.github.chudoxl.linteh.journal.core.database.JournalDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * iOS Native test class for [RoomCookiesStorage] — delegates to [RoomCookiesStorageSpec]
 * (commonTest). Locally we only check `compileTestKotlinIosX64`; runtime execution
 * (`./gradlew :core:network:iosX64Test`) is reserved for the macos-15 CI runner per
 * `:core:database` Plan 02-03 protocol.
 */
class RoomCookiesStorageTestIos {

    private lateinit var db: JournalDatabase

    @BeforeTest
    fun setUp() {
        db = newInMemoryJournalDatabase()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun add_then_get_round_trip_preserves_fields() =
        RoomCookiesStorageSpec.addThenGetRoundTripPreservesFields(db.cookieDao())

    @Test
    fun get_filters_expired_cookies() =
        RoomCookiesStorageSpec.getFiltersExpiredCookies(db.cookieDao())

    @Test
    fun get_returns_session_cookies_with_null_expiry() =
        RoomCookiesStorageSpec.getReturnsSessionCookiesWithNullExpiry(db.cookieDao())

    @Test
    fun get_matches_subdomain_pitfall_7() =
        RoomCookiesStorageSpec.getMatchesSubdomainPitfall7(db.cookieDao())

    @Test
    fun cross_account_isolation_pitfall_6() =
        RoomCookiesStorageSpec.crossAccountIsolationPitfall6(db.cookieDao())

    @Test
    fun add_cookie_with_empty_name_is_ignored() =
        RoomCookiesStorageSpec.addCookieWithEmptyNameIsIgnored(db.cookieDao())
}
