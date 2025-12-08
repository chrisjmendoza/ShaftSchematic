package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.Segment
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.endFromAftMm
import kotlin.math.abs

data class SnapConfig(
    val toleranceMm: Float = 1.0f,   // tweak later
)

fun buildSnapAnchors(spec: ShaftSpec): List<Float> {
    val anchors = mutableSetOf<Float>()

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

    return anchors
        .filter { it >= 0f && it <= spec.overallLengthMm }
        .sorted()
}

fun snapPositionMm(
    rawMm: Float,
    anchors: List<Float>,
    config: SnapConfig = SnapConfig()
): Float {
    if (anchors.isEmpty()) return rawMm

    val nearest = anchors.minByOrNull { anchor -> abs(anchor - rawMm) } ?: return rawMm
    return if (abs(nearest - rawMm) <= config.toleranceMm) nearest else rawMm
}