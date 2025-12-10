package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.Segment
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.endFromAftMm
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
