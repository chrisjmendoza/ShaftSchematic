package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.model.ShaftSpec
import kotlin.math.max
import kotlin.math.min

/**
 * File: ShaftLayout.kt
 * Layer: UI → Drawing/Layout
 * Purpose: Compute a stable mapping from **shaft space (mm)** to **canvas space (px)**.
 *
 * Invariants
 * • All inputs from model are **millimeters**.
 * • Geometry never inspects UI units; conversion happens in the UI layer only.
 */
object ShaftLayout {

    data class Result(
        val spec: ShaftSpec,
        // content rect in pixels (inside margins)
        val contentLeftPx: Float,
        val contentTopPx: Float,
        val contentRightPx: Float,
        val contentBottomPx: Float,
        // mapping / span
        val pxPerMm: Float,
        val minXMm: Float,
        val maxXMm: Float,
        // guides
        val centerlineYPx: Float
    ) {
        /** Maps axial position in millimeters to canvas X in pixels. */
        fun xPx(mm: Float): Float = contentLeftPx + (mm - minXMm) * pxPerMm
        /** Maps diameter (mm) to radius (px) using current scale. */
        fun rPx(diaMm: Float): Float = (diaMm * 0.5f) * pxPerMm

        /** Debug snapshot string for overlays/logcat. */
        fun dbg(): String = buildString {
            append("pxPerMm=").append("%.3f".format(pxPerMm))
            append(" content=(")
            append("%.0f".format(contentLeftPx)).append(',')
            append("%.0f".format(contentTopPx)).append(")–(")
            append("%.0f".format(contentRightPx)).append(',')
            append("%.0f".format(contentBottomPx)).append(')')
            append(" spanMm=[").append("%.1f".format(minXMm))
            append("→").append("%.1f".format(maxXMm)).append("]")
        }
    }

    /**
     * Compute layout for the given canvas rect (px). Adds inner margins (px).
     *
     * @param leftPx Canvas left
     * @param topPx  Canvas top
     * @param rightPx Canvas right
     * @param bottomPx Canvas bottom
     * @param marginPx Inner margin on all sides (default 12 px)
     */
    fun compute(
        spec: ShaftSpec,
        leftPx: Float,
        topPx: Float,
        rightPx: Float,
        bottomPx: Float,
        marginPx: Float = 12f
    ): Result {
        // Normalize rect
        val L = min(leftPx, rightPx)
        val R = max(leftPx, rightPx)
        val T = min(topPx, bottomPx)
        val B = max(topPx, bottomPx)
        val W = max(1f, R - L)
        val H = max(1f, B - T)

        // Content rect inside margins (clamped so it doesn't invert)
        val m = max(0f, marginPx)
        val cL = L + m
        val cT = T + m
        val cR = R - m
        val cB = B - m
        val cW = max(1f, cR - cL)
        val cH = max(1f, cB - cT)

        // Axial span (mm) — left=0, right=overall
        val minXMm = 0f
        val maxXMm = max(1f, spec.overallLengthMm)
        val axialSpanMm = maxXMm - minXMm

        // Radial span (mm) — use max diameter across all components (and at least a small minimum)
        val maxDiaMm = listOf(
            spec.bodies.maxOfOrNull  { it.diaMm } ?: 0f,
            spec.tapers.maxOfOrNull  { max(it.startDiaMm, it.endDiaMm) } ?: 0f,
            spec.liners.maxOfOrNull  { it.odMm } ?: 0f,
            spec.threads.maxOfOrNull { it.majorDiaMm } ?: 0f
        ).maxOrNull() ?: 0f

        // Minimum radial span so a very thin shaft still gets some vertical presence
        val minDiaForFitMm = 10f
        val fitDiaMm = max(maxDiaMm, minDiaForFitMm)
        val radialSpanMm = fitDiaMm // we render around centerline, so top/bottom split evenly

        // Scale: choose the tighter of width/height constraints
        val pxPerMmX = cW / axialSpanMm
        val pxPerMmY = cH / radialSpanMm
        val pxPerMm = min(pxPerMmX, pxPerMmY).coerceAtLeast(0.0001f) // avoid zero

        // Vertical centerline
        val centerlineY = cT + cH * 0.5f

        return Result(
            spec = spec,
            contentLeftPx = cL,
            contentTopPx = cT,
            contentRightPx = cR,
            contentBottomPx = cB,
            pxPerMm = pxPerMm,
            minXMm = minXMm,
            maxXMm = maxXMm,
            centerlineYPx = centerlineY
        )
    }
}
