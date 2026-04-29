package io.github.chudoxl.linteh.journal.core.api.avers.v4.killswitch

import io.github.chudoxl.linteh.journal.core.api.avers.v4.result.ApiResult
import io.github.chudoxl.linteh.journal.core.api.avers.v4.result.AversApiError
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Verifies D-12 (config schema), D-13 (fail-open), D-14 (cold-start cache), and T-02-38
 * (severity=block hardening) for [KillSwitchClient].
 *
 * MockEngine drives the Ktor pipeline without ContentNegotiation — KillSwitchClient parses
 * via `bodyAsText()` + `Json.parseToJsonElement` + a custom KSerializer, matching the Plan
 * 02-06 DTO convention.
 *
 * NOTE: HttpRequestTimeoutException is not exercised in MockEngine (the timeout block is
 * a no-op without the HttpTimeout plugin installed and MockEngine responds synchronously).
 * The fail-open path is covered via 404 (non-2xx status throws inside the runCatching arm)
 * and malformed JSON — all routed through the same `runCatching { ... }.getOrElse { ... }`
 * arm that catches HttpRequestTimeoutException at runtime.
 */
class KillSwitchClientTest {

    /**
     * Builds a Ktor HttpClient over MockEngine that walks through [responseSequence] one
     * call at a time. Each entry is `(status, body, contentType)`.
     *
     * Returns the client + a `() -> Int` that reports how many MockEngine calls were made
     * (used by D-14 cache assertions).
     */
    private fun client(
        responseSequence: List<Triple<HttpStatusCode, String, String>>,
    ): Pair<HttpClient, () -> Int> {
        var idx = 0
        var calls = 0
        val engine = MockEngine { _ ->
            calls++
            val (status, body, ct) = responseSequence[idx.coerceAtMost(responseSequence.size - 1)]
            idx++
            respond(content = body, status = status, headers = headersOf("Content-Type", ct))
        }
        val httpClient = HttpClient(engine) {
            // KillSwitchClient uses bodyAsText() + manual Json parse, so no ContentNegotiation.
            // expectSuccess=false means 4xx flows through as a successful HttpResponse — the
            // client itself enforces 2xx via response.status.isSuccess() and routes failures
            // into the same runCatching/getOrElse fail-open arm.
            expectSuccess = false
        }
        return httpClient to { calls }
    }

    @Test
    fun happy_path_returns_success_with_info_config() = runTest {
        val (httpClient, _) = client(
            listOf(
                Triple(
                    HttpStatusCode.OK,
                    """{"latestSupportedAversBuild":"23813","severity":"info","minAppVersion":"0.2.0"}""",
                    "application/json",
                ),
            ),
        )
        val ks = KillSwitchClient(httpClient = httpClient, currentAversBuild = "23813")
        val r = ks.checkOrFailOpen()
        r.shouldBeInstanceOf<ApiResult.Success<KillSwitchConfig>>()
        r.value.severity shouldBe Severity.info
        r.value.latestSupportedAversBuild shouldBe "23813"
    }

    @Test
    fun severity_warning_returns_success() = runTest {
        val (httpClient, _) = client(
            listOf(
                Triple(
                    HttpStatusCode.OK,
                    """{"latestSupportedAversBuild":"24000","severity":"warning","message":"new build out","minAppVersion":"0.2.0"}""",
                    "application/json",
                ),
            ),
        )
        val ks = KillSwitchClient(httpClient = httpClient, currentAversBuild = "23813")
        val r = ks.checkOrFailOpen()
        r.shouldBeInstanceOf<ApiResult.Success<KillSwitchConfig>>()
        r.value.severity shouldBe Severity.warning
        r.value.message shouldBe "new build out"
    }

