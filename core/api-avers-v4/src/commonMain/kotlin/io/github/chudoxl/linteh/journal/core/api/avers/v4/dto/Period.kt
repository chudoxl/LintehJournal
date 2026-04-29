package io.github.chudoxl.linteh.journal.core.api.avers.v4.dto

import kotlinx.datetime.LocalDate

/**
 * Time period used for AVERS endpoint queries (grades, schedule, attendance).
 *
 * Phase 2: simple [LocalDate] from..to. Phase 4 may extend with named periods
 * («четверть», «триместр») if AVERS returns enumerated period IDs (observed in
 * `GET_PERIODS` HAR fixture: 360000 = "I четверть", 360001 = "II четверть", …).
 */
data class Period(val from: LocalDate, val to: LocalDate)
