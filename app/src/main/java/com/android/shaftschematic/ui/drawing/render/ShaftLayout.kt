package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.ThreadSpec
import com.android.shaftschematic.model.maxOuterDiaMm
import kotlin.math.max
import kotlin.math.min

/**
 * This version uses the **canonical** model layer types (no more data.ShaftSpecMm).
 * All geometry in the spec is assumed to be **millimeters (mm)**; the returned
 * [pxPerMm] converts mm → px for the current content rectangle.
 */
object ShaftLayout {


    data class Result(
        val spec: ShaftSpec,
        val contentLeftPx: Float,
        val contentTopPx: Float,
        val contentRightPx: Float,
        val contentBottomPx: Float,
        val pxPerMm: Float,
        val centerlineYPx: Float
    )


    /**
     * Compute a layout that fits the full shaft length into [leftPx,rightPx] and
     * (approximately) fits the max diameter into [topPx,bottomPx] with a little headroom.
     *
     * Signature intentionally simple so call sites can pass positionally.
     */
    fun compute(
        spec: ShaftSpec,
        leftPx: Float,
        topPx: Float,
        rightPx: Float,
        bottomPx: Float
    ): Result {
        val widthPx = (rightPx - leftPx).coerceAtLeast(1f)
        val heightPx = (bottomPx - topPx).coerceAtLeast(1f)


        val overallLenMm = spec.overallLengthMm.coerceAtLeast(1f)
        val maxDiaMm = max(1f, spec.maxOuterDiaMm())


// Convert mm → px. We try to fit the length horizontally and the diameter vertically.
// Leave vertical headroom for grid labels/legend.
        val scaleX = widthPx / overallLenMm
        val scaleY = heightPx / (maxDiaMm * 1.4f) // 40% headroom for labels/grid
        val pxPerMm = min(scaleX, scaleY).coerceAtLeast(0.01f)


        val centerlineYPx = topPx + heightPx / 2f
        val halfDiaPx = (maxDiaMm * 0.5f) * pxPerMm
        val labelPadPx = 24f


        val contentTopPx = max(topPx, centerlineYPx - halfDiaPx - labelPadPx)
        val contentBottomPx = min(bottomPx, centerlineYPx + halfDiaPx + labelPadPx)


        return Result(
            spec = spec,
            contentLeftPx = leftPx,
            contentTopPx = contentTopPx,
            contentRightPx = rightPx,
            contentBottomPx = contentBottomPx,
            pxPerMm = pxPerMm,
            centerlineYPx = centerlineYPx
        )
    }


    // --- Helpers kept for potential future use (min/max across lists) ---
    private inline fun <T> maxFromBodies(items: List<Body>, sel: (Body) -> T): Float =
        items.maxOfOrNull { sel(it) as Float } ?: 0f


    private inline fun <T> maxFromTapers(items: List<Taper>, sel: (Taper) -> T): Float =
        items.maxOfOrNull { sel(it) as Float } ?: 0f


    private inline fun <T> maxFromThreads(items: List<ThreadSpec>, sel: (ThreadSpec) -> T): Float =
        items.maxOfOrNull { sel(it) as Float } ?: 0f


    private inline fun <T> maxFromLiners(items: List<Liner>, sel: (Liner) -> T): Float =
        items.maxOfOrNull { sel(it) as Float } ?: 0f
}
