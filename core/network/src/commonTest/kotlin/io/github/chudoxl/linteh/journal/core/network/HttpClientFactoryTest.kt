package io.github.chudoxl.linteh.journal.core.network

import io.github.chudoxl.linteh.journal.core.network.credentials.NoopCredentialProvider
import io.github.chudoxl.linteh.journal.core.network.plugins.HttpRequestRedactor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.pluginOrNull
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class HttpClientFactoryTest {
    private fun newFactory() = HttpClientFactory(
        engine = MockEngine { respond(content = "{}", status = HttpStatusCode.OK) },
        cookiesStorageProvider = { _ -> AcceptAllCookiesStorage() },
        credentialProvider = NoopCredentialProvider,
        redactor = HttpRequestRedactor(),
        loginCall = { _, _ -> false },
        isDebug = true,
    )

    @Test
    fun forAccount_caches_client_per_accountId() = runTest {
        val factory = newFactory()
        val a1 = factory.forAccount("alice")
        val a2 = factory.forAccount("alice")
        a1 shouldBe a2 // same instance (cache hit)
    }

    @Test
    fun forAccount_returns_different_clients_for_different_accounts_isolation_pitfall_6() = runTest {
        val factory = newFactory()
        val alice = factory.forAccount("alice")
        val bob = factory.forAccount("bob")
        alice shouldNotBe bob
    }

    @Test
    fun evict_closes_and_removes_cached_client() = runTest {
        val factory = newFactory()
        val a1 = factory.forAccount("alice")
        factory.evict("alice")
        val a2 = factory.forAccount("alice")
        a1 shouldNotBe a2 // cache cleared, new instance built
    }

    @Test
    fun built_client_has_required_plugins_installed() = runTest {
        val factory = newFactory()
        val client = factory.forAccount("default")

        // Probe plugin presence via Ktor 3.x pluginOrNull(...) — non-null when installed.
        client.pluginOrNull(HttpTimeout) shouldNotBe null
        client.pluginOrNull(HttpRequestRetry) shouldNotBe null
        client.pluginOrNull(ContentNegotiation) shouldNotBe null
        client.pluginOrNull(HttpCookies) shouldNotBe null
        client.pluginOrNull(Logging) shouldNotBe null
        client.pluginOrNull(DefaultRequest) shouldNotBe null
    }
}
