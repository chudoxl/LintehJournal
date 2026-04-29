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
 * Homework DTO — DairyRow from `/act/GET_STUDENT_DAIRY` (Plan 02-02 SUMMARY).
 *
 * Observed row shape (positional, 12 columns):
 *   `[date_iso, lesson_id, subject_id, theme, homework_text, mark_or_null, in_day,
 *    null, null, teacher_id, in_parallel, comment_or_null]`
 *
 * `homework_text` is free-form Russian text (often empty when teacher hasn't filled).
 * `theme` is the lesson topic for the day. `subject_id` cross-references the subject
 * dictionary returned by `/act/GET_DAIRY_CLASS_SUBJECTS` (bootstrap call — Phase 4 stores
 * the dictionary alongside DTOs in `:core:database`).
 *
 * Phase 2 placeholder field `attachments` is empty — file URLs are not in the row, the
 * dairy endpoint surfaces them via a separate dictionary call (Phase 5 extension).
 */
@Serializable(with = HomeworkDtoSerializer::class)
data class HomeworkDto(
    val dateString: String? = null,
    val lessonId: Long? = null,
    val subjectId: Long? = null,
    val theme: String? = null,
    val homeworkText: String? = null,
    val mark: String? = null,
    val inDay: Int? = null,
    val teacherId: Long? = null,
    val comment: String? = null,
)

internal object HomeworkDtoSerializer : KSerializer<HomeworkDto> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("HomeworkDto", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): HomeworkDto {
        require(decoder is JsonDecoder) { "HomeworkDto requires Json codec" }
        val element = decoder.decodeJsonElement()
        require(element is JsonArray) { "HomeworkDto expects JSON array; got ${element::class.simpleName}" }
        return HomeworkDto(
            dateString = PositionalRowReader.stringAt(element, 0),
            lessonId = PositionalRowReader.longAt(element, 1),
            subjectId = PositionalRowReader.longAt(element, 2),
            theme = PositionalRowReader.stringAt(element, 3),
            homeworkText = PositionalRowReader.stringAt(element, 4),
            mark = PositionalRowReader.stringAt(element, 5),
            inDay = PositionalRowReader.intAt(element, 6),
            teacherId = PositionalRowReader.longAt(element, 9),
            comment = PositionalRowReader.stringAt(element, 11),
        )
    }

    override fun serialize(encoder: Encoder, value: HomeworkDto) {
        error("HomeworkDto is read-only")
    }
}
