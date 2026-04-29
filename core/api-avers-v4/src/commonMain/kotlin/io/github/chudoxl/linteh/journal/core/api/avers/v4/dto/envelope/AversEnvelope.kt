package io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.envelope

import io.github.chudoxl.linteh.journal.core.api.avers.v4.result.ApiResult
import io.github.chudoxl.linteh.journal.core.api.avers.v4.result.AversApiError
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Helper for the ExtJS `{success: true, data: <…>, msg?: string}` envelope unwrap.
 *
 * AVERS may return either:
 *  - Wrapped: `{"success": true, "data": [...]}` — typical ExtJS response from some endpoints
 *  - Wrapped error: `{"success": false, "msg": "Authorization required"}`
 *  - Direct array: `[[col0, col1, …], …]` — observed shape for `/login`, `/act/...` endpoints
 *    (Plan 02-02 SUMMARY: server emits ExtJS-style array literals, NOT object envelopes)
 *
 * Per D-09: `ignoreUnknownKeys = true` makes parsing tolerant of new fields; envelope
 * check is the strict layer (`requireNotNull(success)`).
 *
 * Returns:
 *  - [ApiResult.Success] carrying the unwrapped [JsonElement] (caller does typed decode)
 *  - [ApiResult.Mismatch] when an OBJECT envelope is present but is missing `success`/`data`
 *  - [ApiResult.Failure] wrapping `Server(200)` when server returned `success: false`
 *  - [ApiResult.Success] for direct (non-object) responses — the element is passed through
 *    verbatim, since AVERS' `/act/...` endpoints emit raw array literals.
 */
data class UnwrappedEnvelope(val data: JsonElement)

fun JsonElement.unwrapAversEnvelope(endpoint: String): ApiResult<UnwrappedEnvelope> {
    val obj: JsonObject = try {
        jsonObject
    } catch (_: IllegalArgumentException) {
        // Direct array/primitive response (no envelope) — caller treats as data directly.
        // This is the dominant case for AVERS /act/* endpoints (Plan 02-02 SUMMARY).
        return ApiResult.Success(UnwrappedEnvelope(this))
    }

    val success = obj["success"]?.jsonPrimitive?.boolean
        ?: return ApiResult.Mismatch(
            rawDump = toString(),
            missingFields = listOf("success"),
            endpoint = endpoint,
        )

    if (!success) {
        // ExtJS server error: 200 OK envelope with success=false; surface as Server(200)
        // rather than Mismatch so downstream can react (show msg in UI).
        // The msg field is optional — log it; downstream feature modules read it from
        // ApiResult.Failure if useful (Phase 4+).
        return ApiResult.Failure(AversApiError.Server(httpCode = 200))
    }

    val data = obj["data"]
        ?: return ApiResult.Mismatch(
            rawDump = toString(),
            missingFields = listOf("data"),
            endpoint = endpoint,
        )

    return ApiResult.Success(UnwrappedEnvelope(data))
}
