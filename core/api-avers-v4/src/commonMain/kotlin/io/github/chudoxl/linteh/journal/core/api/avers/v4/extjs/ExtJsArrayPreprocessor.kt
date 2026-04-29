package io.github.chudoxl.linteh.journal.core.api.avers.v4.extjs

/**
 * Pre-processor that turns AVERS' ExtJS-flavoured response text into strict JSON before
 * it reaches kotlinx.serialization.
 *
 * AVERS emits ExtJS-style code that the original ExtJS client passes through `eval()`,
 * so the body is JavaScript, not JSON. The single non-JSON construct that breaks
 * kotlinx.serialization is the `new Date(YYYY, M_minus_1, D, …)` literal embedded in
 * `/act/GET_STUDENT_DAIRY` and `/act/GET_STUDENT_JOURNAL_DATA` rows (Plan 02-02 SUMMARY,
 * "Response shape — NOT strict JSON").
 *
 * **Critical**: JS `Date` constructor uses 0-indexed month, so `new Date(2026, 3, 27)` =
 * 2026-04-27. We add 1 to the month when emitting ISO.
 *
 * Implementation: a single regex pass that substitutes each `new Date(Y, M, D, …)` with
 * `"YYYY-MM-DD"` (a JSON string). The result is still a valid JSON document because
 * arrays-of-arrays compose with strings without further changes.
 *
 * Limitations: hours/minutes/seconds are dropped (AVERS observed only emits 0,0,0,0 in
 * captured fixtures — the trailing args are always zero). If AVERS starts emitting non-zero
 * time-of-day, Phase 4 must extend this preprocessor to ISO-8601 timestamps.
 */
internal object ExtJsArrayPreprocessor {

    /**
     * Matches:
     *  `new Date( YYYY , M_minus_1 , D , H , Mn , S , MS )`
     *  with arbitrary whitespace, optional trailing comma list. Y/M/D are required;
     *  the rest are tolerated and discarded.
     */
    private val NEW_DATE_REGEX = Regex(
        """new\s+Date\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,[^)]*)?\)""",
    )

    fun toStrictJson(body: String): String =
        NEW_DATE_REGEX.replace(body) { match ->
            val year = match.groupValues[1].toInt()
            val monthZeroBased = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            val month = monthZeroBased + 1
            // Zero-padded month + day for ISO-8601 ordering. No Locale-sensitive helpers.
            val mm = if (month < 10) "0$month" else month.toString()
            val dd = if (day < 10) "0$day" else day.toString()
            "\"$year-$mm-$dd\""
        }
}
