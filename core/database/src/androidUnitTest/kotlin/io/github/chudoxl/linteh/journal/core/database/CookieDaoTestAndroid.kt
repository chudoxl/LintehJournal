package io.github.chudoxl.linteh.journal.core.database

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-runner test class for [CookieDao] contract — delegates to
 * [CookieDaoSpec] (commonTest) so the cross-platform behavioural spec stays in one place
 * (Phase 1 AppTestHelpers WR-05 pattern).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CookieDaoTestAndroid {

    private lateinit var db: JournalDatabase

    @Before
    fun setUp() {
        db = newInMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_and_find_returns_cookie() = CookieDaoSpec.upsertAndFindReturnsCookie(db.cookieDao())

    @Test
    fun upsert_replaces_existing_cookie_with_same_pk() =
        CookieDaoSpec.upsertReplacesExistingCookieWithSamePk(db.cookieDao())

    @Test
    fun findFor_matches_subdomain_pitfall_7() =
        CookieDaoSpec.findForMatchesSubdomainPitfall7(db.cookieDao())

    @Test
    fun findFor_matches_path_prefix() =
        CookieDaoSpec.findForMatchesPathPrefix(db.cookieDao())

    @Test
    fun deleteByAccount_isolates_per_account_pitfall_6() =
        CookieDaoSpec.deleteByAccountIsolatesPerAccountPitfall6(db.cookieDao())

    @Test
    fun cross_account_findFor_returns_no_other_account_cookies() =
        CookieDaoSpec.crossAccountFindForReturnsNoOtherAccountCookies(db.cookieDao())
}
