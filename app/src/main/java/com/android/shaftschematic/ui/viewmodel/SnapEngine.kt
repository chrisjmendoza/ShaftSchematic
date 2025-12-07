package com.yourpackage.shaftschematic.ui.viewmodel

import kotlin.math.abs
import com.yourpackage.shaftschematic.model.ShaftSpec

data class SnapConfig(
    val toleranceMm: Float = 1.0f,   // tweak later
)

fun buildSnapAnchors(spec: ShaftSpec): List<Float> {
    val anchors = mutableSetOf<Float>()

    anchors += 0f
    anchors += spec.overallLengthMm

    spec.bodies.forEach { body ->
        anchors += body.startFromAftMm
        anchors += body.endFromAftMm
    }
    spec.tapers.forEach { taper ->
        anchors += taper.startFromAftMm
        anchors += taper.endFromAftMm
    }
    spec.threads.forEach { th ->
        anchors += th.startFromAftMm
        anchors += th.endFromAftMm
    }
    spec.liners.forEach { liner ->
        anchors += liner.startFromAftMm
        anchors += liner.endFromAftMm
    }

    return anchors
        .filter { it >= 0f }
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