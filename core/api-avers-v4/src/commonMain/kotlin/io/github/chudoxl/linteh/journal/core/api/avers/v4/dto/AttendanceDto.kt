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
 * Attendance DTO — `/act/GET_ATT_JOURNAL_DATA` (Plan 02-02 SUMMARY).
 *
 * Plan 02-02 finding: this endpoint returns `[]` for student/parent role. The dedicated
 * attendance endpoint requires `aclass-grid` (teacher-grid) selection. For
 * UT_STUDENT view, attendance marks ("Н" = absent, "Б" = sick, "ОСВ" = excused) are
 * embedded in `GradeDto.value` rows from `GET_STUDENT_JOURNAL_DATA`.
 *
 * The DTO retains a placeholder positional shape so future role expansion (parent or
 * teacher view) does not require an API surface change. Phase 4 mapper decides whether
 * to use this DTO or pivot off `GradeDto.value`.
 */
@Serializable(with = AttendanceDtoSerializer::class)
data class AttendanceDto(
    val id: Long? = null,
    val dateString: String? = null,
    val subjectId: Long? = null,
    val mark: String? = null,
)

internal object AttendanceDtoSerializer : KSerializer<AttendanceDto> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AttendanceDto", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): AttendanceDto {
        require(decoder is JsonDecoder) { "AttendanceDto requires Json codec" }
        val element = decoder.decodeJsonElement()
        require(element is JsonArray) { "AttendanceDto expects JSON array; got ${element::class.simpleName}" }
        return AttendanceDto(
            id = PositionalRowReader.longAt(element, 0),
            dateString = PositionalRowReader.stringAt(element, 1),
            subjectId = PositionalRowReader.longAt(element, 2),
            mark = PositionalRowReader.stringAt(element, 3),
        )
    }

    override fun serialize(encoder: Encoder, value: AttendanceDto) {
        error("AttendanceDto is read-only")
    }
}
