package io.github.chudoxl.linteh.journal.core.network.cookies

import io.github.chudoxl.linteh.journal.core.database.dao.CookieDao
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock

/**
 * Cross-platform behavioural spec for [RoomCookiesStorage]. Mirrors the Plan 02-03
 * Spec/Wrapper test split (`CookieDaoSpec`): each function takes the [CookieDao] and
 * verifies one behaviour. Platform wrappers in `androidUnitTest` (Robolectric) and
 * `iosTest` (kotlin.test) build a fresh in-memory [JournalDatabase] via
 * [newInMemoryJournalDatabase] and feed `db.cookieDao()` here.
 *
 * The DAO is the single shared resource exercised across the spec; per-account scoping is
 * proven by spawning two [RoomCookiesStorage] instances over the same DAO (see
 * [crossAccountIsolationPitfall6]).
 */
object RoomCookiesStorageSpec {

    fun addThenGetRoundTripPreservesFields(dao: CookieDao) = runTest {
        val storage = RoomCookiesStorage(dao = dao, accountId = "default")
        val cookie = Cookie(
            name = "JSESSIONID",
            value = "abc123",
            domain = "journal.school28-kirov.ru",
            path = "/journal",
            secure = true,
            httpOnly = true,
        )
        storage.addCookie(Url("https://journal.school28-kirov.ru/journal/api"), cookie)

        val got = storage.get(Url("https://journal.school28-kirov.ru/journal/api/marks"))

        got shouldHaveSize 1
        got[0].name shouldBe "JSESSIONID"
        got[0].value shouldBe "abc123"
        got[0].domain shouldBe "journal.school28-kirov.ru"
        got[0].path shouldBe "/journal"
        got[0].secure shouldBe true
        got[0].httpOnly shouldBe true
    }

    fun getFiltersExpiredCookies(dao: CookieDao) = runTest {
        val storage = RoomCookiesStorage(dao = dao, accountId = "default")
        val now = Clock.System.now().toEpochMilliseconds()

        val future = Cookie(
            name = "FUTURE",
            value = "x",
            domain = "school28-kirov.ru",
            path = "/",
            expires = GMTDate(timestamp = now + 60_000L),
        )
        storage.addCookie(Url("https://journal.school28-kirov.ru/"), future)

        val past = Cookie(
            name = "PAST",
            value = "y",
            domain = "school28-kirov.ru",
            path = "/",
            expires = GMTDate(timestamp = now - 60_000L),
        )
        storage.addCookie(Url("https://journal.school28-kirov.ru/"), past)

        val got = storage.get(Url("https://journal.school28-kirov.ru/journal"))
        got.map { it.name } shouldBe listOf("FUTURE")
    }

    fun getReturnsSessionCookiesWithNullExpiry(dao: CookieDao) = runTest {
        val storage = RoomCookiesStorage(dao = dao, accountId = "default")
        val sessionCookie = Cookie(
            name = "SESSION",
            value = "z",
            domain = "school28-kirov.ru",
            path = "/",
            expires = null,
        )
        storage.addCookie(Url("https://journal.school28-kirov.ru/"), sessionCookie)

        val got = storage.get(Url("https://journal.school28-kirov.ru/journal"))
        got shouldHaveSize 1
    }

    fun getMatchesSubdomainPitfall7(dao: CookieDao) = runTest {
        val storage = RoomCookiesStorage(dao = dao, accountId = "default")
        // Cookie set on parent domain — must match subdomain request (RFC 6265 leading-dot
        // suffix match handled at DAO query level via `:host LIKE '%' || domain`).
        val cookie = Cookie(
            name = "PHPSESSID",
            value = "abc",
            domain = "school28-kirov.ru",
            path = "/",
        )
        storage.addCookie(Url("https://school28-kirov.ru/"), cookie)

        val got = storage.get(Url("https://journal.school28-kirov.ru/journal"))
        got shouldHaveSize 1
    }

    fun crossAccountIsolationPitfall6(dao: CookieDao) = runTest {
        val storageAlice = RoomCookiesStorage(dao = dao, accountId = "alice")
        val storageBob = RoomCookiesStorage(dao = dao, accountId = "bob")

        storageAlice.addCookie(
            Url("https://journal.school28-kirov.ru/"),
            Cookie(name = "X", value = "alice_val", domain = "journal.school28-kirov.ru", path = "/"),
        )
        storageBob.addCookie(
            Url("https://journal.school28-kirov.ru/"),
            Cookie(name = "X", value = "bob_val", domain = "journal.school28-kirov.ru", path = "/"),
        )

        val aliceCookies = storageAlice.get(Url("https://journal.school28-kirov.ru/journal"))
        val bobCookies = storageBob.get(Url("https://journal.school28-kirov.ru/journal"))

        aliceCookies shouldHaveSize 1
        aliceCookies[0].value shouldBe "alice_val"

        bobCookies shouldHaveSize 1
        bobCookies[0].value shouldBe "bob_val"
    }

    fun addCookieWithEmptyNameIsIgnored(dao: CookieDao) = runTest {
        val storage = RoomCookiesStorage(dao = dao, accountId = "default")
        val empty = Cookie(name = "", value = "x", domain = "school28-kirov.ru", path = "/")
        storage.addCookie(Url("https://journal.school28-kirov.ru/"), empty)

        val got = storage.get(Url("https://journal.school28-kirov.ru/journal"))
        got.shouldBeEmpty()
    }
}
