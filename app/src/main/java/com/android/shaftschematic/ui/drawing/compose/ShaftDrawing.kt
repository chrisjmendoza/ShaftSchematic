package com.android.shaftschematic.ui.drawing.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import kotlin.math.max
import kotlin.math.max as kmax

/* ---------- Shared interval utilities (top-level so both can use) ---------- */

private data class Interval(val a: Float, val b: Float)

/** Merge overlapping/adjacent [a,b) intervals with a tiny tolerance. */
private fun mergeIntervals(list: List<Interval>): List<Interval> {
    if (list.isEmpty()) return emptyList()
    val eps = 1e-3f
    val sorted = list.sortedBy { it.a }
    val out = ArrayList<Interval>(sorted.size)
    var cur = sorted.first()
    for (i in 1 until sorted.size) {
        val nx = sorted[i]
        if (nx.a <= cur.b + eps) {
            cur = Interval(cur.a, kmax(cur.b, nx.b))
        } else {
            out += cur
            cur = nx
        }
    }
    out += cur
    return out
}

/* ----------------------------- Composable ----------------------------- */

@Composable
fun ShaftDrawing(
    spec: ShaftSpec,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        // Layout / scaling
        val padding = 16.dp.toPx()
        val left = padding
        val right = size.width - padding
        val top = padding
        val bottom = size.height - padding

        val drawWidth = (right - left).coerceAtLeast(1f)
        val drawHeight = (bottom - top).coerceAtLeast(1f)

        val overallMm = max(1f, spec.overallLengthMm)
        val xScale = drawWidth / overallMm

        // Find max diameter among all components
        var maxDiaMm = 0f
        spec.bodies.forEach { maxDiaMm = kmax(maxDiaMm, it.diaMm) }
        spec.liners.forEach { maxDiaMm = kmax(maxDiaMm, it.odMm) }
        spec.tapers.forEach { t -> maxDiaMm = kmax(maxDiaMm, kmax(t.startDiaMm, t.endDiaMm)) }
        if (maxDiaMm <= 0f) maxDiaMm = 20f // fallback so something shows

        val desiredHalf = drawHeight * 0.33f
        val yScale = if (maxDiaMm > 0f) desiredHalf / (maxDiaMm / 2f) else 1f
        val centerY = (top + bottom) / 2f

        fun mmX(mm: Float) = left + mm * xScale
        fun halfDiaPx(diaMm: Float) = (diaMm / 2f) * yScale

        // Pens
        val thin = Stroke(width = 1.5.dp.toPx())
        val normal = Stroke(width = 2.dp.toPx())
        val bold = Stroke(width = 3.dp.toPx())

        // Collect occupied intervals for centerline masking
        val occ = mutableListOf<Interval>()
        fun addOcc(start: Float, len: Float) {
            val s = start.coerceAtLeast(0f)
            val e = (start + len).coerceAtMost(overallMm)
            if (e > s) occ += Interval(s, e)
        }
        spec.bodies.forEach { addOcc(it.startFromAftMm, it.lengthMm) }
        spec.liners.forEach { addOcc(it.startFromAftMm, it.lengthMm) }
        spec.tapers.forEach { addOcc(it.startFromAftMm, it.lengthMm) }

        val merged = mergeIntervals(occ)

        // ---- Draw components (full borders) ----

        // Bodies: full rectangle
        for (b in spec.bodies) {
            if (b.lengthMm <= 0f || b.diaMm <= 0f) continue
            val x0 = mmX(b.startFromAftMm)
            val x1 = mmX(b.startFromAftMm + b.lengthMm)
            val h = halfDiaPx(b.diaMm)
            drawRect(
                color = Color.Black,
                topLeft = Offset(x0, centerY - h),
                size = Size(x1 - x0, 2f * h),
                style = bold
            )
        }

        // Liners: full rectangle
        for (ln in spec.liners) {
            if (ln.lengthMm <= 0f || ln.odMm <= 0f) continue
            val x0 = mmX(ln.startFromAftMm)
            val x1 = mmX(ln.startFromAftMm + ln.lengthMm)
            val h = halfDiaPx(ln.odMm)
            drawRect(
                color = Color.Black,
                topLeft = Offset(x0, centerY - h),
                size = Size(x1 - x0, 2f * h),
                style = bold
            )
        }

        // Tapers: 4-sided polygon
        for (t in spec.tapers) {
            if (t.lengthMm <= 0f) continue
            val x0 = mmX(t.startFromAftMm)
            val x1 = mmX(t.startFromAftMm + t.lengthMm)
            val h0 = halfDiaPx(t.startDiaMm)
            val h1 = halfDiaPx(t.endDiaMm)

            val path = Path().apply {
                moveTo(x0, centerY - h0) // top-left
                lineTo(x1, centerY - h1) // top-right
                lineTo(x1, centerY + h1) // bottom-right
                lineTo(x0, centerY + h0) // bottom-left
                close()
            }
            drawPath(path = path, color = Color.Black, style = bold)
        }

        // ---- Centerline only in gaps ----
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        var last = 0f
        for (iv in merged) {
            if (iv.a > last) {
                drawLine(
                    color = Color.Gray,
                    start = Offset(mmX(last), centerY),
                    end = Offset(mmX(iv.a), centerY),
                    strokeWidth = normal.width,
                    pathEffect = dashEffect
                )
            }
            last = kmax(last, iv.b)
        }
        if (last < overallMm) {
            drawLine(
                color = Color.Gray,
                start = Offset(mmX(last), centerY),
                end = Offset(mmX(overallMm), centerY),
                strokeWidth = normal.width,
                pathEffect = dashEffect
            )
        }

        // End ticks to show overall span
        drawLine(
            color = Color.Black,
            start = Offset(mmX(0f), centerY - 12f),
            end = Offset(mmX(0f), centerY + 12f),
            strokeWidth = thin.width
        )
        drawLine(
            color = Color.Black,
            start = Offset(mmX(overallMm), centerY - 12f),
            end = Offset(mmX(overallMm), centerY + 12f),
            strokeWidth = thin.width
        )
    }
}
