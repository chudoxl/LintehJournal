package io.github.chudoxl.linteh.journal.core.network.plugins

import co.touchlab.kermit.Logger
import io.github.chudoxl.linteh.journal.core.network.credentials.CredentialProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpStatusCode

private const val TAG = "AversAuthInterceptor"

/**
 * D-26 auto-relogin via custom HttpSend interceptor (chosen over Ktor `bearer { }` plugin
 * because AVERS uses cookie-session, not bearer tokens — `bearer.refreshTokens` ergonomics
 * fight a cookie model).
 *
 * Behavior:
 *  1. Skip interceptor for /login endpoint (Pitfall #4 mitigation — prevents infinite
 *     401 loop when password is wrong: login itself returns 401, interceptor would call
 *     login again, ad infinitum).
 *  2. Execute the request normally.
 *  3. If response is 401 Unauthorized AND credentials are available via [CredentialProvider]:
 *     - Call [loginCall] with stored credentials.
 *     - On success: replay original request (HttpCookies plugin will use the new session cookies).
 *     - On failure: return original 401 response (caller maps to `AversApiError.Unauthorized`
 *       at the `:core:api-avers-v4` boundary).
 *  4. If 401 AND no credentials: return original 401 (caller maps as above — Phase 3 Auth UI
 *     shows login screen).
 *
 * @param accountId scope passed to [CredentialProvider.get]; per-account isolation invariant.
 * @param credentialProvider Phase 2: NoopCredentialProvider; Phase 3: KVault-backed.
 * @param loginCall AVERS-specific login callable supplied by `:core:api-avers-v4`. Returns
 *   `true` on successful login (cookies updated), `false` on failure (wrong password etc.).
 *   Phase 2 stub passes a noop lambda for tests.
 */
fun HttpClient.installAversAuthInterceptor(
    accountId: String,
    credentialProvider: CredentialProvider,
    loginCall: suspend (login: String, password: String) -> Boolean,
) {
    plugin(HttpSend).intercept { request ->
        // Pitfall #4 mitigation: skip interceptor for the login endpoint itself.
        // `request.url` is URLBuilder; URLBuilder exposes encodedPathSegments (Url alone exposes
        // encodedPath). Joining segments yields the same `/seg1/seg2` shape as encodedPath.
        val encodedPath = request.url.encodedPathSegments.joinToString("/")
        if (encodedPath.contains("login", ignoreCase = true)) {
            return@intercept execute(request)
        }

        val originalCall = execute(request)
        if (originalCall.response.status != HttpStatusCode.Unauthorized) {
            return@intercept originalCall
        }

        val creds = credentialProvider.get(accountId)
        if (creds == null) {
            Logger.i(TAG) { "401 received and no creds for accountId=$accountId; surfacing 401" }
            return@intercept originalCall
        }

        val ok = loginCall(creds.first, creds.second)
        if (!ok) {
            Logger.i(TAG) { "401 received, re-login failed for accountId=$accountId; surfacing 401" }
            return@intercept originalCall
        }

        Logger.i(TAG) { "401 received, re-login succeeded for accountId=$accountId; replaying request" }
        execute(request) // Replay; HttpCookies has new session cookies
    }
}
