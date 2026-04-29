package io.github.chudoxl.linteh.journal.core.api.avers.v4.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder

/**
 * Login response — `/login` returns `[[user_id, user_type, null, null, null, fio, id_a, null, id_pupil]]`
 * (Plan 02-02 SUMMARY).
 *
 * Field semantics:
 *  - [accountId] = first column. AVERS calls it `user_id` (Long). Used as the
 *    `:core:network` per-account scope key.
 *  - [userType] = 4 → UT_STUDENT, 3 → UT_PARENT, etc. (per `Login.js`).
 *  - [displayName] = full ФИО as a single string (sanitizer collapsed it).
 *  - [studentId] = `id_pupil` (column 8 — the last array element). For UT_STUDENT this is
 *    the pupil id used as `student=` parameter in subsequent `/act/...` calls.
 *
 * Phase 3 Auth UI consumes this DTO for the post-login welcome state and for wiring the
 * account-data store with the resolved `accountId`.
 */
@Serializable(with = LoginDtoSerializer::class)
data class LoginDto(
    val accountId: Long? = null,
    val userType: Int? = null,
    val displayName: String? = null,
    val studentId: Long? = null,
)

internal object LoginDtoSerializer : KSerializer<LoginDto> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LoginDto", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LoginDto {
        require(decoder is JsonDecoder) { "LoginDto requires Json codec" }
        val element = decoder.decodeJsonElement()
        require(element is JsonArray) { "LoginDto expects JSON array; got ${element::class.simpleName}" }
        return LoginDto(
            accountId = PositionalRowReader.longAt(element, 0),
            userType = PositionalRowReader.intAt(element, 1),
            displayName = PositionalRowReader.stringAt(element, 5),
            studentId = PositionalRowReader.longAt(element, 8),
        )
    }

    override fun serialize(encoder: Encoder, value: LoginDto) {
        error("LoginDto is read-only")
    }
}
