package com.android.shaftschematic.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.TaperOrientation

private enum class TaperDirection { AFT, FWD }

/**
 * Deterministic display titles for tapers.
 *
 * Rules:
 * - Direction is based on explicit taper orientation (AFT/FWD).
 * - Numbering is shown only when more than one taper shares the same direction.
 * - Ordering is stable: sort by startMm (AFT→FWD), tie-break by stable id.
 */
fun buildTaperTitleById(spec: ShaftSpec): Map<String, String> {
    val tapers = spec.tapers
    if (tapers.isEmpty()) return emptyMap()

    fun directionOf(t: Taper): TaperDirection = when (t.orientation) {
        TaperOrientation.AFT -> TaperDirection.AFT
        TaperOrientation.FWD -> TaperDirection.FWD
    }

    val sorted = tapers.sortedWith(compareBy<Taper>({ it.startFromAftMm }, { it.id }))
    val directionById = sorted.associate { it.id to directionOf(it) }

    val countByDirection: Map<TaperDirection, Int> = directionById.values
        .groupingBy { it }
        .eachCount()

    val nById: Map<String, Int> = buildMap {
        TaperDirection.entries.forEach { dir ->
            var n = 0
            sorted.forEach { t ->
                if (directionById[t.id] == dir) {
                    n += 1
                    put(t.id, n)
                }
            }
        }
    }

    fun prefix(dir: TaperDirection) = when (dir) {
        TaperDirection.AFT -> "AFT"
        TaperDirection.FWD -> "FWD"
    }

    return sorted.associate { t ->
        val dir = directionById[t.id] ?: TaperDirection.AFT
        val count = countByDirection[dir] ?: 0
        val title = if (count <= 1) {
            "${prefix(dir)} Taper"
        } else {
            val n = nById[t.id] ?: 0
            "${prefix(dir)} Taper #$n"
        }
        t.id to title
    }
}
