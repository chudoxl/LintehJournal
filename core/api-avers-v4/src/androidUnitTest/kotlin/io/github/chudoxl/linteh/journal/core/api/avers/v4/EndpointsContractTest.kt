package io.github.chudoxl.linteh.journal.core.api.avers.v4

import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.Period
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.StudentScope
import io.github.chudoxl.linteh.journal.core.api.avers.v4.fixtures.harMockEngine
import io.github.chudoxl.linteh.journal.core.api.avers.v4.result.ApiResult
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test

/**
 * Replays each of the 12 sanitized HAR fixtures (6 endpoints × 2 accounts) through Ktor
 * MockEngine and asserts that every endpoint call yields [ApiResult.Success].
 *
 * D-25: integration test boundary — never hits live AVERS in CI. All input from
 * `fixtures/sanitized/account-{A,B}/<endpoint>.har` (Plan 02-02), loaded via the
 * `fixtures.dir` system property + `loadHarFile` expect/actual.
 *
 * iOS Native: this test class is intentionally located in `androidUnitTest` only — the
 * iOS Native build does NOT see it and therefore does not invoke `loadHarFile.ios.kt`
 * (which raises by design). Plan 02-09 (final integration smoke) is responsible for
 * relocating this contract — either to `commonTest` once iOS resource bundling lands, or
 * by duplicating an iosTest variant. The plan's plan-level success criteria mark this
 * caveat as acceptable for Phase 2.
 *
 * Per-account scope (Plan 02-02 SUMMARY cross-account proof):
 *  - Account A: user_id 3020, pupil_id 4028, classId 1013
 *  - Account B: user_id 2684, pupil_id 3661, classId 1015
 *
 * The StudentScope values mirror these so AversApi's outgoing form params (`cls`,
 * `student`, `uchYear`) match what the HAR was captured with — the MockEngine itself
 * doesn't gate on form params, only on path+method, so this is documentation, not
 * a strict matcher constraint.
 */
class EndpointsContractTest {

    private fun client(harPath: String): HttpClient = HttpClient(harMockEngine(harPath))

    private val period = Period(LocalDate(2026, 4, 27), LocalDate(2026, 5, 3))

    private val scopeA = StudentScope(classId = 1013, studentId = 4028, academicYear = 2025)
    private val scopeB = StudentScope(classId = 1015, studentId = 3661, academicYear = 2025)

    // ---------- Account A ----------

    @Test
    fun account_A_grades() = runTest {
        val api = AversApi(client("account-A/grades.har"), scopeA)
        val r = api.fetchGrades(period)
        r.shouldBeInstanceOf<ApiResult.Success<*>>()
    }

    @Test
    fun account_A_schedule() = runTest {
        val api = AversApi(client("account-A/schedule.har"), scopeA)
        val r = api.fetchSchedule(period)
        r.shouldBeInstanceOf<ApiResult.Success<*>>()
    }

    @Test
    fun account_A_homework() = runTest {
        val api = AversApi(client("account-A/homework.har"), scopeA)
        val r = api.fetchHomework(period)
        r.shouldBeInstanceOf<ApiResult.Success<*>>()
    }

    @Test
    fun account_A_attendance() = runTest {
        val api = AversApi(client("account-A/attendance.har"), scopeA)
        val r = api.fetchAttendance(period)
        r.shouldBeInstanceOf<ApiResult.Success<*>>()
    }

    @Test
    fun account_A_messages() = runTest {
        val api = AversApi(client("account-A/messages.har"), scopeA)
        val r = api.fetchMessages()
        r.shouldBeInstanceOf<ApiResult.Success<*>>()
    }

    @Test
    fun account_A_login() = runTest {
        val api = AversAuthApi(client("account-A/login.har"))
        val r = api.login(loginValue = "test", password = "test")
        r.shouldBeInstanceOf<ApiResult.Success<*>>()
    }

    // ---------- Account B ----------

    @Test
    fun account_B_grades() = runTest {
        val api = AversApi(client("account-B/grades.har"), scopeB)
        val r = api.fetchGrades(period)
        r.shouldBeInstanceOf<ApiResult.Success<*>>()
    }

    @Test
    fun account_B_schedule() = runTest {
        val api = AversApi(client("account-B/schedule.har"), scopeB)
        val r = api.fetchSchedule(period)
        r.shouldBeInstanceOf<ApiResult.Success<*>>()
    }

    @Test
    fun account_B_homework() = runTest {
        val api = AversApi(client("account-B/homework.har"), scopeB)
        val r = api.fetchHomework(period)
        r.shouldBeInstanceOf<ApiResult.Success<*>>()
    }

    @Test
    fun account_B_attendance() = runTest {
        val api = AversApi(client("account-B/attendance.har"), scopeB)
        val r = api.fetchAttendance(period)
        r.shouldBeInstanceOf<ApiResult.Success<*>>()
    }

    @Test
    fun account_B_messages() = runTest {
        val api = AversApi(client("account-B/messages.har"), scopeB)
        val r = api.fetchMessages()
        r.shouldBeInstanceOf<ApiResult.Success<*>>()
    }

    @Test
    fun account_B_login() = runTest {
        val api = AversAuthApi(client("account-B/login.har"))
        val r = api.login(loginValue = "test", password = "test")
        r.shouldBeInstanceOf<ApiResult.Success<*>>()
    }
}
