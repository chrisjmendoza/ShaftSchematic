package com.android.shaftschematic.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.ThreadAttachment
import com.android.shaftschematic.model.effectiveOalEndMm
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.resolvedAttachment
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

    val denom = max(spec.effectiveOalEndMm(), 1f)

    fun endOf(th: Threads): ThreadEnd {
        if (th.excludeFromOAL) {
            return when (th.resolvedAttachment(spec.overallLengthMm)) {
                ThreadAttachment.FWD -> ThreadEnd.FWD
                else -> ThreadEnd.AFT
            }
        }
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
