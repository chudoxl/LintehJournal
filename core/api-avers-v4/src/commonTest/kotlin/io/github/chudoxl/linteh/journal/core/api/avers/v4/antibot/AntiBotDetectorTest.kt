package io.github.chudoxl.linteh.journal.core.api.avers.v4.antibot

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AntiBotDetectorTest {
    private val detector = AntiBotDetector()

    @Test
    fun doctype_html_detected() {
        detector.isCaptchaBody("<!DOCTYPE html><html><body>captcha</body></html>") shouldBe true
    }

    @Test
    fun html_tag_uppercase_detected() {
        detector.isCaptchaBody("<HTML lang=\"ru\">...") shouldBe true
    }

    @Test
    fun form_with_captcha_keyword_detected() {
        detector.isCaptchaBody(
            "<form id=\"captcha-form\"><input name=\"captcha\" /></form>",
        ) shouldBe true
    }

    @Test
    fun valid_json_not_detected() {
        detector.isCaptchaBody("""{"success": true, "data": []}""") shouldBe false
    }

    @Test
    fun empty_body_not_detected() {
        detector.isCaptchaBody("") shouldBe false
    }

    @Test
    fun whitespace_then_html_detected() {
        detector.isCaptchaBody("   \n  <!DOCTYPE html>") shouldBe true
    }

    @Test
    fun avers_extjs_array_response_not_detected() {
        // Plan 02-02 SUMMARY: real AVERS responses look like this — must NOT be flagged.
        val body = """[
[3020, 4, null, null, null, "Кузнецов К.К.", 4028, null, 4028]
]"""
        detector.isCaptchaBody(body) shouldBe false
    }

    @Test
    fun form_without_captcha_keyword_not_detected() {
        // Avoid false positive: a generic <form> in non-captcha contexts (e.g. login page).
        detector.isCaptchaBody("<form action=\"/submit\">name</form>") shouldBe false
    }
}
