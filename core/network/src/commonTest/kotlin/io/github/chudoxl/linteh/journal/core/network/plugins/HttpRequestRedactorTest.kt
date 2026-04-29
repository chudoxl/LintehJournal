package io.github.chudoxl.linteh.journal.core.network.plugins

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test

class HttpRequestRedactorTest {
    private val redactor = HttpRequestRedactor()

    @Test
    fun form_encoded_password_is_redacted() {
        val body = "password=secret123&foo=bar"
        val out = redactor.redact(body, "application/x-www-form-urlencoded")
        out shouldContain "password=***REDACTED***"
        out shouldNotContain "secret123"
        out shouldContain "foo=bar"
    }

    @Test
    fun form_encoded_canary_kanareyka_password_42_redacted() {
        // ROADMAP success #5 / Pitfall #5 canary
        val canary = "kanareyka_PASSWORD_DO_NOT_LEAK_42"
        val body = "login=ivan&password=$canary&otp=000000"
        val out = redactor.redact(body)
        out shouldNotContain canary
        out shouldContain "***REDACTED***"
    }

    @Test
    fun json_password_is_redacted_keeps_other_fields() {
        val body = """{"login":"ivan","password":"secret123","keep":"this"}"""
        val out = redactor.redact(body, "application/json")
        out shouldContain "\"password\":\"***REDACTED***\""
        out shouldNotContain "secret123"
        out shouldContain "\"keep\":\"this\""
    }

    @Test
    fun benign_content_not_redacted_pitfall_2() {
        // "You forgot your password to the website" — should NOT be masked because
        // the literal `password` is not followed by `=` (form) or `":` (JSON key).
        val body = "Note: User said 'You forgot your password to the website yesterday'"
        val out = redactor.redact(body)
        out shouldBe body
    }

    @Test
    fun all_seven_sensitive_keys_redacted_in_json() {
        listOf("password", "pwd", "pass", "cookie", "authorization", "set-cookie", "token")
            .forEach { key ->
                val body = """{"$key":"sensitive-value-for-$key","other":"keep"}"""
                val out = redactor.redact(body)
                out shouldContain "***REDACTED***"
                out shouldNotContain "sensitive-value-for-$key"
                out shouldContain "\"other\":\"keep\""
            }
    }

    @Test
    fun header_redactor_masks_known_keys_case_insensitive() {
        redactor.redactHeaderValue("Authorization", "Bearer xyz") shouldBe "***REDACTED***"
        redactor.redactHeaderValue("authorization", "Basic abc") shouldBe "***REDACTED***"
        redactor.redactHeaderValue("Cookie", "session=abc") shouldBe "***REDACTED***"
        redactor.redactHeaderValue("Content-Type", "application/json") shouldBe "application/json"
    }

    @Test
    fun redact_is_idempotent() {
        val body = """{"password":"secret","foo":"bar"}"""
        val once = redactor.redact(body)
        val twice = redactor.redact(once)
        twice shouldBe once
    }
}
