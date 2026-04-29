package io.github.chudoxl.linteh.journal.core.database

import io.github.chudoxl.linteh.journal.core.database.dao.CookieDao
import io.github.chudoxl.linteh.journal.core.database.entity.CookieEntity
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

/**
 * Cross-platform behavioural spec for [CookieDao]. Each function is invoked from
 * a platform-specific `@Test` wrapper:
 *  - [androidUnitTest/CookieDaoTestAndroid] (under Robolectric)
 *  - [iosTest/CookieDaoTestIos]
 *
 * Centralising the assertions here mirrors the Phase 1 AppTestHelpers pattern (WR-05) —
 * platform-specific runners differ, but the contract being verified is identical.
 *
 * The platform wrapper supplies the `dao` from a freshly-built in-memory [JournalDatabase]
 * (via [newInMemoryDatabase]) so this file does not need to know about [JournalDatabase]
 * lifecycle (close/reopen).
 */
object CookieDaoSpec {

    fun upsertAndFindReturnsCookie(dao: CookieDao) = runTest {
        val cookie = sampleCookie(name = "JSESSIONID", value = "abc123", accountId = "default")
        dao.upsert(cookie)

        val found = dao.findFor(
            accountId = "default",
            host = "journal.school28-kirov.ru",
            path = "/journal/api",
        )

        found shouldHaveSize 1
        found[0].value shouldBe "abc123"
    }

    fun upsertReplacesExistingCookieWithSamePk(dao: CookieDao) = runTest {
        dao.upsert(sampleCookie(name = "JSESSIONID", value = "old"))
        dao.upsert(sampleCookie(name = "JSESSIONID", value = "new"))

        val found = dao.findFor(
            accountId = "default",
            host = "journal.school28-kirov.ru",
            path = "/journal",
        )

        found shouldHaveSize 1
        found[0].value shouldBe "new"
    }

    fun findForMatchesSubdomainPitfall7(dao: CookieDao) = runTest {
        // RFC 6265 subdomain rule: a cookie set on parent domain `school28-kirov.ru` with
        // path `/` should be sent to `journal.school28-kirov.ru` for any request path
        // (Pitfall #7 — failing this lets sticky session cookies leak).
        // We use path="/" for the cookie so the LIKE-prefix matches every request path.
        dao.upsert(sampleCookie(name = "PHPSESSID", domain = "school28-kirov.ru", path = "/"))

        val found = dao.findFor(
            accountId = "default",
            host = "journal.school28-kirov.ru",
            path = "/journal",
        )

        found shouldHaveSize 1
        found[0].name shouldBe "PHPSESSID"
    }

    fun findForMatchesPathPrefix(dao: CookieDao) = runTest {
        dao.upsert(sampleCookie(name = "X", path = "/journal"))

        val found = dao.findFor(
            accountId = "default",
            host = "journal.school28-kirov.ru",
            path = "/journal/api/marks",
        )

        found shouldHaveSize 1
    }

    fun deleteByAccountIsolatesPerAccountPitfall6(dao: CookieDao) = runTest {
        dao.upsert(sampleCookie(name = "A_cookie", accountId = "alice"))
        dao.upsert(sampleCookie(name = "B_cookie", accountId = "bob"))

        dao.deleteByAccount("alice") shouldBe 1

        dao.countForAccount("alice") shouldBe 0
        dao.countForAccount("bob") shouldBe 1
    }

    fun crossAccountFindForReturnsNoOtherAccountCookies(dao: CookieDao) = runTest {
        dao.upsert(sampleCookie(name = "X", accountId = "alice", value = "alice_value"))
        dao.upsert(sampleCookie(name = "X", accountId = "bob", value = "bob_value"))

        val foundForAlice = dao.findFor(
            accountId = "alice",
            host = "journal.school28-kirov.ru",
            path = "/journal/api",
        )

        foundForAlice shouldHaveSize 1
        foundForAlice[0].value shouldBe "alice_value"
    }

    private fun sampleCookie(
        name: String = "JSESSIONID",
        value: String = "test-value",
        domain: String = "journal.school28-kirov.ru",
        path: String = "/journal",
        accountId: String = "default",
        expiresAtEpochMillis: Long? = null,
    ) = CookieEntity(
        accountId = accountId,
        name = name,
        value = value,
        domain = domain,
        path = path,
        expiresAtEpochMillis = expiresAtEpochMillis,
        httpOnly = true,
        secure = true,
    )
}
