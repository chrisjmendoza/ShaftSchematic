package com.android.shaftschematic.ui.drawing

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.config.DisplayCompressionConfig as C
import kotlin.math.max

/**
 * LayoutMap — maps real mm → drawn X (px/pt) for rendering.
 * Only Body spans may be horizontally compressed for display; model math stays in mm.
 */
private enum class SpanKind { BODY, TAPER, THREAD, LINER }

sealed class VisualSpan(
    val realStartMm: Float,
    val realLenMm: Float,
    val drawnLenPx: Float
) {
    class Body(realStartMm: Float, realLenMm: Float, drawnLenPx: Float, val compressed: Boolean)
        : VisualSpan(realStartMm, realLenMm, drawnLenPx)
    class Taper(realStartMm: Float, realLenMm: Float, drawnLenPx: Float)
        : VisualSpan(realStartMm, realLenMm, drawnLenPx)
    class Thread(realStartMm: Float, realLenMm: Float, drawnLenPx: Float)
        : VisualSpan(realStartMm, realLenMm, drawnLenPx)
    class Liner(realStartMm: Float, realLenMm: Float, drawnLenPx: Float)
        : VisualSpan(realStartMm, realLenMm, drawnLenPx)
}

data class LayoutMap(
    val spans: List<VisualSpan>,
    /** Convert any real mm position to its drawn X coordinate (same units as pxPerMm). */
    val realToDrawX: (Float) -> Float
)

/**
 * Build a layout where long bodies are compressed and marked with breaks.
 *
 * @param pxPerMm base scale for this surface (pt/mm for PDF, px/mm for preview)
 * @param canvasWidthPx width of the drawable area (same units as pxPerMm)
 * @param compress whether compression is enabled
 * @param minBodyPx minimum drawn width for any body
 * @param compressFactor fraction of true length to draw for long bodies (0.30..1.00). 1f = none
 */
fun buildLayoutMap(
    spec: ShaftSpec,
    pxPerMm: Float,
    canvasWidthPx: Float,
    compress: Boolean = true,
    minBodyPx: Float = C.MIN_BODY_DRAWN_PT,
    compressFactor: Float = 1f
): LayoutMap {
    data class Raw(val start: Float, val len: Float, val kind: SpanKind)

    val raws = buildList {
        spec.bodies.forEach  { add(Raw(it.startFromAftMm, it.lengthMm, SpanKind.BODY)) }
        spec.tapers.forEach  { add(Raw(it.startFromAftMm, it.lengthMm, SpanKind.TAPER)) }
        spec.threads.forEach { add(Raw(it.startFromAftMm, it.lengthMm, SpanKind.THREAD)) }
        spec.liners.forEach  { add(Raw(it.startFromAftMm, it.lengthMm, SpanKind.LINER)) }
    }.sortedBy { it.start }

    val spans = mutableListOf<VisualSpan>()
    raws.forEach { r ->
        val realPx = r.len * pxPerMm
        val isLongBody = compress && r.kind == SpanKind.BODY && r.len >= C.BODY_BREAK_MIN_MM
        if (isLongBody && compressFactor < 0.999f) {
            val px = max(minBodyPx, realPx * compressFactor)
            spans += VisualSpan.Body(r.start, r.len, px, compressed = true)
        } else {
            val px = max(0f, realPx)
            spans += when (r.kind) {
                SpanKind.BODY   -> VisualSpan.Body(r.start, r.len, px, compressed = false)
                SpanKind.TAPER  -> VisualSpan.Taper(r.start, r.len, px)
                SpanKind.THREAD -> VisualSpan.Thread(r.start, r.len, px)
                SpanKind.LINER  -> VisualSpan.Liner(r.start, r.len, px)
            }
        }
    }

    // Cumulative drawn X mapping
    val ordered = spans.sortedBy { it.realStartMm }
    val accum = FloatArray(ordered.size + 1)
    for (i in ordered.indices) accum[i + 1] = accum[i] + ordered[i].drawnLenPx

    val realEnds = FloatArray(ordered.size) { i -> ordered[i].realStartMm + ordered[i].realLenMm }

    fun toX(mm: Float): Float {
        var i = 0
        while (i < ordered.size && mm >= realEnds[i]) i++
        if (i >= ordered.size) return accum.last()
        val s = ordered[i]
        val withinMm = (mm - s.realStartMm).coerceIn(0f, s.realLenMm)
        val scale = if (s.realLenMm <= 0f) 0f else s.drawnLenPx / s.realLenMm
        return accum[i] + withinMm * scale
    }
    return LayoutMap(ordered, ::toX)
}




