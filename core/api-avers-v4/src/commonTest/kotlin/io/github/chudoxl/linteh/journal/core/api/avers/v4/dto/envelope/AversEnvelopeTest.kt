package io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.envelope

import io.github.chudoxl.linteh.journal.core.api.avers.v4.result.ApiResult
import io.github.chudoxl.linteh.journal.core.api.avers.v4.result.AversApiError
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlin.test.Test

class AversEnvelopeTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun success_true_data_array_unwraps() {
        val raw = json.parseToJsonElement("""{"success": true, "data": [1, 2, 3]}""")
        val out = raw.unwrapAversEnvelope("test")
        out.shouldBeInstanceOf<ApiResult.Success<UnwrappedEnvelope>>()
    }

    @Test
    fun success_false_returns_failure_server_200() {
        val raw = json.parseToJsonElement("""{"success": false, "msg": "Auth required"}""")
        val out = raw.unwrapAversEnvelope("test")
        out.shouldBeInstanceOf<ApiResult.Failure>()
        (out.error as AversApiError.Server).httpCode shouldBe 200
    }

    @Test
    fun missing_success_field_returns_mismatch() {
        val raw = json.parseToJsonElement("""{"data": [1, 2]}""")
        val out = raw.unwrapAversEnvelope("test")
        out.shouldBeInstanceOf<ApiResult.Mismatch>()
        out.missingFields shouldBe listOf("success")
        out.endpoint shouldBe "test"
    }

    @Test
    fun missing_data_field_returns_mismatch() {
        val raw = json.parseToJsonElement("""{"success": true}""")
        val out = raw.unwrapAversEnvelope("test")
        out.shouldBeInstanceOf<ApiResult.Mismatch>()
        out.missingFields shouldBe listOf("data")
    }

    @Test
    fun direct_array_without_envelope_returns_success() {
        val raw = json.parseToJsonElement("""[1, 2, 3]""")
        val out = raw.unwrapAversEnvelope("test")
        out.shouldBeInstanceOf<ApiResult.Success<UnwrappedEnvelope>>()
    }
}
