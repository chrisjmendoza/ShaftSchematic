package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.Segment
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.endFromAftMm
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs

/**
 * Configuration for snapping in **millimeters**.
 *
 * @property toleranceMm Maximum distance in mm between a raw position and the nearest anchor
 *                       for snapping to occur. If the nearest anchor is farther than this,
 *                       snapping is skipped and the raw position is returned unchanged.
 */
data class SnapConfig(
    val toleranceMm: Float = 1.0f
)

/**
 * Builds a sorted list of unique snap anchors (in **mm**) for the given [ShaftSpec].
 *
 * Anchors include:
 *  • 0 mm (aft face)
 *  • overallLengthMm (forward face)
 *  • start and end of every component (bodies, tapers, threads, liners)
 *
 * Any anchors outside [0, overallLengthMm] are discarded.
 */
fun buildSnapAnchors(spec: ShaftSpec): List<Float> {
    val anchors = mutableSetOf<Float>()

    // Always include the aft face and forward face
    anchors += 0f
    anchors += spec.overallLengthMm

    fun MutableSet<Float>.addSegmentEnds(segments: List<out Segment>) {
        segments.forEach { seg ->
            add(seg.startFromAftMm)
            add(seg.endFromAftMm)
        }
    }

    anchors.addSegmentEnds(spec.bodies)
    anchors.addSegmentEnds(spec.tapers)
    anchors.addSegmentEnds(spec.threads)
    anchors.addSegmentEnds(spec.liners)

    // Keep only anchors within [0, overallLength] and sort
    return anchors
        .filter { it >= 0f && it <= spec.overallLengthMm }
        .sorted()
}

/**
 * Snaps [rawMm] to the nearest anchor (in mm) if it lies within [config.toleranceMm].
 *
 * Behavior:
 *  • If [anchors] is empty → returns [rawMm].
 *  • Finds the nearest anchor by absolute distance.
 *  • If distance ≤ toleranceMm → returns that anchor.
 *  • Otherwise → returns [rawMm] unchanged.
 */
fun snapPositionMm(
    rawMm: Float,
    anchors: List<Float>,
    config: SnapConfig = SnapConfig()
): Float {
    if (anchors.isEmpty()) return rawMm

    val nearest = anchors.minByOrNull { anchor -> abs(anchor - rawMm) } ?: return rawMm
    return if (abs(nearest - rawMm) <= config.toleranceMm) nearest else rawMm
}

private const val METRIC_SNAP_TOL_MM = 1.0f      // 1 mm
private const val IMPERIAL_SNAP_TOL_IN = 0.04f   // ~0.04 in ≈ 1.016 mm
private const val INCH_TO_MM = 25.4f

/**
 * Snap tolerance in mm for the active display unit — a hair tighter in metric than imperial,
 * so the snap radius feels the same size on screen in either unit.
 */
fun snapToleranceMm(unit: UnitSystem): Float = when (unit) {
    UnitSystem.MILLIMETERS -> METRIC_SNAP_TOL_MM
    UnitSystem.INCHES      -> IMPERIAL_SNAP_TOL_IN * INCH_TO_MM
}

/**
 * Snap [rawMm] against anchors built from [spec], using a tolerance appropriate to [unit].
 *
 * This is the whole tap-to-add snap decision in one pure call. Pure counterpart of
 * `ShaftViewModel.snapRawPositionMm`, which delegates here.
 */
fun snapRawPositionMm(rawMm: Float, spec: ShaftSpec, unit: UnitSystem): Float =
    snapPositionMm(rawMm, buildSnapAnchors(spec), SnapConfig(toleranceMm = snapToleranceMm(unit)))

/**
 * Minimum separation before an anchor counts as "ahead of" a position. Without it, an anchor
 * sitting exactly on [positionMm] (the common case — the tap already snapped to it) would be
 * picked as its own next anchor and yield a zero-length gap.
 */
const val NEXT_ANCHOR_EPSILON_MM = 0.1f

/** Default prefill length when nothing lies ahead of the tap. */
const val DEFAULT_ADD_GAP_MM = 50f

/**
 * Distance from [positionMm] forward to the next snap anchor, floored at [minimumMm].
 *
 * Used to prefill the length field in tap-to-add dialogs so a component dropped into a gap
 * defaults to filling it. Falls back to the forward face when no anchor lies ahead.
 *
 * Pure counterpart of `ShaftViewModel.gapToNextAnchorMm`, which delegates here.
 */
fun gapToNextAnchorMm(
    spec: ShaftSpec,
    positionMm: Float,
    minimumMm: Float = DEFAULT_ADD_GAP_MM
): Float {
    val anchors = buildSnapAnchors(spec)
    val next = anchors.filter { it > positionMm + NEXT_ANCHOR_EPSILON_MM }.minOrNull()
        ?: spec.overallLengthMm
    return maxOf(next - positionMm, minimumMm)
}
