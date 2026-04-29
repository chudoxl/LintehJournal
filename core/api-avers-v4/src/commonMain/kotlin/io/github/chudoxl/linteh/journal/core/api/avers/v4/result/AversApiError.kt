package io.github.chudoxl.linteh.journal.core.api.avers.v4.result

/**
 * Typed AVERS errors — sealed so `when` is exhaustive and Swift consumers see clean enums.
 *
 * D-06 reconciliation: [AntiBotChallenge] carries raw HTML so Phase 3 WebView fallback
 * (D-06) has the captcha page content. Sealed superset of D-11 (object → data class with
 * payload); no API breakage.
 *
 * Mapping rules from Ktor exceptions (used by AversApi endpoint functions):
 *  - HttpRequestTimeoutException, IOException → [Network]
 *  - SerializationException → ApiResult.Mismatch (NOT AversApiError — distinct outcome)
 *  - ResponseException with status 401 (after re-login fails) → [Unauthorized]
 *  - ResponseException with status >= 500 or other 4xx → [Server]
 *  - HTML response detected by AntiBotDetector → [AntiBotChallenge]
 *  - KillSwitchClient severity == block → [KillSwitchTriggered]
 *
 * Plan 02-02 SUMMARY also identified server-side semantic errors that map onto these variants:
 *  - Bad credentials (server replies `[error_symbol]`) → [Unauthorized]
 *  - Server `Content-Length` ≠ actual body bytes → [Network] (Ktor surfaces as IO)
 *  - Body unparseable post-`new Date()` substitution → ApiResult.Mismatch
 */
sealed class AversApiError {
    /** 401 returned by AVERS AND re-login attempt failed (D-26). Phase 3 Auth UI catches this. */
    data object Unauthorized : AversApiError()

    /**
     * AVERS returned an HTML CAPTCHA page (anti-bot triggered). Phase 3 (D-06) catches this
     * and opens a WebView with [rawHtml] for the user to solve.
     */
    data class AntiBotChallenge(val rawHtml: String) : AversApiError()

    /** Network error — timeout, connection refused, DNS failure, IncompleteRead. */
    data object Network : AversApiError()

    /**
     * AVERS returned valid JSON but the shape doesn't match expectations (e.g., missing
     * critical field). Distinct from [ApiResult.Mismatch] — this variant exists for cases
     * where the contract violation is severe enough to be a hard error.
     */
    data class ContractMismatch(
        val rawDump: String,
        val missingFields: List<String>,
        val endpoint: String,
    ) : AversApiError()

    /** Server returned 4xx or 5xx (other than 401). */
    data class Server(val httpCode: Int) : AversApiError()

    /** Kill-switch (D-12) triggered: severity == "block" and build differs. */
    data object KillSwitchTriggered : AversApiError()
}
