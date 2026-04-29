package io.github.chudoxl.linteh.journal.core.api.avers.v4.killswitch

import co.touchlab.kermit.Logger
import io.github.chudoxl.linteh.journal.core.api.avers.v4.result.ApiResult
import io.github.chudoxl.linteh.journal.core.api.avers.v4.result.AversApiError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private const val TAG = "KillSwitchClient"

/**
 * Fetches kill-switch config from GitHub Pages at app cold-start (D-14).
 *
 * **D-13 fail-open** — any failure (timeout, 404, JSON parse error, GitHub Pages downtime)
 * returns [ApiResult.Success] with a permissive default config and never blocks the user
 * from the offline-first app.
 *
 * **D-14 caching** — the result is cached in-memory after the first successful interpretation;
 * subsequent calls in the same session return the cached value. Phase 6 may extend with
 * periodic background re-fetch (BGAppRefreshTask / WorkManager).
 *
 * **Hardening (T-02-38 mitigation)** — `severity=block` is honored only when
 * `latestSupportedAversBuild != currentAversBuild`. Prevents an accidental `severity=block`
 * on the current build (e.g., a typo in the static JSON) from bricking the entire user base.
 *
 * Body parsing follows the Plan 02-06 convention: `bodyAsText()` plus `Json.parseToJsonElement`
 * with a custom KSerializer ([KillSwitchConfigSerializer]). The kotlinx-serialization compiler
 * plugin is NOT applied to `:core:api-avers-v4` (only the runtime); custom KSerializers per
 * `@Serializable(with = ...)` DTO are the established pattern.
 *
 * @property httpClient any pre-configured [HttpClient]. The kill-switch URL is non-AVERS so the
 *   AVERS-specific plugin chain (cookies, redactor, retry) is not required — but it does no
 *   harm either if the same factory client is reused. Caller must ensure the [HttpClient]
 *   has [io.ktor.client.plugins.HttpTimeout] installed if `timeoutMillis` is meant to apply;
 *   the project [io.github.chudoxl.linteh.journal.core.network.HttpClientFactory] already
 *   installs it.
 * @property killSwitchUrl deployable URL of the static config; defaults to the project's
 *   GitHub Pages location.
 * @property currentAversBuild build identifier of AVERS that the app is built against
 *   (Phase 2 baseline `"23813"` — see Plan 02-02 SUMMARY).
 * @property currentAppVersion current app's semantic version (mirrors `BuildKonfig.VERSION_NAME`).
 * @property timeoutMillis request timeout for the kill-switch fetch (default 5s — short
 *   enough not to block app launch, long enough for a typical CDN response).
 */
class KillSwitchClient(
    private val httpClient: HttpClient,
    private val killSwitchUrl: String = "https://chudoxl.github.io/LintehJournal/api-config.json",
    private val currentAversBuild: String = "23813",
    private val currentAppVersion: String = "0.2.0",
    private val timeoutMillis: Long = 5_000,
) {
    private val mutex = Mutex()
    private var cached: KillSwitchConfig? = null
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Cold-start kill-switch check.
     *
     * @return [ApiResult.Success] with the config (cached or freshly fetched) on the happy path
     *   AND on every fail-open scenario (timeout, 404, malformed JSON, network error). Returns
     *   [ApiResult.Failure] wrapping [AversApiError.KillSwitchTriggered] ONLY when
     *   `severity=block` AND the build identifier differs (T-02-38 hardening).
     */
    suspend fun checkOrFailOpen(): ApiResult<KillSwitchConfig> {
        cached?.let { cfg ->
            Logger.d(TAG) { "Using cached kill-switch config (severity=${cfg.severity})" }
            return interpretConfig(cfg)
        }

        return mutex.withLock {
            // Re-check inside the lock (double-checked) to handle concurrent first calls.
            cached?.let { return@withLock interpretConfig(it) }

            val cfg = runCatching {
                val response = httpClient.get(killSwitchUrl) {
                    timeout { requestTimeoutMillis = timeoutMillis }
                }
                if (!response.status.isSuccess()) {
                    error("Non-2xx status ${response.status.value} from $killSwitchUrl")
                }
                val body = response.bodyAsText()
                val element = json.parseToJsonElement(body)
                json.decodeFromJsonElement(KillSwitchConfigSerializer, element)
            }.getOrElse { e ->
                // D-13 fail-open: timeout, 404, DNS failure, malformed JSON — all collapse
                // to the default config. Never blocks the user.
                when (e) {
                    is HttpRequestTimeoutException ->
                        Logger.w(TAG) { "Kill-switch fetch timed out — fail-open" }
                    else ->
                        Logger.w(TAG, e) { "Kill-switch fetch failed: ${e.message} — fail-open" }
                }
                defaultConfig()
            }

            cached = cfg
            interpretConfig(cfg)
        }
    }

    /** Hardening + interpretation. Pure (no I/O) — safe to call from inside the lock. */
    private fun interpretConfig(cfg: KillSwitchConfig): ApiResult<KillSwitchConfig> {
        // T-02-38 hardening: severity=block honored only when builds differ.
        // Same-build severity=block is treated as accident (e.g., typo in the static JSON)
        // and downgraded to Success, avoiding a self-inflicted DoS on the family/classroom
        // user base.
        val shouldBlock = cfg.severity == Severity.block &&
            cfg.latestSupportedAversBuild != currentAversBuild

        return if (shouldBlock) {
            Logger.w(TAG) {
                "Kill-switch BLOCK active: latestSupported=${cfg.latestSupportedAversBuild}, " +
                    "current=$currentAversBuild"
            }
            ApiResult.Failure(AversApiError.KillSwitchTriggered)
        } else {
            ApiResult.Success(cfg)
        }
    }

    private fun defaultConfig(): KillSwitchConfig = KillSwitchConfig(
        latestSupportedAversBuild = currentAversBuild,
        message = "",
        severity = Severity.info,
        minAppVersion = currentAppVersion,
    )

    /**
     * Test/debug only: clear in-memory cache. Phase 6 may use this for periodic
     * background re-fetch (BGAppRefreshTask / WorkManager).
     */
    suspend fun invalidateCache() = mutex.withLock { cached = null }
}
