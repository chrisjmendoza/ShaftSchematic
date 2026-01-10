package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import kotlin.math.max

private enum class LinerRegion { AFT, MID, FWD }

/**
 * Builds deterministic display titles for liner component cards.
 *
 * Rules:
 * - If a liner has a non-blank custom label, that label wins.
 * - Otherwise, use a default name computed from the liner's position on the shaft.
 * - Ordering is stable: sort by startMm (AFT→FWD), tie-break by stable id.
 */
fun buildLinerTitleById(spec: ShaftSpec): Map<String, String> {
    val liners = spec.liners
    if (liners.isEmpty()) return emptyMap()

    fun customLabelOrNull(ln: Liner): String? = ln.label?.trim()?.takeIf { it.isNotEmpty() }

    val sorted = liners.sortedWith(compareBy<Liner>({ it.startFromAftMm }, { it.id }))

    val denom = max(spec.overallLengthMm, 1f)
    fun regionOf(ln: Liner): LinerRegion {
        val centerMm = ln.startFromAftMm + (ln.lengthMm / 2f)
        val ratio = centerMm / denom
        return when {
            ratio < 0.33f -> LinerRegion.AFT
            ratio <= 0.66f -> LinerRegion.MID
            else -> LinerRegion.FWD
        }
    }

    fun prefix(r: LinerRegion): String = when (r) {
        LinerRegion.AFT -> "AFT"
        LinerRegion.MID -> "MID"
        LinerRegion.FWD -> "FWD"
    }

    // Special case: if there are exactly two liners, treat them as AFT and FWD
    // (no MID naming unless there are 3+ liners).
    val regionById: Map<String, LinerRegion> = if (sorted.size == 2) {
        mapOf(
            sorted[0].id to LinerRegion.AFT,
            sorted[1].id to LinerRegion.FWD,
        )
    } else {
        sorted.associate { it.id to regionOf(it) }
    }
    val countByRegion: Map<LinerRegion, Int> = regionById.values
        .groupingBy { it }
        .eachCount()

    // Numbering within each region (deterministic: same sort as global).
    val nById: Map<String, Int> = buildMap {
        LinerRegion.entries.forEach { region ->
            var n = 0
            sorted.forEach { ln ->
                if (regionById[ln.id] == region) {
                    n += 1
                    put(ln.id, n)
                }
            }
        }
    }

    // Single-liner case: positional, no numbering.
    if (sorted.size == 1) {
        val ln = sorted.first()
        val region = regionById[ln.id] ?: regionOf(ln)
        return mapOf(ln.id to (customLabelOrNull(ln) ?: "${prefix(region)} Liner"))
    }

    return sorted.associate { ln ->
        val custom = customLabelOrNull(ln)
        val region = regionById[ln.id] ?: regionOf(ln)
        val count = countByRegion[region] ?: 0
        val defaultTitle = if (count <= 1) {
            "${prefix(region)} Liner"
        } else {
            val n = nById[ln.id] ?: 0
            "${prefix(region)} Liner #$n"
        }

        ln.id to (custom ?: defaultTitle)
    }
}
