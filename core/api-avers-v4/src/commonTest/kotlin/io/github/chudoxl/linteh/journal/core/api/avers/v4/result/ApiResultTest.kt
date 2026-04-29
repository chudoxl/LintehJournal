package io.github.chudoxl.linteh.journal.core.api.avers.v4.result

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ApiResultTest {

    @Test
    fun success_holds_value() {
        val r = ApiResult.Success(listOf(1, 2, 3))
        r.value shouldBe listOf(1, 2, 3)
    }

    @Test
    fun mismatch_holds_diagnostic() {
        val r = ApiResult.Mismatch(rawDump = "raw", missingFields = listOf("a"), endpoint = "ep")
        r.endpoint shouldBe "ep"
        r.missingFields shouldBe listOf("a")
    }

    @Test
    fun failure_wraps_error() {
        val r = ApiResult.Failure(AversApiError.Network)
        r.error shouldBe AversApiError.Network
    }

    @Test
    fun result_when_is_exhaustive() {
        fun describe(r: ApiResult<List<Int>>): String = when (r) {
            is ApiResult.Success -> "ok"
            is ApiResult.Mismatch -> "mismatch"
            is ApiResult.Failure -> "fail"
        }
        describe(ApiResult.Success(listOf(1))) shouldBe "ok"
        describe(ApiResult.Mismatch("x", emptyList(), "y")) shouldBe "mismatch"
        describe(ApiResult.Failure(AversApiError.Network)) shouldBe "fail"
    }
}
