package io.github.chudoxl.linteh.journal.core.api.avers.v4.result

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AversApiErrorTest {

    /** Compile-time check: `when` is exhaustive across all 6 sealed members. */
    private fun describe(error: AversApiError): String = when (error) {
        is AversApiError.Unauthorized -> "unauthorized"
        is AversApiError.AntiBotChallenge -> "captcha:${error.rawHtml.take(20)}"
        is AversApiError.Network -> "network"
        is AversApiError.ContractMismatch -> "mismatch:${error.endpoint}"
        is AversApiError.Server -> "server:${error.httpCode}"
        is AversApiError.KillSwitchTriggered -> "kill"
    }

    @Test
    fun anti_bot_challenge_carries_raw_html() {
        val html = "<!DOCTYPE html>captcha"
        val err = AversApiError.AntiBotChallenge(rawHtml = html)
        err.rawHtml shouldBe html
        describe(err).startsWith("captcha:") shouldBe true
    }

    @Test
    fun all_six_variants_map() {
        describe(AversApiError.Unauthorized) shouldBe "unauthorized"
        describe(AversApiError.Network) shouldBe "network"
        describe(AversApiError.KillSwitchTriggered) shouldBe "kill"
        describe(AversApiError.Server(500)) shouldBe "server:500"
        describe(AversApiError.ContractMismatch("dump", listOf("f"), "ep")) shouldBe "mismatch:ep"
        describe(AversApiError.AntiBotChallenge("html")) shouldBe "captcha:html"
    }
}
