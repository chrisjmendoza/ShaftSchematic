package com.android.shaftschematic.ui.drawing.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * On-screen shaft preview with a unit-aware grid aligned to the renderer.
 *
 * - Grid is symmetric around X=0 and Y center.
 * - Vertical origin uses layout.minXMm so X=0 aligns even if span goes negative.
 * - Horizontal center major uses layout.centerlineYPx (same as renderer).
 * - Centerline itself is not drawn in preview (policy).
 * - The Overall label is drawn by the renderer (single source of truth).
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun ShaftDrawing(
    spec: ShaftSpec,
    unit: UnitSystem,
    showGrid: Boolean,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val textMeasurer = rememberTextMeasurer()

    // We draw the grid here; disable the renderer's grid to avoid double drawing.
    val opts = remember(unit, showGrid) {
        RenderOptions(
            showGrid = false,
            textSizePx = 22f,                 // Float per your RenderOptions
            gridUseInches = (unit == UnitSystem.INCHES),
            paddingPx = 16                    // Int (fixes the first mismatch)
        )
    }

    Canvas(modifier = modifier) {
        val padX = opts.paddingPx
        val layout = ShaftLayout.compute(
            spec = spec,
            leftPx = padX.toFloat(),          // Floats (fixes the second mismatch)
            topPx = 0f,
            rightPx = size.width - padX.toFloat(),
            bottomPx = size.height
        )

        if (showGrid) {
            drawIntegerGridWithLabels(
                layout = layout,
                unit = unit,
                textMeasurer = textMeasurer
            )
        }

        // Geometry (and the single Overall label) via renderer
        with(ShaftRenderer) {
            draw(layout, opts, textMeasurer)
        }
    }
}

/* ───────────────────────────────────────────────────────────────────────────
 * Grid + labels (integer-stepped majors; minors = major/2)
 * ─────────────────────────────────────────────────────────────────────────── */

@OptIn(ExperimentalTextApi::class)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntegerGridWithLabels(
    layout: ShaftLayout.Result,
    unit: UnitSystem,
    textMeasurer: TextMeasurer
) {
    val left = layout.contentLeftPx
    val right = layout.contentRightPx
    val top = layout.contentTopPx
    val bottom = layout.contentBottomPx
    val width = right - left
    val height = bottom - top

    val midY = layout.centerlineYPx
    val mmPerUnit = if (unit == UnitSystem.INCHES) 25.4f else 1f

    // Choose major size ~ 1/10 of visible span, snapped to a "nice" integer in display units.
    val spanMm = layout.maxXMm - layout.minXMm
    val spanDisp = (spanMm / mmPerUnit).coerceAtLeast(1f)
    val majDisp = chooseNiceIntegerNear(spanDisp / 10f).coerceAtLeast(1f)
    val minDisp = majDisp / 2f

    val pxPerMm = layout.pxPerMm
    val majPx = majDisp * mmPerUnit * pxPerMm
    val minPx = minDisp * mmPerUnit * pxPerMm
    val showMinors = minPx >= 6f

    // Align vertical origin at X=0, taking minXMm into account.
    val xAtZero = left + (0f - layout.minXMm) * pxPerMm

    // Vertical grid lines: march out from origin both directions (minors then majors).
    fun drawVerticalRays(step: Float, isMajor: Boolean) {
        val stroke = if (isMajor) 2f else 1f
        val color = if (isMajor) Color(0x66000000) else Color(0x33000000)

        var x = xAtZero + step
        while (x <= right + 0.5f) {
            drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = stroke)
            x += step
        }
        x = xAtZero - step
        while (x >= left - 0.5f) {
            drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = stroke)
            x -= step
        }
    }
    if (showMinors) drawVerticalRays(minPx, false)
    drawVerticalRays(majPx, true)

    // Emphasize origin and exact center line used by the renderer.
    drawLine(Color(0x66000000), Offset(xAtZero, top), Offset(xAtZero, bottom), strokeWidth = 2.8f)

    // Horizontal rows (even spacing); emphasize center.
    val rows = 10
    val majorGapY = height / rows
    val minorGapY = majorGapY / 2f
    val showMinorY = minorGapY >= 6f

    var y = top
    var i = 0
    while (y <= bottom + 0.5f) {
        val isMajor = i % 2 == 0
        val stroke = if (isMajor) 2f else 1f
        val color = if (isMajor) Color(0x66000000) else Color(0x33000000)
        if (isMajor || showMinorY) drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = stroke)
        y += minorGapY; i++
    }
    drawLine(Color(0x66000000), Offset(left, midY), Offset(right, midY), strokeWidth = 2.8f)

    // Axis labels
    drawAxisLabels(layout, unit, majPx, textMeasurer)
}

