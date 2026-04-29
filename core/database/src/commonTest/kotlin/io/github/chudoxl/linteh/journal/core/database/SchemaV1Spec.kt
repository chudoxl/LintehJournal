package io.github.chudoxl.linteh.journal.core.database

import io.github.chudoxl.linteh.journal.core.database.entity.CookieEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest

/**
 * Cross-platform behavioural spec for the schema v1 invariants of [JournalDatabase]:
 * DAO presence + 8-field round trip. Invoked from platform-specific @Test wrappers.
 */
object SchemaV1Spec {

    fun databaseProvidesCookieDao(db: JournalDatabase) {
        db.cookieDao() shouldNotBe null
    }

    fun cookieRoundTripPreservesAllEightFields(db: JournalDatabase) = runTest {
        val original = CookieEntity(
            accountId = "default",
            name = "JSESSIONID",
            value = "abc",
            domain = "school28-kirov.ru",
            path = "/journal",
            expiresAtEpochMillis = 1_700_000_000_000L,
            httpOnly = true,
            secure = false,
        )

        db.cookieDao().upsert(original)
        val retrieved = db.cookieDao()
            .findFor(accountId = "default", host = "journal.school28-kirov.ru", path = "/journal")
            .single()

        retrieved.accountId shouldBe original.accountId
        retrieved.name shouldBe original.name
        retrieved.value shouldBe original.value
        retrieved.domain shouldBe original.domain
        retrieved.path shouldBe original.path
        retrieved.expiresAtEpochMillis shouldBe original.expiresAtEpochMillis
        retrieved.httpOnly shouldBe original.httpOnly
        retrieved.secure shouldBe original.secure
    }
}
