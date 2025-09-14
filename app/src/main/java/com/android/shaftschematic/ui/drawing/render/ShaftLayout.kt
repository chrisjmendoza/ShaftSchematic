package com.android.shaftschematic.ui.drawing.render

import android.util.DisplayMetrics
import com.android.shaftschematic.data.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** Absolute primitives the renderer will paint. */
sealed interface Prim {
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float): Prim
    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float): Prim
    data class Path(val points: List<Pair<Float, Float>>, val close: Boolean = true): Prim
    data class Text(val x: Float, val y: Float, val text: String): Prim
}

data class LayoutResult(
    val shaftAxis: Prim.Line,
    val parts: List<Prim>,          // bodies, tapers, threads, liners
    val dims: List<Prim>,           // dimension lines + caps
    val labels: List<Prim.Text>,    // dimension labels
    val grid: List<Prim.Line> = emptyList()
)

class ShaftLayout(private val dm: DisplayMetrics) {
    private var pxPerMmX = 1f
    private var leftX = 0f
    private var rightX = 0f
    private var shaftTopY = 0f
    private var shaftCenterY = 0f
    private var shaftBottomY = 0f

    fun layout(
        spec: ShaftSpecMm,
        canvasWidthPx: Int,
        canvasHeightPx: Int,
        opts: RenderOptions
    ): LayoutResult {
        // Width/height in px
        val inchesToPxX = { inch: Float -> inch * dm.xdpi }
        val inchesToPxY = { inch: Float -> inch * dm.ydpi }

        val targetWidthPx = when (val wIn = opts.targetWidthInches) {
            null -> (canvasWidthPx - 2 * opts.paddingPx).toFloat()
            else -> inchesToPxX(wIn)
        }
        val maxHeightPx = inchesToPxY(opts.maxHeightInches)

        val totalLenMm = max(spec.overallLengthMm, 1.0f)
        pxPerMmX = targetWidthPx / totalLenMm

        leftX = opts.paddingPx.toFloat()
        rightX = leftX + targetWidthPx

        val availableH = (canvasHeightPx - 2 * opts.paddingPx).toFloat()
        val shaftBandHeight = min(maxHeightPx, max(availableH, 1.0f))
        val top = max(opts.paddingPx.toFloat(), (canvasHeightPx - shaftBandHeight) / 2f)
        val bottom = top + shaftBandHeight
        shaftTopY = top
        shaftBottomY = bottom
        shaftCenterY = (top + bottom) / 2f

        val axis = Prim.Line(leftX, shaftCenterY, rightX, shaftCenterY)

        // Gather components (support optional singletons + lists)
        val tapers = buildList {
            spec.aftTaper?.let { add(it) }
            spec.forwardTaper?.let { add(it) }
            addAll(spec.tapers)
        }.sortedBy { it.startFromAftMm }
        val threads = buildList {
            spec.aftThread?.let { add(it) }
            spec.forwardThread?.let { add(it) }
            addAll(spec.threads)
        }.sortedBy { it.startFromAftMm }
        val bodies = spec.bodies.sortedBy { it.startFromAftMm }
        val liners = spec.liners.sortedBy { it.startFromAftMm }

        val parts = mutableListOf<Prim>()
        val dims = mutableListOf<Prim>()
        val labels = mutableListOf<Prim.Text>()

        // Bodies
        bodies.forEach { b ->
            val sx = xAt(b.startFromAftMm)
            val exTrue = xAt(b.startFromAftMm + b.lengthMm)
            val compression = (b.compressionFactor ?: if (b.compressed) 0.45f else 1f).coerceIn(0.05f, 1f)
            val ex = sx + (exTrue - sx) * compression
            val (ty, by) = bodyExtent(b.diaMm)
            parts += Prim.Rect(sx, ty, ex, by)
            if (compression < 0.999f) {
                parts += squiggle(sx, ty, by, left = true)
                parts += squiggle(ex, ty, by, left = false)
            }
            val y = shaftTopY - dp(48f)
            dims += dimLine(sx, ex, y)
            labels += Prim.Text((sx + ex) / 2f, y - dp(6f),
                labelFor(b.lengthMm, fromRef(b.startFromAftMm, b.lengthMm, spec.overallLengthMm, opts)))
        }

        // Liners
        liners.forEach { l ->
            val sx = xAt(l.startFromAftMm)
            val ex = xAt(l.startFromAftMm + l.lengthMm)
            val (ty, by) = bodyExtent(l.odMm)
            val inset = (by - ty) * 0.1f
            parts += Prim.Rect(sx, ty + inset, ex, by - inset)
            val y = shaftTopY - dp(70f)
            dims += dimLine(sx, ex, y)
            labels += Prim.Text((sx + ex) / 2f, y - dp(6f),
                labelFor(l.lengthMm, fromRef(l.startFromAftMm, l.lengthMm, spec.overallLengthMm, opts)))
        }

        // Tapers
        tapers.forEach { t ->
            val sx = xAt(t.startFromAftMm)
            val ex = xAt(t.startFromAftMm + t.lengthMm)
            val (t1, b1) = bodyExtent(t.startDiaMm)
            val (t2, b2) = bodyExtent(t.endDiaMm)
            parts += Prim.Path(listOf(sx to b1, sx to t1, ex to t2, ex to b2))
            val y = shaftTopY - dp(92f)
            dims += dimLine(sx, ex, y)
            labels += Prim.Text((sx + ex) / 2f, y - dp(6f),
                labelFor(t.lengthMm, fromRef(t.startFromAftMm, t.lengthMm, spec.overallLengthMm, opts)))
        }

        // Threads (renderer will add hatch if you want later)
        threads.forEach { th ->
            val sx = xAt(th.startFromAftMm)
            val ex = xAt(th.startFromAftMm + th.lengthMm)
            val (ty, by) = bodyExtent(th.majorDiaMm)
            parts += Prim.Rect(sx, ty, ex, by)
            val y = shaftTopY - dp(114f)
            dims += dimLine(sx, ex, y)
            labels += Prim.Text((sx + ex) / 2f, y - dp(6f),
                labelFor(th.lengthMm, fromRef(th.startFromAftMm, th.lengthMm, spec.overallLengthMm, opts)))
        }

        // Overall
        val overY = shaftTopY - dp(24f)
        dims += dimLine(leftX, rightX, overY)
        labels += Prim.Text((leftX + rightX) / 2f, overY - dp(6f), lengthStr(spec.overallLengthMm))

        val grid = if (opts.showGrid) makeGrid(leftX, rightX, shaftTopY, shaftBottomY, dm) else emptyList()
        return LayoutResult(axis, parts, dims, labels, grid)
    }

