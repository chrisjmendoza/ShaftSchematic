package com.android.shaftschematic.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import kotlin.math.max

private enum class ThreadEnd { AFT, FWD }

/**
 * Deterministic display titles for threads.
 *
 * Rules:
 * - Threads are grouped into AFT vs FWD by physical position along the shaft.
 * - Numbering is shown only when more than one thread shares the same end.
 * - Ordering is stable: sort by startMm (AFT→FWD), tie-break by stable id.
 */
fun buildThreadTitleById(spec: ShaftSpec): Map<String, String> {
    val threads = spec.threads
    if (threads.isEmpty()) return emptyMap()

    val sorted = threads.sortedWith(compareBy<Threads>({ it.startFromAftMm }, { it.id }))

    fun coverageEndMm(): Float {
        var end = 0f
        spec.bodies.forEach { end = max(end, it.startFromAftMm + it.lengthMm) }
        spec.tapers.forEach { end = max(end, it.startFromAftMm + it.lengthMm) }
        spec.threads.forEach { end = max(end, it.startFromAftMm + it.lengthMm) }
        spec.liners.forEach { end = max(end, it.startFromAftMm + it.lengthMm) }
        return end
    }

    val denom = max(coverageEndMm(), 1f)

    fun endOf(th: Threads): ThreadEnd {
        val centerMm = th.startFromAftMm + (th.lengthMm / 2f)
        return if (centerMm / denom < 0.5f) ThreadEnd.AFT else ThreadEnd.FWD
    }

    val endById = sorted.associate { it.id to endOf(it) }
    val countByEnd: Map<ThreadEnd, Int> = endById.values.groupingBy { it }.eachCount()

    val nById: Map<String, Int> = buildMap {
        ThreadEnd.entries.forEach { end ->
            var n = 0
            sorted.forEach { th ->
                if (endById[th.id] == end) {
                    n += 1
                    put(th.id, n)
                }
            }
        }
    }

    fun prefix(end: ThreadEnd) = when (end) {
        ThreadEnd.AFT -> "AFT"
        ThreadEnd.FWD -> "FWD"
    }

    return sorted.associate { th ->
        val end = endById[th.id] ?: ThreadEnd.AFT
        val count = countByEnd[end] ?: 0
        val title = if (count <= 1) {
            "${prefix(end)} Thread"
        } else {
            val n = nById[th.id] ?: 0
            "${prefix(end)} Thread #$n"
        }
        th.id to title
    }
}
