package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.model.ShaftSpec
import kotlin.math.max
import kotlin.math.min

/**
 * Layout mapper for the on-screen preview.
 *
 * Maps model millimeters → screen pixels inside a fixed content rectangle.
 * This version fits the shaft geometry on **both axes**:
 *  - X fit: uses the total span [minXMm, maxXMm] across all components (allows negative).
 *  - Y fit: uses the maximum **diameter** found across all components.
 * The chosen scale is the minimum of the two so everything remains encapsulated.
 */
class ShaftLayout private constructor(
    val spec: ShaftSpec,
    val pxPerMm: Float,
    val contentLeftPx: Float,
    val contentTopPx: Float,
    val contentRightPx: Float,
    val contentBottomPx: Float,
    /** Left bound of the drawn span in mm (can be negative). */
    val minXMm: Float,
    /** Right bound of the drawn span in mm. */
    val maxXMm: Float,
    /** Vertical centerline Y in pixels (shaft center). */
    val centerlineYPx: Float
) {

    /** Immutable view handed to renderers. */
    class Result internal constructor(private val l: ShaftLayout) {
        val spec get() = l.spec
        val pxPerMm get() = l.pxPerMm
        val contentLeftPx get() = l.contentLeftPx
        val contentTopPx get() = l.contentTopPx
        val contentRightPx get() = l.contentRightPx
        val contentBottomPx get() = l.contentBottomPx
        val minXMm get() = l.minXMm
        val maxXMm get() = l.maxXMm
        val centerlineYPx get() = l.centerlineYPx
    }

    companion object {

        /**
         * Compute a layout for the given [spec] inside the pixel rectangle.
         *
         * X span:
         *   minX = min(0, min(component.start))
         *   maxX = max(component.start + component.length)
         *
         * Y fit:
         *   maxDiaMm = max of { body.dia, thread.majorDia, liner.od, taper.max(startDia, endDia) }
         *
         * Scale:
         *   pxPerMm = min( widthPx / (maxX - minX), heightPx / maxDiaMm )
         */
        fun compute(
            spec: ShaftSpec,
            leftPx: Float,
            topPx: Float,
            rightPx: Float,
            bottomPx: Float
        ): Result {
            // ----- Collect X extents -----
            var minStart = Float.POSITIVE_INFINITY
            var maxEnd = Float.NEGATIVE_INFINITY

            fun consider(start: Float, length: Float) {
                minStart = min(minStart, start)
                maxEnd = max(maxEnd, start + length)
            }

            spec.bodies.forEach { consider(it.startFromAftMm, it.lengthMm) }
            spec.tapers.forEach { consider(it.startFromAftMm, it.lengthMm) }
            spec.threads.forEach { consider(it.startFromAftMm, it.lengthMm) }
            spec.liners.forEach { consider(it.startFromAftMm, it.lengthMm) }

            if (minStart == Float.POSITIVE_INFINITY) {
                // No components: fall back to [0, overall] (or [0,1] if overall is 0)
                minStart = 0f
                maxEnd = if (spec.overallLengthMm > 0f) spec.overallLengthMm else 1f
            }

            val minX = min(0f, minStart)
            val maxX = maxEnd
            val spanMm = max(1f, maxX - minX)

            // ----- Collect maximum diameter for vertical fit -----
            var maxDiaMm = 0f
            spec.bodies.forEach { maxDiaMm = max(maxDiaMm, it.diaMm) }
            spec.threads.forEach { maxDiaMm = max(maxDiaMm, it.majorDiaMm) }
            spec.liners.forEach { maxDiaMm = max(maxDiaMm, it.odMm) }
            spec.tapers.forEach { t ->
                maxDiaMm = max(maxDiaMm, max(t.startDiaMm, t.endDiaMm))
            }
            if (maxDiaMm <= 0f) {
                // If we somehow have no diameters yet, keep something tiny so we don't divide by zero.
                maxDiaMm = 1f
            }

            // ----- Compute scale (fit both axes) -----
            val widthPx = max(1f, rightPx - leftPx)
            val heightPx = max(1f, bottomPx - topPx)
            val pxPerMmX = widthPx / spanMm
            val pxPerMmY = heightPx / maxDiaMm
            val pxPerMm = min(pxPerMmX, pxPerMmY)

            val centerlineYPx = topPx + heightPx * 0.5f

            return Result(
                ShaftLayout(
                    spec = spec,
                    pxPerMm = pxPerMm,
                    contentLeftPx = leftPx,
                    contentTopPx = topPx,
                    contentRightPx = rightPx,
                    contentBottomPx = bottomPx,
                    minXMm = minX,
                    maxXMm = maxX,
                    centerlineYPx = centerlineYPx
                )
            )
        }
    }
}
