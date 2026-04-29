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
 * Message (SMS) DTO — `/act/get_sms` (Plan 02-02 SUMMARY).
 *
 * Plan 02-02 finding: returns `[]` for both captured accounts (no notifications
 * present). The positional row shape is therefore a forward-looking placeholder; the
 * Phase 4 mapper will refine it when a non-empty fixture is captured.
 *
 * Best-guess column ordering: `[id, date_iso, sender_name, subject, body, is_read]`.
 */
@Serializable(with = MessageDtoSerializer::class)
data class MessageDto(
    val id: Long? = null,
    val dateString: String? = null,
    val senderName: String? = null,
    val subject: String? = null,
    val body: String? = null,
    val read: Boolean = false,
)

internal object MessageDtoSerializer : KSerializer<MessageDto> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MessageDto", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): MessageDto {
        require(decoder is JsonDecoder) { "MessageDto requires Json codec" }
        val element = decoder.decodeJsonElement()
        require(element is JsonArray) { "MessageDto expects JSON array; got ${element::class.simpleName}" }
        return MessageDto(
            id = PositionalRowReader.longAt(element, 0),
            dateString = PositionalRowReader.stringAt(element, 1),
            senderName = PositionalRowReader.stringAt(element, 2),
            subject = PositionalRowReader.stringAt(element, 3),
            body = PositionalRowReader.stringAt(element, 4),
            read = PositionalRowReader.booleanAt(element, 5, default = false),
        )
    }

    override fun serialize(encoder: Encoder, value: MessageDto) {
        error("MessageDto is read-only")
    }
}
