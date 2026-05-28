package com.android.shaftschematic.geom

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import kotlin.math.abs
import kotlin.math.max

private const val EPS_MM = 1e-3

// Tolerance for matching taper endpoints to shaft ends / thread shoulders.
// Matches END_EPS_MM used in ShaftPdfComposer so taper detection is consistent.
private const val END_EPS_MM = 0.5

data class ExcludedThreadLengths(
    val aftExcludedMm: Double,
    val fwdExcludedMm: Double,
)

private fun findAftEndThread(spec: ShaftSpec): com.android.shaftschematic.model.Threads? {
    // Coordinate-anchored selection only: an AFT end-attached thread must start at x=0 (within epsilon).
    // This avoids any list-order dependence or "first adjacent" heuristics.
    val candidates = spec.threads.asSequence().filter { th ->
        abs(th.startFromAftMm.toDouble() - 0.0) <= EPS_MM
    }

    return candidates
        .sortedWith(
            compareBy<com.android.shaftschematic.model.Threads> { it.startFromAftMm.toDouble() }
                .thenByDescending { (it.startFromAftMm + it.lengthMm).toDouble() }
        )
        .firstOrNull()
}

private fun findFwdEndThread(spec: ShaftSpec, overallLengthMm: Double): com.android.shaftschematic.model.Threads? {
    // Coordinate-anchored selection only: a FWD end-attached thread must end at x=overallLength (within epsilon).
    // This avoids any list-order dependence or "last adjacent" heuristics.
    val candidates = spec.threads.asSequence().filter { th ->
        val end = (th.startFromAftMm + th.lengthMm).toDouble()
        abs(end - overallLengthMm) <= EPS_MM
    }

    return candidates
        .sortedWith(
            compareByDescending<com.android.shaftschematic.model.Threads> { (it.startFromAftMm + it.lengthMm).toDouble() }
                .thenBy { it.startFromAftMm.toDouble() }
        )
        .firstOrNull()
}

/**
 * Computes how much length is excluded from OAL at the AFT and FWD ends.
 *
 * Contract:
 * - This is a **dimensioning-frame offset**, not a geometry change.
 * - Uses the per-end thread engagement length from the model ([Threads.lengthMm]) for the
 *   actual AFT/FWD end-attached thread components.
 * - Each excluded length is clamped to 0..overallLengthMm.
 * - If no excluded end threads exist, both are 0.
 */
fun computeExcludedThreadLengths(spec: ShaftSpec): ExcludedThreadLengths {
    val oalRaw = spec.overallLengthMm.toDouble().coerceAtLeast(0.0)
    if (oalRaw <= 0.0) return ExcludedThreadLengths(0.0, 0.0)

    val aftThread = findAftEndThread(spec)
    val fwdThread = findFwdEndThread(spec, overallLengthMm = oalRaw)

    val aft = if (aftThread?.excludeFromOAL == true) aftThread.lengthMm.toDouble().coerceIn(0.0, oalRaw) else 0.0
    val fwd = if (fwdThread?.excludeFromOAL == true) fwdThread.lengthMm.toDouble().coerceIn(0.0, oalRaw) else 0.0
    return ExcludedThreadLengths(aftExcludedMm = aft, fwdExcludedMm = fwd)
}

fun computeOalWindow(spec: ShaftSpec): OalWindow {
    val oalRaw = spec.overallLengthMm.toDouble().coerceAtLeast(0.0)
    val ex = computeExcludedThreadLengths(spec)

    val aft = ex.aftExcludedMm.coerceIn(0.0, oalRaw)
    val fwd = ex.fwdExcludedMm.coerceIn(0.0, oalRaw)
    val effective = max(0.0, oalRaw - aft - fwd)

    return OalWindow(
        measureStartMm = aft,
        measureEndMm = aft + effective,
    )
}

/**
 * Returns the AFT-end taper: a taper whose AFT face is anchored to x=0 or to the
 * shoulder of the AFT end thread (whichever applies).
 */
private fun findAftEndTaperForSET(spec: ShaftSpec): Taper? {
    val aftThread = findAftEndThread(spec)
    val anchors = buildList {
        add(0.0)
        if (aftThread != null) add((aftThread.startFromAftMm + aftThread.lengthMm).toDouble())
    }
    return spec.tapers
        .asSequence()
        .filter { tp -> tp.lengthMm > EPS_MM && anchors.any { a -> abs(tp.startFromAftMm.toDouble() - a) <= END_EPS_MM } }
        .minByOrNull { it.startFromAftMm }
}

/**
 * Returns the FWD-end taper: a taper whose FWD face is anchored to x=OAL or to the
 * shoulder of the FWD end thread (whichever applies).
 */
private fun findFwdEndTaperForSET(spec: ShaftSpec): Taper? {
    val fwdX = spec.overallLengthMm.toDouble()
    val fwdThread = findFwdEndThread(spec, overallLengthMm = fwdX)
    val anchors = buildList {
        add(fwdX)
        if (fwdThread != null) add(fwdThread.startFromAftMm.toDouble())
    }
    return spec.tapers
        .asSequence()
        .filter { tp ->
            val endX = (tp.startFromAftMm + tp.lengthMm).toDouble()
            tp.lengthMm > EPS_MM && anchors.any { a -> abs(endX - a) <= END_EPS_MM }
        }
        .maxByOrNull { it.startFromAftMm + it.lengthMm }
}

/**
 * Computes AFT/FWD SET positions in measurement space from actual taper geometry.
 *
 * Measurements on the PDF drawing always originate from the SET (small end of taper),
 * regardless of whether end threads are included or excluded from OAL. If no taper
 * exists at a given end, the measurement boundary defaults to the window edge (0 or oalMm).
 *
 * The returned positions may be negative or exceed [OalWindow.oalMm] when an excluded
 * thread shares the same physical origin as the taper (e.g. threads on the taper surface).
 */
fun computeSetPositionsInMeasureSpace(win: OalWindow, spec: ShaftSpec): SetPositions {
    val aftTaper = findAftEndTaperForSET(spec)
    val fwdTaper = findFwdEndTaperForSET(spec)

    val aftSET = if (aftTaper != null) {
        win.toMeasureX(aftTaper.startFromAftMm.toDouble())
    } else {
        0.0
    }

    val fwdSET = if (fwdTaper != null) {
        win.toMeasureX((fwdTaper.startFromAftMm + fwdTaper.lengthMm).toDouble())
    } else {
        win.oalMm
    }

    return SetPositions(aftSETxMm = aftSET, fwdSETxMm = fwdSET)
}
