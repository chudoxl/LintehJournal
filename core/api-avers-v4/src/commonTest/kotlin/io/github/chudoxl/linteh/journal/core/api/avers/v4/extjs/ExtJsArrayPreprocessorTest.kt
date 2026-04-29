package io.github.chudoxl.linteh.journal.core.api.avers.v4.extjs

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test

class ExtJsArrayPreprocessorTest {

    @Test
    fun new_date_with_zero_indexed_month_becomes_iso() {
        // Plan 02-02 SUMMARY example: new Date(2026,3,27,0,0,0,0) = April 27 2026.
        val out = ExtJsArrayPreprocessor.toStrictJson("new Date(2026,3,27,0,0,0,0)")
        out shouldBe "\"2026-04-27\""
    }

    @Test
    fun december_emitted_as_12() {
        val out = ExtJsArrayPreprocessor.toStrictJson("new Date(2025,11,31,0,0,0,0)")
        out shouldBe "\"2025-12-31\""
    }

    @Test
    fun january_emitted_as_01_with_padding() {
        val out = ExtJsArrayPreprocessor.toStrictJson("new Date(2025,0,5)")
        out shouldBe "\"2025-01-05\""
    }

    @Test
    fun multi_line_array_with_dates_preserved() {
        val raw = """[
[1, "Н", new Date(2025,8,5,0,0,0,0), 3],
 [2, "Б", new Date(2025,8,10,0,0,0,0), 12]
]"""
        val strict = ExtJsArrayPreprocessor.toStrictJson(raw)
        strict shouldNotContain "new Date"
        strict shouldContain "2025-09-05"
        strict shouldContain "2025-09-10"
        // Result MUST be valid JSON now.
        val parsed = Json.parseToJsonElement(strict)
        require(parsed is JsonArray)
        parsed.size shouldBe 2
    }

    @Test
    fun text_without_date_passes_through() {
        val raw = """[[1, 2, 3], ["a", "b"]]"""
        ExtJsArrayPreprocessor.toStrictJson(raw) shouldBe raw
    }
}
