package io.github.chudoxl.linteh.journal.core.api.avers.v4

import io.github.chudoxl.linteh.journal.core.api.avers.v4.antibot.AntiBotDetector
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.AttendanceDto
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.AttendanceDtoSerializer
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.GradeDto
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.GradeDtoSerializer
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.HomeworkDto
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.HomeworkDtoSerializer
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.LessonDto
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.LessonDtoSerializer
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.MessageDto
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.MessageDtoSerializer
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.Period
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.StudentScope
import io.github.chudoxl.linteh.journal.core.api.avers.v4.dto.envelope.unwrapAversEnvelope
import io.github.chudoxl.linteh.journal.core.api.avers.v4.extjs.ExtJsArrayPreprocessor
import io.github.chudoxl.linteh.journal.core.api.avers.v4.result.ApiResult
import io.github.chudoxl.linteh.journal.core.api.avers.v4.result.AversApiError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * AVERS endpoint paths — verified against Plan 02-02 SUMMARY HAR captures.
 *
 * URL pattern: `<base>/act/<ACTION_NAME>` for all data endpoints; auth uses bare `/login`,
 * `/auth`, `/logout`. `<base>` is `document.location.pathname` — for the production
 * deployment that's just `/`, so all paths are absolute.
 */
internal object AversEndpoints {
    /** POST /login — form `l=<login>&p=<sha1_hex>` returns `[[user_id, user_type, …]]`. */
    const val LOGIN = "/login"

    /** POST /auth — form `uId=<id>&act=1` returns text `ok` (string literal, NOT JSON). */
    const val AUTH = "/auth"

    /** Logout — observed in Login.js `Broker.logout()`; path `/auth/logout`. */
    const val LOGOUT = "/auth/logout"

    /** POST /act/GET_STUDENT_JOURNAL_DATA — form `cls=<id>&student=<id>`. */
    const val GRADES = "/act/GET_STUDENT_JOURNAL_DATA"

    /** GET /act/GET_TIMETABLE — no parameters (cookie-driven scope). */
    const val SCHEDULE = "/act/GET_TIMETABLE"

    /** POST /act/GET_STUDENT_DAIRY — form `student, cls, begin_dt (DD.MM.YYYY), end_dt`. */
    const val HOMEWORK = "/act/GET_STUDENT_DAIRY"

    /** POST /act/GET_ATT_JOURNAL_DATA — form `cls, period_begin, period_end`. */
    const val ATTENDANCE = "/act/GET_ATT_JOURNAL_DATA"

    /** POST /act/get_sms — form `uchYear=<year>`. */
    const val MESSAGES = "/act/get_sms"
}

/**
 * AVERS API endpoint contract. Suspend functions return [ApiResult] — never throw.
 *
 * D-09 strict-but-tolerant parsing pipeline:
 *  1. AntiBotDetector via Content-Type sniff (catches CAPTCHA HTML).
 *  2. [ExtJsArrayPreprocessor] substitutes `new Date(Y, M-1, D, …)` literals.
 *  3. ExtJS envelope unwrap via [unwrapAversEnvelope] (raw arrays pass through).
 *  4. kotlinx.serialization decode via positional KSerializers (Plan 02-02 row shapes).
 *
 * Exception → ApiResult mapping table (mirrors AversApiError KDoc):
 *  | Throwable                                   | ApiResult                               |
 *  | ------------------------------------------- | --------------------------------------- |
 *  | HttpRequestTimeoutException, IOException    | Failure(Network)                        |
 *  | SerializationException                      | Mismatch(rawDump, [errorMsg], endpoint) |
 *  | ResponseException 401                       | Failure(Unauthorized)                   |
 *  | ResponseException other                     | Failure(Server(httpCode))               |
 *  | text/html Content-Type                      | Failure(AntiBotChallenge(rawHtml))      |
 *
 * D-25: integration tests run via Ktor MockEngine + HAR fixtures (Plan 02-06
 * EndpointsContractTest). No live AVERS calls in CI.
 *
 * @param scope per-account/per-student call scope (classId, studentId, academicYear).
 *   Phase 2 callers populate manually; Phase 4 uses the bootstrap chain.
 */
