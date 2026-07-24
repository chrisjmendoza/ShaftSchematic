package com.android.shaftschematic.ui.screen

/**
 * ShaftScreenController — add-defaults + snap-update helpers backing ShaftScreen.
 *
 * Non-composable helpers: `computeAddDefaults` and the snapped-update wrappers.
 * Extracted verbatim from ShaftScreen.kt 2026-07-24 — pure code move, no behavior change.
 */

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.viewmodel.SnapConfig
import com.android.shaftschematic.ui.viewmodel.snapPositionMm

/** Defaults for new components (mm). */
internal data class AddDefaults(val startMm: Float, val lastDiaMm: Float)
internal fun computeAddDefaults(spec: ShaftSpec): AddDefaults {
    // Bodies are fillers; excluded threads sit outside the shaft envelope.
    // Only sacred components (tapers, non-excluded threads, liners) drive the default start position.
    var end = 0f
    spec.tapers.forEach  { end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.threads.filter { !it.excludeFromOAL }.forEach { end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach  { end = maxOf(end, it.startFromAftMm + it.lengthMm) }

    var dia = 50f
    spec.liners.firstOrNull  { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.odMm }
    spec.threads.filter { !it.excludeFromOAL }.firstOrNull { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.majorDiaMm }
    spec.tapers.firstOrNull  { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.endDiaMm }
    if (dia == 50f && spec.bodies.isNotEmpty()) dia = spec.bodies.first().diaMm

    return AddDefaults(startMm = end, lastDiaMm = dia)
}

/* ───────────────── Snap helpers ───────────────── */

internal fun applySnappedBodyUpdate(
    onUpdate: (Int, Float, Float, Float) -> Unit,
    index: Int,
    rawStartMm: Float,
    rawEndMm: Float,
    diaMm: Float,
    anchors: List<Float>,
    config: SnapConfig = SnapConfig()
) {
    val (snappedStart, snappedEnd) = snapBounds(rawStartMm, rawEndMm, anchors, config)
    val lengthMm = (snappedEnd - snappedStart).coerceAtLeast(0f)
    onUpdate(index, snappedStart, lengthMm, diaMm)
}

internal fun applySnappedTaperUpdate(
    onUpdate: (Int, Float, Float, Float, Float, String) -> Unit,
    index: Int,
    rawStartMm: Float,
    rawEndMm: Float,
    startDiaMm: Float,
    endDiaMm: Float,
    rateText: String = "",
    anchors: List<Float>,
    config: SnapConfig = SnapConfig()
) {
    val (snappedStart, snappedEnd) = snapBounds(rawStartMm, rawEndMm, anchors, config)
    val lengthMm = (snappedEnd - snappedStart).coerceAtLeast(0f)
    onUpdate(index, snappedStart, lengthMm, startDiaMm, endDiaMm, rateText)
}

internal fun applySnappedThreadUpdate(
    onUpdate: (Int, Float, Float, Float, Float) -> Unit,
    index: Int,
    rawStartMm: Float,
    rawEndMm: Float,
    majorDiaMm: Float,
    pitchMm: Float,
    anchors: List<Float>,
    config: SnapConfig = SnapConfig()
) {
    // Only snap the start; preserve the original length. Snapping both start and end
    // independently can silently resize the thread when the raw end happens to land
    // near a snap anchor (e.g., a 99mm thread moved to start=0 could snap its end to
    // the 100mm body anchor, unexpectedly extending it to 100mm).
    val snappedStart = snapPositionMm(rawStartMm, anchors, config)
    val lengthMm = (rawEndMm - rawStartMm).coerceAtLeast(0f)
    onUpdate(index, snappedStart, lengthMm, majorDiaMm, pitchMm)
}

internal fun applySnappedLinerUpdate(
    onUpdate: (Int, Float, Float, Float) -> Unit,
    index: Int,
    rawStartMm: Float,
    rawEndMm: Float,
    odMm: Float,
    anchors: List<Float>,
    config: SnapConfig = SnapConfig()
) {
    val (snappedStart, snappedEnd) = snapBounds(rawStartMm, rawEndMm, anchors, config)
    val lengthMm = (snappedEnd - snappedStart).coerceAtLeast(0f)
    onUpdate(index, snappedStart, lengthMm, odMm)
}

private fun snapBounds(
    rawStartMm: Float,
    rawEndMm: Float,
    anchors: List<Float>,
    config: SnapConfig
): Pair<Float, Float> {
    val snappedStart = snapPositionMm(rawStartMm, anchors, config)
    val snappedEnd = snapPositionMm(rawEndMm, anchors, config)
    return snappedStart to snappedEnd
}
