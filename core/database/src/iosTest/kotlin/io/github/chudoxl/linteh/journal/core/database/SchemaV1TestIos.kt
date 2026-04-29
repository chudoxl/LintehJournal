package io.github.chudoxl.linteh.journal.core.database

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * iOS Native test class for schema v1 invariants — delegates to [SchemaV1Spec] (commonTest).
 */
class SchemaV1TestIos {

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
    fun database_provides_cookie_dao() = SchemaV1Spec.databaseProvidesCookieDao(db)

    @Test
    fun cookie_round_trip_preserves_all_8_fields() =
        SchemaV1Spec.cookieRoundTripPreservesAllEightFields(db)
}