class AversApi(
    private val httpClient: HttpClient,
    private val scope: StudentScope = StudentScope(classId = 0, studentId = 0, academicYear = 0),
    private val antiBotDetector: AntiBotDetector = AntiBotDetector(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    },
) {
    /** POST /act/GET_STUDENT_JOURNAL_DATA — student journal grades + attendance marks. */
    suspend fun fetchGrades(period: Period): ApiResult<List<GradeDto>> =
        executeListEndpoint(
            url = AversEndpoints.GRADES,
            params = mapOf(
                "cls" to scope.classId.toString(),
                "student" to scope.studentId.toString(),
            ),
            method = HttpMethod.POST,
            endpointName = "grades",
            serializer = ListSerializer(GradeDtoSerializer),
        )

    /** GET /act/GET_TIMETABLE — full school week timetable; no params. */
    suspend fun fetchSchedule(period: Period): ApiResult<List<LessonDto>> =
        executeListEndpoint(
            url = AversEndpoints.SCHEDULE,
            params = emptyMap(),
            method = HttpMethod.GET,
            endpointName = "schedule",
            serializer = ListSerializer(LessonDtoSerializer),
        )

    /** POST /act/GET_STUDENT_DAIRY — homework + lesson themes for a date range. */
    suspend fun fetchHomework(period: Period): ApiResult<List<HomeworkDto>> =
        executeListEndpoint(
            url = AversEndpoints.HOMEWORK,
            params = mapOf(
                "student" to scope.studentId.toString(),
                "cls" to scope.classId.toString(),
                "begin_dt" to formatDmy(period.from),
                "end_dt" to formatDmy(period.to),
            ),
            method = HttpMethod.POST,
            endpointName = "homework",
            serializer = ListSerializer(HomeworkDtoSerializer),
        )

    /** POST /act/GET_ATT_JOURNAL_DATA — attendance grid (returns [] for student role). */
    suspend fun fetchAttendance(period: Period): ApiResult<List<AttendanceDto>> =
        executeListEndpoint(
            url = AversEndpoints.ATTENDANCE,
            params = mapOf(
                "cls" to scope.classId.toString(),
                "period_begin" to formatDmy(period.from),
                "period_end" to formatDmy(period.to),
            ),
            method = HttpMethod.POST,
            endpointName = "attendance",
            serializer = ListSerializer(AttendanceDtoSerializer),
        )

    /** POST /act/get_sms — SMS notifications for the academic year. */
    suspend fun fetchMessages(): ApiResult<List<MessageDto>> =
        executeListEndpoint(
            url = AversEndpoints.MESSAGES,
            params = mapOf("uchYear" to scope.academicYear.toString()),
            method = HttpMethod.POST,
            endpointName = "messages",
            serializer = ListSerializer(MessageDtoSerializer),
        )

    private enum class HttpMethod { GET, POST }

    private suspend fun <T> executeListEndpoint(
        url: String,
        params: Map<String, String>,
        method: HttpMethod,
        endpointName: String,
        serializer: KSerializer<List<T>>,
    ): ApiResult<List<T>> = runCatching {
        val response: HttpResponse = when (method) {
            HttpMethod.GET ->
                httpClient.get(url) {
                    params.forEach { (k, v) -> this.url.parameters.append(k, v) }
                }
            HttpMethod.POST ->
                httpClient.submitForm(
                    url = url,
                    formParameters = parameters {
                        params.forEach { (k, v) -> append(k, v) }
                    },
                )
        }

        if (response.status.value == HttpStatusCode.Unauthorized.value) {
            return@runCatching ApiResult.Failure(AversApiError.Unauthorized)
        }
        if (response.status.value >= 400) {
            return@runCatching ApiResult.Failure(AversApiError.Server(response.status.value))
        }

        // Anti-bot check via Content-Type. AVERS legitimate endpoints use text/plain.
        val ct = response.contentType()?.toString() ?: ""
        if (ct.startsWith("text/html", ignoreCase = true)) {
            val html = response.bodyAsText()
            return@runCatching ApiResult.Failure(AversApiError.AntiBotChallenge(rawHtml = html))
        }

        val rawText = response.bodyAsText()
        // Plan 02-02 SUMMARY: server emits ExtJS, not strict JSON. Strip new Date(...) before parse.
        val strictJson = ExtJsArrayPreprocessor.toStrictJson(rawText)

        val raw: JsonElement = try {
            json.parseToJsonElement(strictJson)
        } catch (e: SerializationException) {
            // Body was not parseable even after preprocessing — could be HTML masquerading.
            if (antiBotDetector.isCaptchaBody(rawText)) {
                return@runCatching ApiResult.Failure(
                    AversApiError.AntiBotChallenge(rawHtml = rawText),
                )
            }
            throw e
        }

        // Defense-in-depth: if a parsed primitive turns out to be HTML, surface as anti-bot.
        if (antiBotDetector.isCaptcha(raw)) {
            return@runCatching ApiResult.Failure(
                AversApiError.AntiBotChallenge(rawHtml = rawText),
            )
        }

        when (val unwrapped = raw.unwrapAversEnvelope(endpointName)) {
            is ApiResult.Success -> {
                val list: List<T> = json.decodeFromJsonElement(serializer, unwrapped.value.data)
                ApiResult.Success(list)
            }
            is ApiResult.Mismatch -> unwrapped
            is ApiResult.Failure -> unwrapped
        }
    }.getOrElse { e -> mapException(e, endpointName) }

    private fun <T> mapException(e: Throwable, endpoint: String): ApiResult<T> = when (e) {
        is HttpRequestTimeoutException, is IOException ->
            ApiResult.Failure(AversApiError.Network)
        is SerializationException ->
            ApiResult.Mismatch(
                rawDump = "(serialization-failed)",
                missingFields = listOf(e.message ?: "unknown"),
                endpoint = endpoint,
            )
        is ResponseException -> {
            val code = e.response.status.value
            if (code == HttpStatusCode.Unauthorized.value) {
                ApiResult.Failure(AversApiError.Unauthorized)
            } else {
                ApiResult.Failure(AversApiError.Server(code))
            }
        }
        else -> ApiResult.Failure(AversApiError.Network)
    }

    /** AVERS expects `DD.MM.YYYY` in form parameters (Plan 02-02 SUMMARY). */
    private fun formatDmy(date: kotlinx.datetime.LocalDate): String {
        val d = if (date.dayOfMonth < 10) "0${date.dayOfMonth}" else date.dayOfMonth.toString()
        val m = if (date.monthNumber < 10) "0${date.monthNumber}" else date.monthNumber.toString()
        return "$d.$m.${date.year}"
    }
}
