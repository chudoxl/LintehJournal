package io.github.chudoxl.linteh.journal.core.network

import co.touchlab.kermit.Logger
import io.github.chudoxl.linteh.journal.core.network.credentials.CredentialProvider
import io.github.chudoxl.linteh.journal.core.network.logging.KermitKtorLogger
import io.github.chudoxl.linteh.journal.core.network.plugins.HttpRequestRedactor
import io.github.chudoxl.linteh.journal.core.network.plugins.installAversAuthInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private const val TAG = "HttpClientFactory"

/**
 * Per-account [HttpClient] factory with caching + lifecycle management.
 *
 * Pattern: ARCHITECTURE.md Pattern 3 (per-account HttpClient) + per-account isolation
 * invariant (Pitfall #6 mitigation).
 *
 * Plugin chain installation order (D-10):
 *   1. HttpCookies (storage from cookiesStorageProvider — Plan 02-05 wires Room-backed)
 *   2. ContentNegotiation (Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }) — D-09
 *   3. Logging (KermitKtorLogger + redactor + sanitizeHeader Auth/Cookie/Set-Cookie + level depending on isDebug) — D-28
 *   4. HttpTimeout (connect=10s, request=30s, socket=15s)
 *   5. HttpRequestRetry (maxRetries=5, exponentialDelay 1s..30s base=2.0, retry on 429/502/503/504) — D-05
 *   6. DefaultRequest (baseUrl + Accept-Language ru-RU + Accept JSON + X-Requested-With XMLHttpRequest + UA mimic)
 *   7. AversAuthInterceptor via HttpSend (D-26, Pitfall #4 login-endpoint skip)
 *
 * UA mimic — Phase 2 starts with desktop Safari (matching Chrome DevTools captures).
 * Pitfall #1 risk: AVERS may serve different ExtJS variant for mobile UA. First Android run
 * in Phase 4 verifies; if divergence found, single-line UA swap here.
 *
 * @param engine Platform-specific Ktor engine — Darwin on iOS, OkHttp on Android. Wired by caller.
 * @param cookiesStorageProvider Function producing CookiesStorage per accountId. Plan 02-05 wires
 *   { id -> RoomCookiesStorage(cookieDao, id) }; tests use { _ -> AcceptAllCookiesStorage() }.
 * @param credentialProvider Phase 2: NoopCredentialProvider; Phase 3: KVault-backed.
 * @param redactor Body redactor, applied via KermitKtorLogger.
 * @param loginCall AVERS-specific login function (provided by `:core:api-avers-v4`).
 *   Phase 2 tests pass `{ _, _ -> false }` (no-op); Plan 02-06 wires real login.
 * @param baseUrl AVERS host. Default: `https://journal.school28-kirov.ru`.
 * @param userAgent Mobile or desktop Safari mimic — parameterized for Pitfall #1.
 * @param isDebug Enables LogLevel.ALL when true; LogLevel.NONE otherwise (D-28).
 */
class HttpClientFactory(
    private val engine: HttpClientEngine,
    private val cookiesStorageProvider: (accountId: String) -> CookiesStorage,
    private val credentialProvider: CredentialProvider,
    private val redactor: HttpRequestRedactor,
    private val loginCall: suspend (login: String, password: String) -> Boolean = { _, _ -> false },
    private val baseUrl: String = "https://journal.school28-kirov.ru",
    private val userAgent: String = DESKTOP_SAFARI_UA,
    private val isDebug: Boolean = false,
) {
    private val mutex = Mutex()
    private val clients = mutableMapOf<String, HttpClient>()

    /** Returns a cached HttpClient for [accountId], creating one if absent. */
    suspend fun forAccount(accountId: String): HttpClient = mutex.withLock {
        clients.getOrPut(accountId) {
            Logger.i(TAG) { "Building HttpClient for accountId=$accountId" }
            buildClient(accountId)
        }
    }

    /** Closes and removes the HttpClient for [accountId]. Used by AccountDataPurger (Plan 02-05). */
    suspend fun evict(accountId: String): Unit = mutex.withLock {
        clients.remove(accountId)?.let {
            Logger.i(TAG) { "Evicting HttpClient for accountId=$accountId" }
            it.close()
        }
    }

    private fun buildClient(accountId: String): HttpClient = HttpClient(engine) {
        install(HttpCookies) {
            storage = cookiesStorageProvider(accountId)
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                },
            )
        }
        install(Logging) {
            logger = KermitKtorLogger(redactor)
            level = if (isDebug) LogLevel.ALL else LogLevel.NONE
            sanitizeHeader { name ->
                name.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                    name.equals(HttpHeaders.Cookie, ignoreCase = true) ||
                    name.equals(HttpHeaders.SetCookie, ignoreCase = true)
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 15_000
        }
        install(HttpRequestRetry) {
            maxRetries = 5
            retryIf { _, response -> response.status.value in listOf(429, 502, 503, 504) }
            retryOnExceptionIf { _, _ -> false } // No retry on engine exceptions by default
            exponentialDelay(base = 2.0, baseDelayMs = 1_000, maxDelayMs = 30_000)
        }
        install(DefaultRequest) {
            // Parse baseUrl into URLBuilder so DefaultRequest assigns scheme + host
            val parsed = URLBuilder(baseUrl)
            url.protocol = parsed.protocol
            url.host = parsed.host
            url.port = parsed.port
            header(HttpHeaders.AcceptLanguage, "ru-RU,ru;q=0.9,en;q=0.8")
            header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
            header("X-Requested-With", "XMLHttpRequest")
            header(HttpHeaders.UserAgent, userAgent)
        }
    }.apply {
        // D-26 + Pitfall #4: install AversAuthInterceptor AFTER HttpClient creation
        // because plugin(HttpSend).intercept needs the live client.
        installAversAuthInterceptor(accountId, credentialProvider, loginCall)
    }

    companion object {
        const val DESKTOP_SAFARI_UA: String =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/17.4 Safari/605.1.15"

        /** Pitfall #1 swap candidate. Phase 4 first Android run verifies. */
        const val MOBILE_SAFARI_UA: String =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
    }
}
