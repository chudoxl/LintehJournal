package io.github.chudoxl.linteh.journal.core.network.cookies

import io.github.chudoxl.linteh.journal.core.database.JournalDatabase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-runner test class for [RoomCookiesStorage] — delegates to
 * [RoomCookiesStorageSpec] (commonTest) so the cross-platform behavioural spec stays in
 * one place (Plan 02-03 Spec/Wrapper pattern).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomCookiesStorageTestAndroid {

    private lateinit var db: JournalDatabase

    @Before
    fun setUp() {
        db = newInMemoryJournalDatabase()
    }

    @After
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