    // --- helpers ---
    private fun xAt(mmFromAft: Float) = leftX + mmFromAft * pxPerMmX

    private fun bodyExtent(diaMm: Float): Pair<Float, Float> {
        val band = shaftBottomY - shaftTopY
        val clamped = 0.25f + (diaMm / 100f).coerceIn(0.1f, 0.7f)
        val half = (band * clamped) / 2f
        return (shaftCenterY - half) to (shaftCenterY + half)
    }

    private fun squiggle(x: Float, topY: Float, botY: Float, left: Boolean): Prim.Path {
        val h = botY - topY
        val mid = (topY + botY) / 2f
        val amp = h * 0.18f
        val w = h * 0.35f
        val sign = if (left) -1f else 1f
        return Prim.Path(
            listOf(
                x to topY,
                (x + sign * w * 0.5f) to (mid - amp),
                x to botY,
                (x - sign * w * 0.5f) to (mid + amp)
            ),
            close = false
        )
    }

    private fun dimLine(x1: Float, x2: Float, y: Float): Prim.Path {
        val cap = dp(8f)
        return Prim.Path(
            listOf(
                x1 to (y - cap), x1 to (y + cap), // left cap
                x1 to y, x2 to y,                 // main line
                x2 to (y - cap), x2 to (y + cap)  // right cap
            ),
            close = false
        )
    }

    private fun fromRef(startMm: Float, lengthMm: Float, totalMm: Float, opts: RenderOptions): Float =
        when (opts.referenceEnd) {
            ReferenceEnd.AFT -> startMm
            ReferenceEnd.FWD -> totalMm - (startMm + lengthMm)
        }

    // Builds the dimension label text, e.g. "120.0 mm / 4.724 in   (30.0 mm / 1.181 in from ref)"
    private fun labelFor(lengthMm: Float, fromRefMm: Float): String {
        val len = lengthStr(lengthMm)
        val from = lengthStr(fromRefMm)
        return "$len   ($from from ref)"
    }

    private fun lengthStr(mm: Float): String {
        val inches = mm / 25.4f
        return "${roundTo(mm, 1)} mm / ${roundTo(inches, 3)} in"
    }

    private fun roundTo(v: Float, d: Int): String {
        val factor = 10f.pow(d)
        val r = round(v * factor) / factor
        return "%.${d}f".format(r)
    }
    private fun Float.pow(p: Int): Float { var r = 1f; repeat(p) { r *= 10f }; return r }
    private fun dp(v: Float): Float = v * dm.density

    private fun makeGrid(l: Float, r: Float, t: Float, b: Float, dm: DisplayMetrics): List<Prim.Line> {
        val step = dm.xdpi / 4f // 1/4"
        val out = mutableListOf<Prim.Line>()
        var x = l
        while (x <= r) { out += Prim.Line(x, t, x, b); x += step }
        var y = t
        while (y <= b) { out += Prim.Line(l, y, r, y); y += step }
        return out
    }


}
