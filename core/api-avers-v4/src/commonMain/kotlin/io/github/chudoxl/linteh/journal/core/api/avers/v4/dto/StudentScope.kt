package io.github.chudoxl.linteh.journal.core.api.avers.v4.dto

/**
 * Per-call scope for AVERS data endpoints.
 *
 * Phase 2 surfaces this as a plain data class — the bootstrap chain
 * (`get_user_data` → `get_uch_year` → `GET_STUDENT_CLASS` → `GET_STUDENT_PARALLEL`) lives
 * in Phase 4's `:core:data` mapper layer, which will populate this value class once and
 * reuse it across endpoint calls.
 *
 * Plan 02-02 SUMMARY field semantics:
 *  - [classId] — `cls` parameter (e.g. 1013), comes from `GET_STUDENT_CLASS[0][0]`.
 *  - [studentId] — `student` parameter (e.g. 4028), comes from `LoginDto.studentId`.
 *  - [academicYear] — `uchYear` parameter (e.g. 2025), comes from `get_uch_year[0][0]`.
 */
data class StudentScope(
    val classId: Long,
    val studentId: Long,
    val academicYear: Int,
)
