package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.data.BodySegmentSpec
import com.android.shaftschematic.data.LinerSpec
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.data.TaperSpec
import com.android.shaftschematic.data.ThreadSpec
import kotlin.math.max
import kotlin.math.min

object ShaftLayout {

    data class Result(
        val spec: ShaftSpecMm,
        val contentLeftPx: Float,
        val contentTopPx: Float,
        val contentRightPx: Float,
        val contentBottomPx: Float,
        val pxPerMm: Float,
        val centerlineYPx: Float
    )

    /**
     * Compute a layout that fits the full shaft length into [leftPx,rightPx] and
     * fits the max diameter into [topPx,bottomPx] with a little headroom.
     *
     * Signature intentionally simple so call sites can pass positionally.
     */
    fun compute(
        spec: ShaftSpecMm,
        leftPx: Float,
        topPx: Float,
        rightPx: Float,
        bottomPx: Float
    ): Result {
        val widthPx = (rightPx - leftPx).coerceAtLeast(1f)
        val heightPx = (bottomPx - topPx).coerceAtLeast(1f)

        val overallLenMm = spec.overallLengthMm.coerceAtLeast(1f)

        // Find max diameter across all components (fallback if none set)
        val maxDiaMm = maxOfOrDefault(
            listOf(
                maxFromBodies(spec.bodies) { it.diaMm },
                maxFromTapers(spec.tapers) { max(it.startDiaMm, it.endDiaMm) },
                spec.aftTaper?.let { max(it.startDiaMm, it.endDiaMm) } ?: 0f,
                spec.forwardTaper?.let { max(it.startDiaMm, it.endDiaMm) } ?: 0f,
                maxFromThreads(spec.threads) { it.majorDiaMm },
                maxFromLiners(spec.liners) { it.odMm }
            ),
            defaultValue = 50f // sensible fallback if everything is zero
        ).coerceAtLeast(1f)

        // Horizontal scale must fit the overall length within width
        val pxPerMmX = widthPx / overallLenMm

        // Vertical scale must fit +/- radius with a margin factor
        val margin = 1.15f
        val pxPerMmY = heightPx / (maxDiaMm * margin)

        val pxPerMm = min(pxPerMmX, pxPerMmY).coerceAtLeast(0.01f)

        val centerlineYPx = topPx + heightPx / 2f

        return Result(
            spec = spec,
            contentLeftPx = leftPx,
            contentTopPx = topPx,
            contentRightPx = rightPx,
            contentBottomPx = bottomPx,
            pxPerMm = pxPerMm,
            centerlineYPx = centerlineYPx
        )
    }

    // ---------- helpers ----------

    private inline fun <T> maxFromBodies(items: List<BodySegmentSpec>, sel: (BodySegmentSpec) -> T): Float =
        items.maxOfOrNull { sel(it) as Float } ?: 0f

    private inline fun <T> maxFromTapers(items: List<TaperSpec>, sel: (TaperSpec) -> T): Float =
        items.maxOfOrNull { sel(it) as Float } ?: 0f

    private inline fun <T> maxFromThreads(items: List<ThreadSpec>, sel: (ThreadSpec) -> T): Float =
        items.maxOfOrNull { sel(it) as Float } ?: 0f

    private inline fun <T> maxFromLiners(items: List<LinerSpec>, sel: (LinerSpec) -> T): Float =
        items.maxOfOrNull { sel(it) as Float } ?: 0f

    private fun maxOfOrDefault(values: List<Float>, defaultValue: Float): Float {
        var m = defaultValue
        for (v in values) if (v > m) m = v
        return m
    }
}
