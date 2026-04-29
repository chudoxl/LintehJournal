package io.github.chudoxl.linteh.journal.core.api.avers.v4.antibot

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Detects when AVERS responded with an HTML page instead of JSON — typically a CAPTCHA
 * gating page (anti-bot D-05 / Pitfall #11).
 *
 * Strategy:
 *  - Content-Type "text/html" is the strong signal — checked at the HTTP layer (caller
 *    inspects response.contentType()).
 *  - Body content sniff: starts with `<!DOCTYPE`, `<html`, or contains `<form` paired with
 *    a `captcha` keyword (case-insensitive).
 *
 * Phase 2: pure-pattern detection. Phase 3 may extend with CAPTCHA-specific markers (e.g.,
 * AVERS-known captcha CSS class names) once observed in HAR captures. Plan 02-02 SUMMARY
 * notes no anti-bot was tripped during the 28-request capture corpus, so the sanitized
 * fixtures yield zero matches against this detector. The detection patterns are defensive
 * preparation for a real captcha encounter in production.
 *
 * Returns true if the body is HTML; false if it looks like JSON or empty.
 */
class AntiBotDetector {

    /** Sniff body content. Used after a body has been read (raw text). */
    fun isCaptchaBody(body: String): Boolean {
        val lower = body.trimStart().take(2048).lowercase()
        return lower.startsWith("<!doctype html") ||
            lower.startsWith("<html") ||
            (lower.contains("<form") && lower.contains("captcha"))
    }

    /** Sniff a parsed JsonElement when the parser already ran. */
    fun isCaptcha(json: JsonElement): Boolean {
        // If parser succeeded with a JsonPrimitive whose content looks like HTML,
        // that's an anti-bot tell.
        if (json !is JsonPrimitive) return false
        if (!json.isString) return false
        return isCaptchaBody(json.content)
    }
}
