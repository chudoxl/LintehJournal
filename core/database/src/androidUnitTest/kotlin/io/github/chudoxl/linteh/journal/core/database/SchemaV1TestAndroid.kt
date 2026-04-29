package io.github.chudoxl.linteh.journal.core.database

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-runner test class for schema v1 invariants — delegates to [SchemaV1Spec]
 * (commonTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SchemaV1TestAndroid {

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
    fun database_provides_cookie_dao() = SchemaV1Spec.databaseProvidesCookieDao(db)

    @Test
    fun cookie_round_trip_preserves_all_8_fields() =
        SchemaV1Spec.cookieRoundTripPreservesAllEightFields(db)
}
