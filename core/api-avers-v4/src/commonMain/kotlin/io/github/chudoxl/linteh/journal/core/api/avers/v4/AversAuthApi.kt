package io.github.chudoxl.linteh.journal.core.api.avers.v4

import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.LoginDto
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.LoginDtoSerializer
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.envelope.unwrapAversEnvelope
import io.github.chudoxl.linteh.journal.core.api.avers.v4.extjs.ExtJsArrayPreprocessor
import io.github.chudoxl.linteh.journal.core.api.avers.v4.result.ApiResult
import io.github.chudoxl.linteh.journal.core.api.avers.v4.result.AversApiError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * AVERS auth endpoint contract — login + logout.
 *
 * Plan 02-02 SUMMARY auth model — **client-side cookies, no server-issued sessions**:
 *  1. POST `/login` form `l=<login>&p=<sha1_hex>` → returns
 *     `[[user_id, user_type, null, null, null, "ФИО full", id_a, null, id_pupil]]`.
 *  2. POST `/auth` form `uId=<user_id>&act=1` → returns text `ok`.
 *  3. Client THEN writes three `ys-user`/`ys-password`/`ys-userId` cookies to the cookie
 *     storage (using a JS-escape() polyfill — Cyrillic codepoints emit `%uXXXX`).
 *
 * Phase 2 [login] does NOT perform the `/auth` step nor cookie writing — that's reserved
 * for Phase 4 (`:core:data` auth orchestration which composes `AversAuthApi` +
 * `AversApi` + cookie writer + `JsEscape` polyfill helper).
 *
 * Phase 2 deliverable: parse the `/login` response into [LoginDto] so downstream phases
 * have a typed surface for the user_id / studentId / displayName they need.
 *
 * D-26 wiring: HttpClientFactory (Plan 04) takes
 * `loginCall: suspend (login, password) -> Boolean`. The Phase 4 wiring will compose:
 *
 * ```
 *   val authApi = AversAuthApi(httpClient)
 *   factory = HttpClientFactory(..., loginCall = { l, p ->
 *     when (val r = authApi.login(l, p)) {
 *       is ApiResult.Success -> { authApi.completeAuth(r.value); cookieWriter.write(...); true }
 *       else -> false
 *     }
 *   })
 * ```
 *
 * Pitfall #4: The `/login` endpoint is the one PROTECTED FROM the AversAuthInterceptor
 * (Plan 04) to avoid recursion. Verified by `interceptor_skips_login_endpoint_pitfall_4`
 * test in `:core:network`.
 */
class AversAuthApi(
    private val httpClient: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    },
) {
    /**
     * POST /login with form-encoded `l=<login>&p=<password_sha1>`.
     *
     * **Caller responsibility**: pass the SHA-1 hex digest of the password as [password],
     * NOT the cleartext. The SHA-1 hashing lives in Phase 4 alongside KVault retrieval —
     * `:core:api-avers-v4` is hash-agnostic on purpose, so unit tests don't need a SHA-1
     * dependency and the credential-handling boundary stays in the data layer.
     *
     * AVERS quirks observed in HAR fixtures:
     *  - Response Content-Type is `text/plain; charset=UTF-8`, not JSON.
     *  - Bad credentials yield body `[error_symbol]` (a literal-string array) — surfaces
     *    as [AversApiError.Unauthorized] when [LoginDto.accountId] is null after decode.
     *  - HTTP status is 200 OK regardless of credential validity.
     */
    suspend fun login(loginValue: String, password: String): ApiResult<LoginDto> = runCatching {
        val response = httpClient.submitForm(
            url = AversEndpoints.LOGIN,
            formParameters = parameters {
                append("l", loginValue)
                append("p", password)
            },
        )

        if (response.status.value == HttpStatusCode.Unauthorized.value) {
            return@runCatching ApiResult.Failure(AversApiError.Unauthorized)
        }
        if (response.status.value >= 400) {
            return@runCatching ApiResult.Failure(AversApiError.Server(response.status.value))
        }

        val rawText = response.bodyAsText()
        val strictJson = ExtJsArrayPreprocessor.toStrictJson(rawText)
        val raw: JsonElement = json.parseToJsonElement(strictJson)

        when (val unwrapped = raw.unwrapAversEnvelope(endpoint = "login")) {
            is ApiResult.Success -> {
                // The login response is `[[user_id, user_type, ...]]` — a single-element
                // outer array. Unwrap one level so LoginDto's positional decoder sees the
                // inner row directly. If the outer array is empty (server-side error
                // semantics from Plan 02-02 SUMMARY), surface Unauthorized.
                val data = unwrapped.value.data
                if (data is JsonArray && data.size == 1 && data[0] is JsonArray) {
                    val row = data[0]
                    val dto = json.decodeFromJsonElement(LoginDtoSerializer, row)
                    if (dto.accountId == null) {
                        ApiResult.Failure(AversApiError.Unauthorized)
                    } else {
                        ApiResult.Success(dto)
                    }
                } else if (data is JsonArray && data.isEmpty()) {
                    // [] body == bad creds (Plan 02-02 — server returns [error_symbol]
                    // which our preprocessor leaves intact and which deserialises to a
                    // single-element string-array, not a list of arrays). Treat as unauth.
                    ApiResult.Failure(AversApiError.Unauthorized)
                } else {
                    ApiResult.Mismatch(
                        rawDump = rawText.take(2048),
                        missingFields = listOf("user_row"),
                        endpoint = "login",
                    )
                }
            }
            is ApiResult.Mismatch -> unwrapped
            is ApiResult.Failure -> unwrapped
        }
    }.getOrElse { e ->
        when (e) {
            is HttpRequestTimeoutException, is IOException ->
                ApiResult.Failure(AversApiError.Network)
            is SerializationException ->
                ApiResult.Mismatch(
                    rawDump = "(serialization-failed)",
                    missingFields = listOf(e.message ?: "?"),
                    endpoint = "login",
                )
            is ResponseException ->
                ApiResult.Failure(AversApiError.Server(e.response.status.value))
            else ->
                ApiResult.Failure(AversApiError.Network)
        }
    }

    /**
     * Best-effort logout — issues a request to `/auth/logout` so the server, even though
     * it doesn't manage sessions, gets a hint. The cookie-purge step lives in
     * `AccountDataPurger` (`:core:network`, Plan 02-05) and is the source of truth for
     * "fully logged out" semantics.
     *
     * Returns Success even on transport failure: the cookie purger runs unconditionally,
     * so logging out the network-side handshake failing should not block the user
     * experience.
     */
    suspend fun logout(): ApiResult<Unit> = runCatching {
        httpClient.get(AversEndpoints.LOGOUT)
        ApiResult.Success(Unit)
    }.getOrElse {
        ApiResult.Success(Unit)
    }
}
