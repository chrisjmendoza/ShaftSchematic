package com.android.shaftschematic.ui.drawing.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/**
 * File: GridRenderer.kt
 * Layer: UI → Drawing/Render
 * Purpose: Draw a major/minor grid **anchored** to the shaft coordinate system while
 *          choosing a **scale‑aware** major step so dense shafts don’t clutter.
 *
 * Anchors
 *  • Vertical grid lines: multiples of x = 0 mm (aft face). A major always lands at 0.
 *  • Horizontal grid lines: multiples around the true centerline (layout.centerlineYPx). A major always lands on it.
 *
 * Adaptivity
 *  • We choose the major step in **mm** so that majors are near `targetMajorPx` (≈ 90 px by default),
 *    snapped to “nice” values. Minors are 1/2 of major.
 *
 * Usage
 *  drawAdaptiveShaftGrid(layout, unit, targetMajorPx = 90f)
 */
object GridRenderer {

    /** Draw the grid with adaptive major/minor spacing, anchored to origin and centerline. */
    fun DrawScope.drawAdaptiveShaftGrid(
        layout: ShaftLayout.Result,
        unit: UnitSystem,
        targetMajorPx: Float = 90f,
        drawLeftPx: Float = layout.contentLeftPx,
        drawTopPx: Float = layout.contentTopPx,
        drawRightPx: Float = layout.contentRightPx,
        drawBottomPx: Float = layout.contentBottomPx,
        colorMajor: Color = Color(0x55000000),
        colorMinor: Color = Color(0x22000000)
    ) {
        val left = drawLeftPx
        val right = drawRightPx
        val top = drawTopPx
        val bottom = drawBottomPx
        val cy = layout.centerlineYPx
        val pxPerMm = layout.pxPerMm

        // --- choose major step in mm near targetMajorPx, snapped to nice sequence
        val majorStepMm = chooseNiceStepMm(
            targetMm = (targetMajorPx / pxPerMm).coerceAtLeast(1f),
            unit = unit
        )
        val minorStepMm = majorStepMm * 0.5f
        val majorPx = majorStepMm * pxPerMm
        val minorPx = minorStepMm * pxPerMm

        // --- verticals anchored to x=0
        val originXPx = layout.contentLeftPx + (0f - layout.minXMm) * pxPerMm
        run { // minors
            val first = floor((left - originXPx) / minorPx).toInt()
            val last = ceil((right - originXPx) / minorPx).toInt()
            for (i in first..last) {
                val x = originXPx + i * minorPx
                drawLine(colorMinor, Offset(x, top), Offset(x, bottom), 1f)
            }
        }
        run { // majors
            val first = floor((left - originXPx) / majorPx).toInt()
            val last = ceil((right - originXPx) / majorPx).toInt()
            for (i in first..last) {
                val x = originXPx + i * majorPx
                drawLine(colorMajor, Offset(x, top), Offset(x, bottom), 1.5f)
            }
        }

        // --- horizontals anchored to centerline
        run {
            val first = floor((top - cy) / minorPx).toInt()
            val last = ceil((bottom - cy) / minorPx).toInt()
            for (j in first..last) {
                val y = cy + j * minorPx
                drawLine(colorMinor, Offset(left, y), Offset(right, y), 1f)
            }
        }
        run {
            val first = floor((top - cy) / majorPx).toInt()
            val last = ceil((bottom - cy) / majorPx).toInt()
            for (j in first..last) {
                val y = cy + j * majorPx
                drawLine(colorMajor, Offset(left, y), Offset(right, y), 1.5f)
            }
        }
    }

    /**
     * Pick a "nice" major step in mm near the target. For inches, choose multiples of 1, 2, 5 × 10^k inches.
     * For mm, choose multiples of 1, 2, 5 × 10^k mm but clamp to >= 5 mm (readability).
     */
    private fun chooseNiceStepMm(targetMm: Float, unit: UnitSystem): Float {
        val base = if (unit == UnitSystem.INCHES) 25.4f else 1f
        val minMm = if (unit == UnitSystem.INCHES) base else 5f
        val t = targetMm.coerceAtLeast(minMm)
        // compute order of magnitude in the chosen base
        val k = kotlin.math.floor(ln((t / base).toDouble()) / ln(10.0)).toInt()
        val mag = base * 10f.pow(k)
        val candidates = floatArrayOf(1f, 2f, 5f, 10f).map { it * mag }
        return candidates.minBy { kotlin.math.abs(it - t) }
    }
}
