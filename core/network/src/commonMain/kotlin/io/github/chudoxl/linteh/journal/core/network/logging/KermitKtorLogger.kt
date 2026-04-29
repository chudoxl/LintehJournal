package io.github.chudoxl.linteh.journal.core.network.logging

import co.touchlab.kermit.Logger as KermitLogger
import io.github.chudoxl.linteh.journal.core.network.plugins.HttpRequestRedactor
import io.ktor.client.plugins.logging.Logger as KtorLogger

/**
 * Adapter from Ktor's [io.ktor.client.plugins.logging.Logger] interface to Kermit
 * (Logcat on Android, OSLog on iOS).
 *
 * D-28 addendum: applies [HttpRequestRedactor] to every message before passing to Kermit.
 * This is the one chokepoint where bodies and headers must be masked even when running in
 * debug-build with `LogLevel.ALL`. The Ktor `sanitizeHeader { ... }` block masks specific
 * headers BEFORE they reach this Logger; the redactor catches form/JSON bodies and any
 * leaked sensitive keys.
 *
 * Tag: `KtorClient`. Severity: `Logger.i` for normal messages; consumers can change.
 */
class KermitKtorLogger(
    private val redactor: HttpRequestRedactor,
    private val tag: String = "KtorClient",
) : KtorLogger {
    override fun log(message: String) {
        // Defense-in-depth: redact even though sanitizeHeader should have masked headers
        // and structured bodies are already masked at request-build time.
        val safe = redactor.redact(message)
        KermitLogger.i(tag) { safe }
    }
}