    @Test
    fun severity_block_with_build_mismatch_triggers_kill_switch() = runTest {
        val (httpClient, _) = client(
            listOf(
                Triple(
                    HttpStatusCode.OK,
                    """{"latestSupportedAversBuild":"24000","severity":"block","minAppVersion":"0.2.0"}""",
                    "application/json",
                ),
            ),
        )
        val ks = KillSwitchClient(httpClient = httpClient, currentAversBuild = "23813")
        val r = ks.checkOrFailOpen()
        r.shouldBeInstanceOf<ApiResult.Failure>()
        r.error shouldBe AversApiError.KillSwitchTriggered
    }

    @Test
    fun severity_block_with_same_build_returns_success_hardening_t_02_38() = runTest {
        val (httpClient, _) = client(
            listOf(
                Triple(
                    HttpStatusCode.OK,
                    """{"latestSupportedAversBuild":"23813","severity":"block","minAppVersion":"0.2.0"}""",
                    "application/json",
                ),
            ),
        )
        val ks = KillSwitchClient(httpClient = httpClient, currentAversBuild = "23813")
        val r = ks.checkOrFailOpen()
        // Hardening: severity=block on the SAME build is treated as accident → Success
        r.shouldBeInstanceOf<ApiResult.Success<KillSwitchConfig>>()
        r.value.severity shouldBe Severity.block
        r.value.latestSupportedAversBuild shouldBe "23813"
    }

    @Test
    fun http_404_returns_fail_open_d_13() = runTest {
        val (httpClient, _) = client(
            listOf(Triple(HttpStatusCode.NotFound, "", "text/plain")),
        )
        val ks = KillSwitchClient(httpClient = httpClient, currentAversBuild = "23813")
        val r = ks.checkOrFailOpen()
        r.shouldBeInstanceOf<ApiResult.Success<KillSwitchConfig>>()
        // Default config — severity=info, latestSupportedAversBuild = currentAversBuild
        r.value.severity shouldBe Severity.info
        r.value.latestSupportedAversBuild shouldBe "23813"
    }

    @Test
    fun malformed_json_returns_fail_open() = runTest {
        val (httpClient, _) = client(
            listOf(Triple(HttpStatusCode.OK, "<<<not json>>>", "application/json")),
        )
        val ks = KillSwitchClient(httpClient = httpClient, currentAversBuild = "23813")
        val r = ks.checkOrFailOpen()
        r.shouldBeInstanceOf<ApiResult.Success<KillSwitchConfig>>()
        r.value.severity shouldBe Severity.info
        r.value.latestSupportedAversBuild shouldBe "23813"
    }

    @Test
    fun second_call_uses_cache_d_14() = runTest {
        val (httpClient, callCount) = client(
            listOf(
                Triple(
                    HttpStatusCode.OK,
                    """{"latestSupportedAversBuild":"23813","severity":"info","minAppVersion":"0.2.0"}""",
                    "application/json",
                ),
            ),
        )
        val ks = KillSwitchClient(httpClient = httpClient, currentAversBuild = "23813")
        ks.checkOrFailOpen()
        ks.checkOrFailOpen()
        callCount() shouldBe 1  // second call hits the in-memory cache, no second HTTP fetch
    }

    @Test
    fun invalidateCache_forces_refetch() = runTest {
        val (httpClient, callCount) = client(
            listOf(
                Triple(
                    HttpStatusCode.OK,
                    """{"latestSupportedAversBuild":"23813","severity":"info","minAppVersion":"0.2.0"}""",
                    "application/json",
                ),
                Triple(
                    HttpStatusCode.OK,
                    """{"latestSupportedAversBuild":"24000","severity":"warning","minAppVersion":"0.2.0"}""",
                    "application/json",
                ),
            ),
        )
        val ks = KillSwitchClient(httpClient = httpClient, currentAversBuild = "23813")
        ks.checkOrFailOpen()
        ks.invalidateCache()
        val second = ks.checkOrFailOpen()
        callCount() shouldBe 2
        second.shouldBeInstanceOf<ApiResult.Success<KillSwitchConfig>>()
        // Cache now reflects the second response
        second.value.severity shouldBe Severity.warning
    }
}
