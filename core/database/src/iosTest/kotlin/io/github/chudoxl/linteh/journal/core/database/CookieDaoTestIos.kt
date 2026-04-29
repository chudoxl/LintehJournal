package io.github.chudoxl.linteh.journal.core.database

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * iOS Native test class for [CookieDao] contract — delegates to [CookieDaoSpec] (commonTest).
 * Runs via `./gradlew :core:database:iosX64Test` on macOS CI (Plan 02-08).
 */
class CookieDaoTestIos {

    private lateinit var db: JournalDatabase

    @BeforeTest
    fun setUp() {
        db = newInMemoryDatabase()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_and_find_returns_cookie() =
        CookieDaoSpec.upsertAndFindReturnsCookie(db.cookieDao())

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
