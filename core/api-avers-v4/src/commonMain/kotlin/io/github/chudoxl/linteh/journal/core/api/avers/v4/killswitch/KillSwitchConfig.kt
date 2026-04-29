package io.github.chudoxl.linteh.journal.core.api.avers.v4.killswitch

import kotlinx.serialization.Serializable

/**
 * D-12 schema for kill-switch JSON hosted at
 * `https://chudoxl.github.io/LintehJournal/api-config.json`.
 *
 * Deployed via the Phase 1 `pages.yml` GitHub Pages workflow whenever the `docs/` tree changes.
 *
 * @property latestSupportedAversBuild Build number of AVERS that the current app version
 *   supports. If actual AVERS build differs from this, soft warning ([Severity.warning]) or
 *   hard block ([Severity.block]) may apply (block requires the build mismatch — see
 *   [KillSwitchClient] hardening for T-02-38).
 * @property message Optional UI message to surface (Phase 4+ banner).
 * @property severity One of [Severity] — `info` is no-op, `warning` shows non-blocking
 *   banner, `block` halts data fetches (with hardening guard — see [KillSwitchClient]).
 * @property minAppVersion Optional gate: app versions below this also trigger kill-switch
 *   (Phase 6 forced-update flow; Phase 2 just persists the value).
 *
 * `ignoreUnknownKeys = true` in the parser (Plan 06 default Json) handles backward-compat
 * extensions: e.g., the `_comment` field that ships with the static config or a future
 * `targetMilestone` — won't break parse.
 */
@Serializable
data class KillSwitchConfig(
    val latestSupportedAversBuild: String,
    val message: String = "",
    val severity: Severity = Severity.info,
    val minAppVersion: String = "0.0.0",
)

@Serializable
@Suppress("EnumEntryName")
enum class Severity {
    /** No banner; app proceeds normally. */
    info,

    /** Non-blocking banner (Phase 4+ UI). App proceeds. */
    warning,

    /** Block AVERS fetches when AND ONLY WHEN latestSupportedAversBuild != current. */
    block,
}