@OptIn(ExperimentalTextApi::class)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAxisLabels(
    layout: ShaftLayout.Result,
    unit: UnitSystem,
    majorGapPx: Float,
    textMeasurer: TextMeasurer
) {
    val left = layout.contentLeftPx
    val right = layout.contentRightPx
    val top = layout.contentTopPx
    val bottom = layout.contentBottomPx
    val midY = layout.centerlineYPx
    val pxPerMm = layout.pxPerMm
    val mmPerUnit = if (unit == UnitSystem.INCHES) 25.4f else 1f

    val normal = TextStyle(color = Color.Black, fontSize = 12.sp, platformStyle = PlatformTextStyle(false))
    val special = TextStyle(color = Color.Black, fontSize = 14.sp, platformStyle = PlatformTextStyle(false))

    val xAtZero = left + (0f - layout.minXMm) * pxPerMm
    val baseY = bottom - 4f
    val unitSuffix = if (unit == UnitSystem.INCHES) " in" else " mm"

    fun boxed(text: String, x: Float, yTop: Float) {
        val tl = textMeasurer.measure(AnnotatedString(text), special)
        val pad = 4f
        drawRect(
            color = Color.Black.copy(alpha = 0.14f),
            topLeft = Offset(x - tl.size.width / 2f - pad, yTop - tl.size.height - pad),
            size = androidx.compose.ui.geometry.Size(tl.size.width + 2 * pad, tl.size.height + 2 * pad)
        )
        drawText(tl, topLeft = Offset(x - tl.size.width / 2f, yTop - tl.size.height))
    }
    boxed("0$unitSuffix", xAtZero, baseY)

    val minDX = 24f
    fun labelX(startX: Float, step: Float, dir: Int) {
        var x = startX + step * dir
        var last = startX
        while (x in (left - 2f)..(right + 2f)) {
            if (abs(x - last) >= minDX) {
                val mmAtX = (x - left) / pxPerMm + layout.minXMm
                val v = mmAtX / mmPerUnit
                val txt = if (unit == UnitSystem.INCHES) {
                    if (abs(v) >= 10f) "%.1f in".format(v) else "%.2f in".format(v)
                } else {
                    if (abs(v) >= 100f) "%.0f mm".format(v) else "%.1f mm".format(v)
                }
                val tl = textMeasurer.measure(AnnotatedString(txt), normal)
                drawText(tl, topLeft = Offset(x - tl.size.width / 2f, baseY - tl.size.height))
                last = x
            }
            x += step * dir
        }
    }
    labelX(xAtZero, majorGapPx, +1)
    labelX(xAtZero, majorGapPx, -1)

    // Y labels: 0 at center (boxed), mirrored magnitudes above/below
    run {
        val tl = textMeasurer.measure(AnnotatedString("0"), special)
        val padLeft = left + 4f
        val pad = 4f
        drawRect(
            color = Color.Black.copy(alpha = 0.14f),
            topLeft = Offset(padLeft - pad, midY - tl.size.height / 2f - pad),
            size = androidx.compose.ui.geometry.Size(tl.size.width + 2 * pad, tl.size.height + 2 * pad)
        )
        drawText(tl, topLeft = Offset(padLeft, midY - tl.size.height / 2f))
    }

    val rows = 10
    val stepY = (bottom - top) / rows
    val minDY = 16f
    fun labelY(startY: Float, step: Float, dir: Int) {
        var yPos = startY + step * dir
        var last = startY
        while (yPos in (top - 2f)..(bottom + 2f)) {
            if (abs(yPos - last) >= minDY) {
                val mmFromCenter = abs((yPos - midY) / pxPerMm)
                val v = mmFromCenter / mmPerUnit
                val txt = if (unit == UnitSystem.INCHES) {
                    if (v >= 10f) "%.1f in".format(v) else "%.2f in".format(v)
                } else {
                    if (v >= 100f) "%.0f mm".format(v) else "%.1f mm".format(v)
                }
                val tl = textMeasurer.measure(AnnotatedString(txt), normal)
                drawText(tl, topLeft = Offset(left + 4f, yPos - tl.size.height / 2f))
                last = yPos
            }
            yPos += step * dir
        }
    }
    labelY(midY, stepY, -1)
    labelY(midY, stepY, +1)
}

/** Choose a “nice” integer near x using {1,2,5}×10^k. */
private fun chooseNiceIntegerNear(x: Float): Float {
    if (x <= 0f) return 1f
    val k = kotlin.math.floor(log10(x.toDouble())).toInt()
    val mag = 10f.pow(k)
    val candidates = listOf(1f * mag, 2f * mag, 5f * mag, 10f * mag)
    val best = candidates.minBy { kotlin.math.abs(it - x) }
    return best.roundToInt().toFloat().coerceAtLeast(1f)
}
