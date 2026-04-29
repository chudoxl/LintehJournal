package io.github.chudoxl.linteh.journal.core.api.avers.v4.fixtures

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Loads a sanitized HAR file (relative to the project's `fixtures/sanitized/` directory)
 * and returns a [MockEngine] that replays HTTP entries by matching the path + method of
 * each outgoing request to the most appropriate HAR entry.
 *
 * Phase 2: Android JVM (Robolectric) only — system property `fixtures.dir` set by Gradle
 * test config (see `core/api-avers-v4/build.gradle.kts`). iOS Native: requires resource
 * bundling; tests using this helper are `@Ignore`d on iosTest until Plan 02-09 reworks
 * resource loading (Plan 02-06 SUMMARY records this caveat explicitly).
 *
 * Matching strategy:
 *  - Method must equal (HAR `request.method` → request.method.value).
 *  - Path of request URL must equal path of HAR `request.url`. Because the bare HttpClient
 *    produced by [io.ktor.client.HttpClient]`(harMockEngine(...))` has no `DefaultRequest`
 *    plugin, the outgoing URL is `http://localhost/<path>`; the HAR records the production
 *    URL `https://journal.school28-kirov.ru/<path>`. Path comparison is the lowest common
 *    denominator that does not require us to pin a base URL.
 *  - When multiple entries match (e.g. login.har has `/login` AND `/auth`), the first
 *    method+path match wins; this matches HAR temporal ordering.
 *  - Misses produce a deterministic error listing all available entries — easy debugging
 *    when DTO endpoint constants don't match observed URLs.
 */
expect fun loadHarFile(relativePath: String): String

fun harMockEngine(harResourcePath: String): MockEngine {
    val har = loadHarFile(harResourcePath)
    val log = Json.parseToJsonElement(har).jsonObject["log"]!!.jsonObject
    val entries = log["entries"]!!.jsonArray.map { it.jsonObject }

    return MockEngine { request ->
        val match = entries.firstOrNull { entry -> matches(entry, request) }
            ?: error(
                "HAR fixture has no entry matching ${request.method.value} " +
                    "${request.url} (path=${request.url.encodedPath}). " +
                    "Available entries: ${entries.joinToString(", ") { describeEntry(it) }}",
            )

        val resp = match["response"]!!.jsonObject
        val content = resp["content"]?.jsonObject
        val text = content?.get("text")?.jsonPrimitive?.content ?: ""
        val mimeType = content?.get("mimeType")?.jsonPrimitive?.content ?: "application/json"
        val status = resp["status"]!!.jsonPrimitive.int

        respond(
            content = ByteReadChannel(text),
            status = HttpStatusCode.fromValue(status),
            headers = headersOf("Content-Type", mimeType),
        )
    }
}

private fun matches(entry: JsonObject, request: HttpRequestData): Boolean {
    val req = entry["request"]!!.jsonObject
    val harMethod = req["method"]!!.jsonPrimitive.content
    if (!harMethod.equals(request.method.value, ignoreCase = true)) return false

    val harUrl = req["url"]!!.jsonPrimitive.content
    val harPath = pathOf(harUrl)
    val requestPath = request.url.encodedPath
    return harPath.equals(requestPath, ignoreCase = true)
}

/** Returns the path component of an HAR-recorded URL (drops scheme + host + query). */
private fun pathOf(harUrl: String): String {
    // Strip scheme (e.g. `https://`)
    val afterScheme = harUrl.substringAfter("://", harUrl)
    // Drop host portion: keep everything from the first `/` onwards (or `/` if no path).
    val slashIdx = afterScheme.indexOf('/')
    val pathWithQuery = if (slashIdx >= 0) afterScheme.substring(slashIdx) else "/"
    // Drop query/fragment
    return pathWithQuery.substringBefore('?').substringBefore('#')
}

private fun describeEntry(entry: JsonObject): String {
    val req = entry["request"]!!.jsonObject
    val method = req["method"]!!.jsonPrimitive.content
    val url = req["url"]!!.jsonPrimitive.content
    return "$method ${pathOf(url)}"
}
