package io.github.chudoxl.linteh.journal.core.api.avers.v4.result

/**
 * Sealed result for every AVERS API call. Endpoints NEVER throw — they return one of:
 *  - [Success] with the parsed value
 *  - [Mismatch] when the response is well-formed JSON but missing critical fields
 *    (used for D-09 strict-but-tolerant parsing; surfaces "обновите приложение" banner)
 *  - [Failure] wrapping a typed [AversApiError]
 */
sealed class ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>()

    data class Mismatch(
        val rawDump: String,
        val missingFields: List<String>,
        val endpoint: String,
    ) : ApiResult<Nothing>()

    data class Failure(val error: AversApiError) : ApiResult<Nothing>()
}
