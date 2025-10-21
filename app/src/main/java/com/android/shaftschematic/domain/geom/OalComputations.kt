package com.android.shaftschematic.domain.geom

import com.android.shaftschematic.model.ShaftSpec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val END_EPS_MM = 0.5 // tolerance to consider a thread "at" an end

/**
 * Computes the measurement window [start,end] while skipping end-threads excluded from OAL.
 * Rule:
 * - Excluded AFT thread(s) at aft end → start = farthest such thread's end.
 * - Excluded FWD thread(s) at fwd end → end   = nearest such thread's start.
 * - Otherwise: [0, overallLength].
 */
fun computeOalWindow(spec: ShaftSpec): OalWindow {
    val rawOal = spec.overallLengthMm.toDouble()

    val aftStart = spec.threads.asSequence()
        .filter { it.excludeFromOAL }
        .map { it.startFromAftMm.toDouble() to (it.startFromAftMm + it.lengthMm).toDouble() } // (start,end)
        .filter { (start, _) -> abs(start - 0.0) <= END_EPS_MM }
        .map { (_, end) -> end }
        .fold(0.0) { acc, end -> max(acc, end) }

    val fwdEnd = spec.threads.asSequence()
        .filter { it.excludeFromOAL }
        .map { (it.startFromAftMm + it.lengthMm).toDouble() to it.startFromAftMm.toDouble() } // (end,start)
        .filter { (end, _) -> abs(end - rawOal) <= END_EPS_MM }
        .map { (_, start) -> start }
        .fold(rawOal) { acc, start -> min(acc, start) }

    return OalWindow(
        measureStartMm = aftStart.coerceAtLeast(0.0),
        measureEndMm   = fwdEnd.coerceIn(0.0, rawOal)
    )
}

/**
 * Default SET mapping in measurement-space (can be made taper-aware later with no other changes).
 */
fun computeSetPositionsInMeasureSpace(win: OalWindow): SetPositions =
    SetPositions(aftSETxMm = 0.0, fwdSETxMm = win.oalMm)
