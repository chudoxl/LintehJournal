package io.github.chudoxl.linteh.journal.core.network.plugins

/**
 * Body + header value redactor — D-28 mandatory addendum.
 *
 * Masks values for sensitive keys in:
 *  - Form-encoded bodies: `password=secret&foo=bar` -> `password=***REDACTED***&foo=bar`
 *  - JSON bodies: `"password": "secret"` -> `"password": "***REDACTED***"`
 *  - Header values: when applied via [redactHeaderValue]
 *
 * Pitfall #2 mitigation: regex matches the KEY (form-encoded `\bkey=`, JSON `"key":\s*"`),
 * NOT arbitrary substrings. Benign content like "You forgot your password to the website"
 * is NOT redacted because the literal `password` is not followed by `=` or `":` in that text.
 *
 * Idempotent — applying twice is safe.
 *
 * Wired into the Ktor `Logging` plugin via [io.github.chudoxl.linteh.journal.core.network.HttpClientFactory] —
 * runs in BOTH debug (LogLevel.ALL) and release (LogLevel.NONE -> effectively dead-code, but
 * the redactor is always installed as defense-in-depth).
 *
 * Canary: `kanareyka_PASSWORD_DO_NOT_LEAK_42` (ROADMAP success #5 / Pitfall #5) — when
 * sent as a `password=` form value or JSON `"password": "kanareyka..."`, the redactor
 * MUST mask it. Tested in `HttpRequestRedactorTest`.
 */
class HttpRequestRedactor {
    private val sensitiveKeys: List<String> = listOf(
        "password",
        "pwd",
        "pass",
        "cookie",
        "authorization",
        "set-cookie",
        "token",
    )

    /**
     * Redacts sensitive values in [body]. [contentType] is informational; redaction logic
     * is regex-based and works on both form-encoded and JSON content.
     */
    fun redact(body: String, contentType: String? = null): String {
        var result = body
        sensitiveKeys.forEach { key ->
            val escapedKey = Regex.escape(key)
            // Form-encoded: \bkey=value(&|<end>)
            // - \b before key -> word boundary (prevents matching 'foo_password=' as the key 'password')
            // - [^&\s"]* -> any chars until &, whitespace, or " (handles trailing JSON delimiters)
            result = result.replace(
                Regex("(?i)(\\b$escapedKey=)[^&\\s\"]*"),
                "$1***REDACTED***",
            )
            // JSON: "key":"value" or "key" : "value"
            result = result.replace(
                Regex("(?i)(\"$escapedKey\"\\s*:\\s*\")[^\"]*"),
                "$1***REDACTED***",
            )
        }
        return result
    }

    /**
     * Mask sensitive header values. Used by Ktor `sanitizeHeader { name -> ... }` block.
     */
    fun redactHeaderValue(headerName: String, headerValue: String): String =
        if (sensitiveKeys.any { it.equals(headerName, ignoreCase = true) }) "***REDACTED***"
        else headerValue
}
