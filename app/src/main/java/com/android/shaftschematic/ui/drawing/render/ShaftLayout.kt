package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.model.ShaftSpec  // ← if you use data.ShaftSpecMm, swap this import
import kotlin.math.max
import kotlin.math.min

/**
 * File: ShaftLayout.kt
 * Layer: UI → Drawing/Layout
 * Purpose: Compute a stable mapping from **shaft space (mm)** to **canvas space (px)**
 * and expose only the values required by renderers.
 *
 * Responsibilities
 * • Accept view bounds + desired margins and compute scale = pxPerMm.
 * • Define the axial origin (x=0) and place the shaft within the content rect.
 * • Provide helpers/values used by renderers:
 * - pxPerMm (Float)
 * - minXMm (Float): leftmost visible x in mm (so xPx(mm) = contentLeftPx + (mm - minXMm) * pxPerMm)
 * - centerlineYPx (Float): vertical center of the shaft
 * - contentLeft/Top/Right/Bottom px (Float)
 * • Be side‑effect free; do no drawing.
 *
 * Data Contracts / Invariants
 * • All inputs from model are millimeters.
 * • The content rect never exceeds the canvas; margins are clamped to >= 0.
 * • xPx(mm) and rPx(diaMm) must be monotonic and stable across density.
 *
 * Notes
 * • Grid anchoring relies on minXMm and centerlineYPx—do not rename without updating renderers.
 * • Keep float math consistent; avoid mixing Int/Float to prevent rounding drift.
 */
object ShaftLayout {

    data class Result(
        val spec: ShaftSpec,
        // content rect in pixels
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
    )

    /**
     * Build a robust layout from the given spec and pixel rect.
     * The rect may be inverted; we normalize it to [l,t,r,b].
     */
    fun compute(
        spec: ShaftSpec,
        leftPx: Float,
        topPx: Float,
        rightPx: Float,
        bottomPx: Float
    ): Result {
        // Normalize input rect first
        val lRaw = minOf(leftPx, rightPx)
        val rRaw = maxOf(leftPx, rightPx)
        val tRaw = minOf(topPx, bottomPx)
        val bRaw = maxOf(topPx, bottomPx)

        // Clamp width/height to be non-zero (avoid collapsing to top edge)
        val minW = 1f
        val minH = 1f
        val w = (rRaw - lRaw).coerceAtLeast(minW)
        val h = (bRaw - tRaw).coerceAtLeast(minH)

        val l = lRaw
        val t = tRaw
        val r = l + w                // recomputed from clamped width
        val b = t + h                // recomputed from clamped height

        // Visible mm span (aft→forward); avoid 0 span
        val minXMm = 0f
        val maxXMm = max(1f, spec.overallLengthMm)
        val spanMm = maxXMm - minXMm

        // Horizontal scale (mm→px)
        val pxPerMm = w / spanMm

        // Centerline is vertical middle of the content rect
        val centerlineY = t + h * 0.5f

        return Result(
            spec = spec,
            contentLeftPx = l,
            contentTopPx = t,
            contentRightPx = r,
            contentBottomPx = b,
            pxPerMm = pxPerMm,
            minXMm = minXMm,
            maxXMm = maxXMm,
            centerlineYPx = centerlineY
        )
    }
}
