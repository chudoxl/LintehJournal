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
 * Grade DTO — best-effort shape from HAR observations of `/act/GET_STUDENT_JOURNAL_DATA`
 * (Plan 02-02 SUMMARY).
 *
 * Observed row shape (positional):
 *   `[mark_id, pupil_id, value: "5"|"4"|"Н"|"Б"|"ОСВ", date_iso, lesson_id,
 *    null, type_code, fake_int, comment_or_null]`
 *
 * `value` preserves AVERS textual marks ('5', 'н', 'зач', 'Н' for absence) — typed Int
 * would force conversion logic into Phase 2 prematurely. Phase 4 mapper decides display.
 *
 * Date arrives as ISO `"YYYY-MM-DD"` after [io.github.chudoxl.linteh.journal.core.api.avers.v4.extjs.ExtJsArrayPreprocessor]
 * substitutes the `new Date(YYYY, M_minus_1, D, …)` ExtJS literal.
 */
@Serializable(with = GradeDtoSerializer::class)
data class GradeDto(
    val markId: Long? = null,
    val pupilId: Long? = null,
    val value: String? = null,
    val dateString: String? = null,
    val lessonId: Long? = null,
    val typeCode: Int? = null,
    val comment: String? = null,
)

internal object GradeDtoSerializer : KSerializer<GradeDto> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("GradeDto", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): GradeDto {
        require(decoder is JsonDecoder) { "GradeDto requires Json codec" }
        val element = decoder.decodeJsonElement()
        require(element is JsonArray) { "GradeDto expects JSON array; got ${element::class.simpleName}" }
        return GradeDto(
            markId = PositionalRowReader.longAt(element, 0),
            pupilId = PositionalRowReader.longAt(element, 1),
            value = PositionalRowReader.stringAt(element, 2),
            dateString = PositionalRowReader.stringAt(element, 3),
            lessonId = PositionalRowReader.longAt(element, 4),
            typeCode = PositionalRowReader.intAt(element, 6),
            comment = PositionalRowReader.stringAt(element, 8),
        )
    }

    override fun serialize(encoder: Encoder, value: GradeDto) {
        error("GradeDto is read-only — no serialize path needed (network → Kotlin)")
    }
}
