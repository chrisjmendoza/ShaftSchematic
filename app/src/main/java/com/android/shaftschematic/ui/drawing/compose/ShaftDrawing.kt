// file: com/android/shaftschematic/ui/drawing/compose/ShaftDrawing.kt
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
 * - Centerline is currently drawn by the renderer (preview-only hide is a future toggle).
 * - The Overall label is drawn by the renderer.
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
            showGrid = false,                       // preview grid is handled below
            textSizePx = 22f,
            gridUseInches = (unit == UnitSystem.INCHES),
            paddingPx = 16
        )
    }

    // --- Preview-only safety: if overall is 0, scale to last occupied end so something is visible.
    val safeSpec = remember(spec) {
        val lastEnd = listOfNotNull(
            spec.bodies.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
            spec.tapers.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
            spec.liners.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
            spec.threads.maxOfOrNull { it.startFromAftMm + it.lengthMm },
        ).maxOrNull() ?: 0f
        if (spec.overallLengthMm <= 0f && lastEnd > 0f) spec.copy(overallLengthMm = lastEnd) else spec
    }

    Canvas(modifier = modifier) {
        val padX = opts.paddingPx.toFloat()
        val padY = 8f // small vertical space so geometry never touches top/bottom

        val layout = ShaftLayout.compute(
            spec = safeSpec,                 // ← preview-only fallback spec (overall > 0)
            leftPx = padX,
            topPx = padY,
            rightPx = size.width - padX,
            bottomPx = size.height - padY    // ← must be > topPx
        )

        if (showGrid) {
            drawIntegerGridWithLabels(
                layout = layout,
                unit = unit,
                textMeasurer = textMeasurer
            )
        }

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
    val left   = layout.contentLeftPx
    val right  = layout.contentRightPx
    val top    = layout.contentTopPx
    val bottom = layout.contentBottomPx
    val midY   = layout.centerlineYPx
    val pxPerMm = layout.pxPerMm
    val mmPerUnit = if (unit == UnitSystem.INCHES) 25.4f else 1f

    // --- Horizontal majors: centered on the centerline (midY) ---
    // We target ~10 major rows total (5 above, 5 below), but make sure spacing >= 12 px.
    val targetRows = 10
    val rawMajorY = (bottom - top) / targetRows
    val majorGapY = rawMajorY.coerceAtLeast(12f)
    val minorGapY = majorGapY / 2f
    val showMinorY = minorGapY >= 6f

    // Draw horizontal lines starting at midY and stepping up/down so a MAJOR lands on centerline.
    fun drawHorizontals(step: Float, isMajor: Boolean) {
        val stroke = if (isMajor) 2f else 1f
        val color  = if (isMajor) Color(0x66000000) else Color(0x33000000)
        var y = midY
        // go downward
        while (y <= bottom + 0.5f) {
            drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = stroke)
            y += step
        }
        // go upward
        y = midY - step
        while (y >= top - 0.5f) {
            drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = stroke)
            y -= step
        }
    }
    if (showMinorY) drawHorizontals(minorGapY, isMajor = false)
    drawHorizontals(majorGapY, isMajor = true) // centerline is one of these majors

    // --- Vertical majors: locked to x=0 origin in display units ---
    // Choose a major in display units ≈ span/10, then snap to a nice integer (1,2,5×10^k).
    val spanMm = layout.maxXMm - layout.minXMm
    val spanDisp = (spanMm / mmPerUnit).coerceAtLeast(1f)
    val majDisp = chooseNiceIntegerNear(spanDisp / 10f).coerceAtLeast(1f)
    val minDisp = majDisp / 2f

    val majorGapX = majDisp * mmPerUnit * pxPerMm
    val minorGapX = minDisp * mmPerUnit * pxPerMm
    val showMinorX = minorGapX >= 6f

    // x position of the origin (0 mm), so a major sits exactly on x=0.
    val xAtZero = left + (0f - layout.minXMm) * pxPerMm

    fun drawVerticals(step: Float, isMajor: Boolean) {
        val stroke = if (isMajor) 2f else 1f
        val color  = if (isMajor) Color(0x66000000) else Color(0x33000000)
        // rightward from origin
        var x = xAtZero
        while (x <= right + 0.5f) {
            drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = stroke)
            x += step
        }
        // leftward from origin
        x = xAtZero - step
        while (x >= left - 0.5f) {
            drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = stroke)
            x -= step
        }
    }
    if (showMinorX) drawVerticals(minorGapX, isMajor = false)
    drawVerticals(majorGapX, isMajor = true) // ensures a major at x=0

    // --- Labels: 0 at origin & center, integers outward on majors only ---
    val unitSuffix = if (unit == UnitSystem.INCHES) " in" else " mm"
    val normal = TextStyle(color = Color.Black, fontSize = 12.sp, platformStyle = PlatformTextStyle(false))
    val special = TextStyle(color = Color.Black, fontSize = 14.sp, platformStyle = PlatformTextStyle(false))

    // Boxed "0" on centerline (Y) and "0 <unit>" at x=0
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
    boxed("0$unitSuffix", xAtZero, bottom - 4f)

    // X labels on every major tick to the left/right (integers/1-decimals)
    fun labelX(startX: Float, step: Float, dir: Int) {
        var x = startX + step * dir
        while (x in (left - 2f)..(right + 2f)) {
            val mmAtX = (x - left) / pxPerMm + layout.minXMm
            val v = mmAtX / mmPerUnit
            val txt = if (unit == UnitSystem.INCHES) {
                if (kotlin.math.abs(v) >= 10f) "%.0f in".format(v) else "%.1f in".format(v)
            } else {
                if (kotlin.math.abs(v) >= 100f) "%.0f mm".format(v) else "%.0f mm".format(v)
            }
            val tl = textMeasurer.measure(AnnotatedString(txt), normal)
            drawText(tl, topLeft = Offset(x - tl.size.width / 2f, bottom - 4f - tl.size.height))
            x += step * dir
        }
    }
    labelX(xAtZero, majorGapX, +1)
    labelX(xAtZero, majorGapX, -1)

    // Y axis: boxed "0" already; add symmetric labels on majors above/below center
    fun labelY(yStart: Float, step: Float, dir: Int) {
        var y = yStart + step * dir
        while (y in (top - 2f)..(bottom + 2f)) {
            val mmFromCenter = kotlin.math.abs((y - midY) / pxPerMm)
            val v = mmFromCenter / mmPerUnit
            val txt = if (unit == UnitSystem.INCHES) {
                if (v >= 10f) "%.0f in".format(v) else "%.1f in".format(v)
            } else {
                if (v >= 100f) "%.0f mm".format(v) else "%.0f mm".format(v)
            }
            val tl = textMeasurer.measure(AnnotatedString(txt), normal)
            drawText(tl, topLeft = Offset(left + 4f, y - tl.size.height / 2f))
            y += step * dir
        }
    }
    labelY(midY, majorGapY, -1)
    labelY(midY, majorGapY, +1)
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
