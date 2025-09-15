package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.data.ShaftSpecMm
import kotlin.math.max

/**
 * Computes pixel scaling and geometry extents for rendering the shaft.
 *
 * It maps the shaft's overall length (mm) to a target drawing width (px),
 * and sets up content bounds with padding.
 */
object ShaftLayout {

    data class Result(
        val spec: ShaftSpecMm,
        val pxPerMm: Float,
        val contentLeftPx: Float,
        val contentRightPx: Float,
        val contentTopPx: Float,
        val contentBottomPx: Float,
        val centerlineYPx: Float
    )

    /**
     * Compute layout for a shaft.
     *
     * @param spec the shaft spec in mm
     * @param targetWidthPx the width we want to fit the shaft into
     * @param maxHeightPx the max height allowed for the shaft drawing
     * @param paddingPx margin padding on all sides
     */
    fun compute(
        spec: ShaftSpecMm,
        targetWidthPx: Int,
        maxHeightPx: Int,
        paddingPx: Int
    ): Result {
        val totalLenMm = max(spec.overallLengthMm, 1f)

        // Horizontal scale: fit overall length into width minus padding
        val availW = targetWidthPx - 2 * paddingPx
        val pxPerMmX = availW / totalLenMm

        // Vertical scale: limit to maxHeight
        val availH = maxHeightPx - 2 * paddingPx
        val maxDiaMm = listOf(
            spec.bodies.maxOfOrNull { it.diaMm } ?: 0f,
            spec.tapers.maxOfOrNull { max(it.startDiaMm, it.endDiaMm) } ?: 0f,
            spec.threads.maxOfOrNull { it.majorDiaMm } ?: 0f,
            spec.liners.maxOfOrNull { it.odMm } ?: 0f
        ).max()

        val pxPerMmY = if (maxDiaMm > 0f) availH / maxDiaMm else pxPerMmX

        // Use uniform scale for now (keeps aspect ~1:1)
        val pxPerMm = minOf(pxPerMmX, pxPerMmY)

        val contentLeftPx = paddingPx.toFloat()
        val contentRightPx = targetWidthPx - paddingPx.toFloat()
        val contentTopPx = paddingPx.toFloat()
        val contentBottomPx = maxHeightPx - paddingPx.toFloat()

        val centerlineYPx = (contentTopPx + contentBottomPx) / 2f

        return Result(
            spec = spec,
            pxPerMm = pxPerMm,
            contentLeftPx = contentLeftPx,
            contentRightPx = contentRightPx,
            contentTopPx = contentTopPx,
            contentBottomPx = contentBottomPx,
            centerlineYPx = centerlineYPx
        )
    }
}
