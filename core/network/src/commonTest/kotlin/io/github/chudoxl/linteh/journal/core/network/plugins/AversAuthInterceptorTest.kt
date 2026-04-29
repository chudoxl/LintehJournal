package io.github.chudoxl.linteh.journal.core.network.plugins

import io.github.chudoxl.linteh.journal.core.network.credentials.CredentialProvider
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpSend
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AversAuthInterceptorTest {

    private class StubCredentialProvider(val creds: Pair<String, String>?) : CredentialProvider {
        override suspend fun get(accountId: String): Pair<String, String>? = creds
    }

    @Test
    fun interceptor_retries_after_login_when_401_and_creds_available_d26_happy_path() = runTest {
        var requestIndex = 0
        val engine = MockEngine { _ ->
            requestIndex++
            // First call: 401. Second call (after re-login + replay): 200.
            if (requestIndex == 1) respond(content = "", status = HttpStatusCode.Unauthorized)
            else respond(content = "{}", status = HttpStatusCode.OK)
        }

        var loginCalls = 0
        val client = HttpClient(engine) { install(HttpSend) }
        client.installAversAuthInterceptor(
            accountId = "default",
            credentialProvider = StubCredentialProvider("user" to "pwd"),
            loginCall = { _, _ -> loginCalls++; true },
        )

        val resp: HttpResponse = client.get("https://journal.school28-kirov.ru/journal/api/marks")
        assertEquals(HttpStatusCode.OK, resp.status)
        loginCalls shouldBe 1 // exactly one login attempt
    }

    @Test
    fun interceptor_skips_login_endpoint_pitfall_4() = runTest {
        var loginCalls = 0
        val engine = MockEngine { _ ->
            // EVERY request returns 401. Without the skip, interceptor would loop forever.
            respond(content = "", status = HttpStatusCode.Unauthorized)
        }

        val client = HttpClient(engine) { install(HttpSend) }
        client.installAversAuthInterceptor(
            accountId = "default",
            credentialProvider = StubCredentialProvider("user" to "pwd"),
            loginCall = { _, _ -> loginCalls++; true },
        )

        // Hit a /login URL directly — interceptor must NOT trigger login retry.
        val resp = client.get("https://journal.school28-kirov.ru/auth/login")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        loginCalls shouldBe 0 // login endpoint skipped — no retry attempt
    }

    @Test
    fun interceptor_returns_401_when_no_creds_available() = runTest {
        var loginCalls = 0
        val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.Unauthorized) }
        val client = HttpClient(engine) { install(HttpSend) }
        client.installAversAuthInterceptor(
            accountId = "default",
            credentialProvider = StubCredentialProvider(null), // no creds
            loginCall = { _, _ -> loginCalls++; true },
        )

        val resp = client.get("https://journal.school28-kirov.ru/journal/api/marks")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        loginCalls shouldBe 0 // never attempted login (no creds)
    }

    @Test
    fun interceptor_returns_401_when_relogin_fails() = runTest {
        var loginCalls = 0
        val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.Unauthorized) }
        val client = HttpClient(engine) { install(HttpSend) }
        client.installAversAuthInterceptor(
            accountId = "default",
            credentialProvider = StubCredentialProvider("user" to "wrongpwd"),
            loginCall = { _, _ -> loginCalls++; false }, // re-login fails
        )

        val resp = client.get("https://journal.school28-kirov.ru/journal/api/marks")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        loginCalls shouldBe 1 // tried login once, but it failed -> no recursion
    }
}
