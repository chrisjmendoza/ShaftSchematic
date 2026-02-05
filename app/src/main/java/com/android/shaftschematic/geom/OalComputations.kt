package com.android.shaftschematic.geom

import com.android.shaftschematic.model.ShaftSpec
import kotlin.math.abs
import kotlin.math.max

private const val EPS_MM = 1e-3

data class ExcludedThreadLengths(
    val aftExcludedMm: Double,
    val fwdExcludedMm: Double,
)

data class MeasurementDatums(
    val measurementAftMm: Double,
    val measurementForwardMm: Double,
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
 * Measurement datums used for authored reference resolution.
 * - measurementAftMm: measurement-space AFT datum (can shift when AFT threads are excluded)
 * - measurementForwardMm: measurement-space FWD datum (MFD)
 */
fun computeMeasurementDatums(spec: ShaftSpec): MeasurementDatums {
    val win = computeOalWindow(spec)
    return MeasurementDatums(
        measurementAftMm = win.measureStartMm,
        measurementForwardMm = win.measureEndMm
    )
}

/**
 * Defaults: AFT SET = 0, FWD SET = OAL (in measurement space).
 * When explicit SET positions exist, remap here only.
 */
fun computeSetPositionsInMeasureSpace(win: OalWindow): SetPositions =
    SetPositions(aftSETxMm = 0.0, fwdSETxMm = win.oalMm)
