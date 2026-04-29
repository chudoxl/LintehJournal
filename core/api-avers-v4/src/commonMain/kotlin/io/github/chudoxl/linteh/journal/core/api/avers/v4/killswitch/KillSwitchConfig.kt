package io.github.chudoxl.linteh.journal.core.api.avers.v4.killswitch

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
 * Custom KSerializer (Plan 02-06 DTO pattern) — `:core:api-avers-v4` does NOT apply the
 * kotlinx-serialization compiler plugin (only the runtime). Adding the plugin is Plan 02-09's
 * domain (it owns `core/api-avers-v4/build.gradle.kts`); for now KillSwitchConfig follows the
 * same custom-serializer convention as every DTO in this module. Unknown JSON keys (e.g., the
 * `_comment` field that ships with the static config or a future `targetMilestone`) are
 * silently skipped — backward-compatibility-by-default.
 */
@Serializable(with = KillSwitchConfigSerializer::class)
data class KillSwitchConfig(
    val latestSupportedAversBuild: String,
    val message: String = "",
    val severity: Severity = Severity.info,
    val minAppVersion: String = "0.0.0",
)

/**
 * Severity gate. Wire-format is the lowercase enum-entry name. Unknown values fall back to
 * [info] (forward-compatible if future versions introduce e.g. `silent` or `nudge`).
 */
enum class Severity {
    /** No banner; app proceeds normally. */
    info,

    /** Non-blocking banner (Phase 4+ UI). App proceeds. */
    warning,

    /** Block AVERS fetches when AND ONLY WHEN latestSupportedAversBuild != current. */
    block;

    companion object {
        fun fromWire(value: String?): Severity = when (value?.lowercase()) {
            "warning" -> warning
            "block" -> block
            else -> info  // null, "info", or any unrecognised value
        }
    }
}

internal object KillSwitchConfigSerializer : KSerializer<KillSwitchConfig> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("KillSwitchConfig", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): KillSwitchConfig {
        require(decoder is JsonDecoder) { "KillSwitchConfig requires Json codec" }
        val element = decoder.decodeJsonElement()
        require(element is JsonObject) {
            "KillSwitchConfig expects JSON object; got ${element::class.simpleName}"
        }
        val latestBuild = (element["latestSupportedAversBuild"] as? JsonPrimitive)?.contentOrNull
            ?: error("KillSwitchConfig missing required field 'latestSupportedAversBuild'")
        val message = (element["message"] as? JsonPrimitive)?.contentOrNull ?: ""
        val severity = Severity.fromWire((element["severity"] as? JsonPrimitive)?.contentOrNull)
        val minAppVersion = (element["minAppVersion"] as? JsonPrimitive)?.contentOrNull ?: "0.0.0"
        return KillSwitchConfig(
            latestSupportedAversBuild = latestBuild,
            message = message,
            severity = severity,
            minAppVersion = minAppVersion,
        )
    }

    override fun serialize(encoder: Encoder, value: KillSwitchConfig) {
        error("KillSwitchConfig is read-only")
    }
}
