package io.github.chudoxl.linteh.journal.core.api.avers.v4.dto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Helpers for safely reading positional fields out of AVERS' ExtJS row arrays (Plan 02-02
 * SUMMARY: every `/act/...` endpoint emits arrays of arrays; columns are positional, not
 * keyed). All accessors are out-of-bounds-safe and null-tolerant — D-09 strict-but-tolerant.
 */
internal object PositionalRowReader {

    fun stringAt(row: JsonArray, index: Int): String? {
        if (index < 0 || index >= row.size) return null
        val element = row[index]
        if (element === JsonNull) return null
        if (element !is JsonPrimitive) return null
        // ExtJS sometimes pads ids with trailing whitespace ("4     "); trim defensively.
        // Empty strings stay empty (caller decides null-vs-empty semantics).
        return if (element.isString) element.content.trim() else element.content
    }

    fun intAt(row: JsonArray, index: Int): Int? {
        if (index < 0 || index >= row.size) return null
        val element = row[index]
        if (element === JsonNull) return null
        if (element !is JsonPrimitive) return null
        return element.intOrNull
    }

    fun longAt(row: JsonArray, index: Int): Long? {
        if (index < 0 || index >= row.size) return null
        val element = row[index]
        if (element === JsonNull) return null
        if (element !is JsonPrimitive) return null
        return element.longOrNull
    }

    fun booleanAt(row: JsonArray, index: Int, default: Boolean = false): Boolean {
        if (index < 0 || index >= row.size) return default
        val element = row[index]
        if (element === JsonNull) return default
        if (element !is JsonPrimitive) return default
        return element.booleanOrNull ?: default
    }
}
