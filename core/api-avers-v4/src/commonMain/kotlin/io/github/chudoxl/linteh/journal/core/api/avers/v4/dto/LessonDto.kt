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
 * Lesson DTO — schedule row from `/act/GET_TIMETABLE` (Plan 02-02 SUMMARY).
 *
 * Observed row shape (positional, 11 columns):
 *   `[id, lesson_id, subject_id, ?, ?, weekday, in_day, shift, room, valid_from, valid_to]`
 *
 * `weekday` = ISO weekday (1=Mon..7=Sun) per AVERS observation. `in_day` = ordinal
 * within the day. `shift` differentiates morning/afternoon shifts when the school operates
 * two shifts. `room` is a free-form string (numeric ID or display name like "Сзн" for
 * "спортивный зал").
 *
 * Period (week vs full year) selection is server-side — single endpoint returns all
 * recurring lessons; Phase 4 client filters by date range (`Period`) when rendering.
 */
@Serializable(with = LessonDtoSerializer::class)
data class LessonDto(
    val id: Long? = null,
    val lessonId: Long? = null,
    val subjectId: Long? = null,
    val weekday: Int? = null,
    val inDay: Int? = null,
    val shift: Int? = null,
    val room: String? = null,
)

internal object LessonDtoSerializer : KSerializer<LessonDto> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LessonDto", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LessonDto {
        require(decoder is JsonDecoder) { "LessonDto requires Json codec" }
        val element = decoder.decodeJsonElement()
        require(element is JsonArray) { "LessonDto expects JSON array; got ${element::class.simpleName}" }
        return LessonDto(
            id = PositionalRowReader.longAt(element, 0),
            lessonId = PositionalRowReader.longAt(element, 1),
            subjectId = PositionalRowReader.longAt(element, 2),
            weekday = PositionalRowReader.intAt(element, 5),
            inDay = PositionalRowReader.intAt(element, 6),
            shift = PositionalRowReader.intAt(element, 7),
            room = PositionalRowReader.stringAt(element, 8),
        )
    }

    override fun serialize(encoder: Encoder, value: LessonDto) {
        error("LessonDto is read-only")
    }
}
