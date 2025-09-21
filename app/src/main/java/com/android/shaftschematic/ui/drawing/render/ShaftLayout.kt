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
 * Computes a device-space layout for rendering a shaft drawing.
 *
 * Converts canonical millimeter geometry from [com.android.shaftschematic.model.ShaftSpec]
 * into pixel coordinates for a given drawing rectangle and returns a [Result] that
 * encapsulates:
 *
 * - **Content bounds**: [contentLeftPx], [contentTopPx], [contentRightPx], [contentBottomPx]
 *   where the shaft will be drawn.
 * - **Scale**: [pxPerMm], a uniform mm→px factor used for both X (length) and Y (diameter).
 * - **Centerline**: [centerlineYPx], the vertical center of the shaft.
 * - **Spec reference**: the original [spec] used to compute the layout.
 *
 * The layout is deterministic given the inputs; it does not query resources or hold mutable state.
 */
object ShaftLayout {

    /**
     * Output of a layout computation for a shaft drawing.
     *
     * @property spec The canonical input spec (all geometry in millimeters).
     * @property pxPerMm Uniform scale from millimeters to pixels.
     * @property contentLeftPx Left edge (px) of the drawing content region.
     * @property contentTopPx Top edge (px) of the drawing content region.
     * @property contentRightPx Right edge (px) of the drawing content region.
     * @property contentBottomPx Bottom edge (px) of the drawing content region.
     * @property centerlineYPx The vertical Y position (px) of the shaft centerline.
     */
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
     * Compute the mm→px mapping and content rectangle for rendering a [spec] within
     * the provided device-space bounds.
     *
     * The algorithm fits the geometry into `[leftPx, topPx, rightPx, bottomPx]` while
     * preserving a uniform mm→px scale ([Result.pxPerMm]). The result also encodes a
     * vertical centerline for convenience in the renderer.
     *
     * @param spec Canonical shaft geometry in **millimeters**.
     * @param leftPx Left bound of the available drawing area (pixels).
     * @param topPx Top bound of the available drawing area (pixels).
     * @param rightPx Right bound of the available drawing area (pixels).
     * @param bottomPx Bottom bound of the available drawing area (pixels).
     * @return A [Result] with pixel bounds, centerline, and mm→px scale for rendering.
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
