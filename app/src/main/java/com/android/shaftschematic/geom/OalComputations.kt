package com.android.shaftschematic.geom

import com.android.shaftschematic.model.ShaftSpec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val END_EPS_MM = 0.5

fun computeOalWindow(spec: ShaftSpec): OalWindow {
    val oalRaw = spec.overallLengthMm.toDouble()

    val aftStart = spec.threads.asSequence()
        .filter { it.excludeFromOAL }
        .map { it.startFromAftMm.toDouble() to (it.startFromAftMm + it.lengthMm).toDouble() }
        .filter { (start, _) -> abs(start - 0.0) <= END_EPS_MM }
        .map { (_, end) -> end }
        .fold(0.0) { acc, end -> max(acc, end) }

    val fwdEnd = spec.threads.asSequence()
        .filter { it.excludeFromOAL }
        .map { (it.startFromAftMm + it.lengthMm).toDouble() to it.startFromAftMm.toDouble() }
        .filter { (end, _) -> abs(end - oalRaw) <= END_EPS_MM }
        .map { (_, start) -> start }
        .fold(oalRaw) { acc, start -> min(acc, start) }

    return OalWindow(
        measureStartMm = aftStart.coerceAtLeast(0.0),
        measureEndMm   = fwdEnd.coerceIn(0.0, oalRaw)
    )
}

/**
 * Defaults: AFT SET = 0, FWD SET = OAL (in measurement space).
 * When explicit SET positions exist, remap here only.
 */
fun computeSetPositionsInMeasureSpace(win: OalWindow): SetPositions =
    SetPositions(aftSETxMm = 0.0, fwdSETxMm = win.oalMm)
