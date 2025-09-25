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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.abs

/**
 * ShaftDrawing
 *
 * Compose preview that uses the shared layout/renderer pipeline for geometry and draws a
 * **unit-aware grid** underlay with integer **major spacing** and **minor = major/2**.
 *
 * Heuristic:
 *  - Choose a major spacing (in the *display* unit) as a *nice* integer near overall/10.
 *  - "Nice" integers are picked from {1, 2, 5} × 10^k, giving values like 1,2,5,10,20,50,100...
 *  - Minors are exactly half of majors (can be 0.5 fractions in display units).
 *  - We allow leftover space at the end (no forced stretching).
 *
 * Notes:
 *  - All model geometry is in millimeters; [unit] only alters grid spacing/legend and labels.
 *  - We disable the renderer's grid via RenderOptions (to avoid double drawing).
 *  - **Centerline is not drawn**; we emphasize majors at **X=0** and **Y center** instead.
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

    // Disable internal renderer grid; we draw our own here.
    val opts = remember(unit, showGrid) {
        RenderOptions(
            showGrid = false,               // our grid below
            textSizePx = 22f,
            gridUseInches = (unit == UnitSystem.INCHES)
        )
    }

    Canvas(modifier = modifier) {
        // Add horizontal padding so geometry doesn't touch edges.
        val pad = 16f
        val layout = ShaftLayout.compute(
            spec = spec,
            leftPx = pad,
            topPx = 0f,
            rightPx = size.width - pad,
            bottomPx = size.height
        )

        if (showGrid) {
            drawIntegerSteppedGrid(
                layout = layout,
                unit = unit,
                textMeasurer = textMeasurer
            )
        }

        // Shaft geometry (bodies, liners, threads, tapers). Centerline is not drawn.
        with(ShaftRenderer) {
            draw(layout, opts, textMeasurer)
        }
    }
}

/* ─────────────────────────────
 * Integer-stepped unit-aware grid + labels
 * ───────────────────────────── */

@OptIn(ExperimentalTextApi::class)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntegerSteppedGrid(
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
    val midY = (top + bottom) * 0.5f

    // Overall in display units, choose a major spacing (nice integer) and minor = major/2
    val overallMm = layout.spec.overallLengthMm.coerceAtLeast(1f)
    val mmPerUnit = if (unit == UnitSystem.INCHES) 25.4f else 1f
    val overallDisp = overallMm / mmPerUnit
    val majDisp = chooseNiceIntegerNear(overallDisp / 10f).coerceAtLeast(1f)
    val minDisp = majDisp / 2f

    val pxPerMm = layout.pxPerMm
    val majPx = majDisp * mmPerUnit * pxPerMm
    val minPx = minDisp * mmPerUnit * pxPerMm
    val showMinors = minPx >= 6f

    // X=0 origin in pixels (datum = 0 mm is at 'left' by construction)
    val xAtZero = left

    // --- Draw vertical lines (symmetric from origin) ---
    fun drawVerticalRays(stepPx: Float) {
        // Origin line (drawn once, then march both ways)
        // Minor or major style chosen by caller
        var x: Float

        // Right side
        x = xAtZero + stepPx
        var idx = 1
        while (x <= right + 0.5f) {
            val isMajor = stepPx == majPx
            val stroke = if (isMajor) 2f else 1f
            val color = if (isMajor) Color(0x66000000) else Color(0x33000000)
            drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = stroke)
            x += stepPx; idx++
        }

        // Left side
        x = xAtZero - stepPx
        idx = 1
        while (x >= left - 0.5f) {
            val isMajor = stepPx == majPx
            val stroke = if (isMajor) 2f else 1f
            val color = if (isMajor) Color(0x66000000) else Color(0x33000000)
            drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = stroke)
            x -= stepPx; idx++
        }
    }

    // Draw minors then majors so majors sit on top
    if (showMinors) drawVerticalRays(minPx)
    drawVerticalRays(majPx)

    // Emphasize the origin major at X=0 (slightly thicker)
    drawLine(
        color = Color(0x66000000),
        start = Offset(xAtZero, top),
        end = Offset(xAtZero, bottom),
        strokeWidth = 2.8f
    )

    // --- Draw horizontal lines (minors/majors), with center emphasized ---
    val majorRows = 10  // use same density vertically for a pleasing grid
    val majorGapY = height / majorRows
    val minorGapY = majorGapY / 2f
    val showMinorY = minorGapY >= 6f

    var y = top
    var i = 0
    while (y <= bottom + 0.5f) {
        val isMajor = i % 2 == 0
        val stroke = if (isMajor) 2f else 1f
        val color = if (isMajor) Color(0x66000000) else Color(0x33000000)
        if (isMajor || showMinorY) {
            drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = stroke)
        }
        y += minorGapY; i++
    }

    // Emphasize the horizontal center major (Y=0)
    drawLine(
        color = Color(0x66000000),
        start = Offset(left, midY),
        end = Offset(right, midY),
        strokeWidth = 2.8f
    )

    // --- Axis labels ---
    val normalStyle = TextStyle(
        color = Color.Black,
        fontSize = 12.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    val specialStyle = TextStyle(
        color = Color.Black,
        fontSize = 14.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    fun drawBoxedLabel(text: String, x: Float, yTop: Float) {
        val tl = textMeasurer.measure(AnnotatedString(text), specialStyle)
        // Simple translucent background
        val pad = 4f
        drawRect(
            color = Color.Black.copy(alpha = 0.14f),
            topLeft = Offset(x - tl.size.width / 2f - pad, yTop - tl.size.height - pad),
            size = androidx.compose.ui.geometry.Size(tl.size.width + 2 * pad, tl.size.height + 2 * pad)
        )
        drawText(tl, topLeft = Offset(x - tl.size.width / 2f, yTop - tl.size.height))
    }

    // X-axis labels (bottom), “0” at origin with box
    val baseY = bottom - 4f
    val unitSuffix = if (unit == UnitSystem.INCHES) " in" else " mm"
    drawBoxedLabel("0$unitSuffix", xAtZero, baseY)

    // March labels to the right and left at each major
    val minDX = 24f
    fun labelXRay(startX: Float, step: Float, dir: Int) {
        var x = startX + step * dir
        var last = startX
        while (x in (left - 2f)..(right + 2f)) {
            if (abs(x - last) >= minDX) {
                val mmAtX = (x - left) / pxPerMm // since origin is at 'left'
                val display = mmAtX / mmPerUnit
                val txt = if (unit == UnitSystem.INCHES) {
                    if (abs(display) >= 10f) String.format("%.1f in", display) else String.format("%.2f in", display)
                } else {
                    if (abs(display) >= 100f) String.format("%.0f mm", display) else String.format("%.1f mm", display)
                }
                val tl = textMeasurer.measure(AnnotatedString(txt), normalStyle)
                drawText(tl, topLeft = Offset(x - tl.size.width / 2f, baseY - tl.size.height))
                last = x
            }
            x += step * dir
        }
    }
    labelXRay(xAtZero, majPx, +1)
    labelXRay(xAtZero, majPx, -1)

    // Y-axis labels: 0 at center (boxed), positive values mirrored up and down
    run {
        val tl = textMeasurer.measure(AnnotatedString("0"), specialStyle)
        val padLeft = left + 4f
        // boxed background
        val pad = 4f
        drawRect(
            color = Color.Black.copy(alpha = 0.14f),
            topLeft = Offset(padLeft - pad, midY - tl.size.height / 2f - pad),
            size = androidx.compose.ui.geometry.Size(tl.size.width + 2 * pad, tl.size.height + 2 * pad)
        )
        drawText(tl, topLeft = Offset(padLeft, midY - tl.size.height / 2f))
    }

    val minDY = 16f
    val rows = 10
    val stepY = height / rows
    fun labelYRay(startY: Float, step: Float, dir: Int) {
        var yPos = startY + step * dir
        var last = startY
        while (yPos in (top - 2f)..(bottom + 2f)) {
            if (abs(yPos - last) >= minDY) {
                val mmFromCenter = abs((yPos - midY) / pxPerMm)
                val display = mmFromCenter / mmPerUnit
                val txt = if (unit == UnitSystem.INCHES) {
                    if (display >= 10f) String.format("%.1f in", display) else String.format("%.2f in", display)
                } else {
                    if (display >= 100f) String.format("%.0f mm", display) else String.format("%.1f mm", display)
                }
                val tl = textMeasurer.measure(AnnotatedString(txt), normalStyle)
                drawText(tl, topLeft = Offset(left + 4f, yPos - tl.size.height / 2f))
                last = yPos
            }
            yPos += step * dir
        }
    }
    labelYRay(midY, stepY, -1) // up
    labelYRay(midY, stepY, +1) // down

    // Simple legend bar (kept): one major in current unit
    val barH = 6f
    val barW = majPx.coerceAtMost(right - left - 16f)
    if (barW > 8f) {
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(right - barW - 8f, top + 8f),
            size = androidx.compose.ui.geometry.Size(barW, barH)
        )
        val legend = if (unit == UnitSystem.INCHES) {
            String.format("%.0f in", majDisp)
        } else {
            String.format("%.0f mm", majDisp)
        }
        val style = TextStyle(
            color = Color.Black,
            fontSize = 12.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
        val layoutText = textMeasurer.measure(AnnotatedString(legend), style)
        drawText(
            layoutText,
            topLeft = Offset(right - layoutText.size.width - 8f, top + 8f + barH + 6f)
        )
    }
}

/**
 * Snap a positive float to a *nice* **integer** near it using the {1,2,5}×10^k ladder.
 * Examples:
 *  - 19.7 → 20
 *  - 61.2 → 60
 *  - 280 → 300
 */
private fun chooseNiceIntegerNear(x: Float): Float {
    if (x <= 0f) return 1f
    val k = kotlin.math.floor(kotlin.math.log10(x.toDouble())).toInt()
    val mag = 10f.pow(k)
    val c1 = 1f * mag
    val c2 = 2f * mag
    val c5 = 5f * mag
    val candidates = listOf(c1, c2, c5, 10f * mag)
    val best = candidates.minBy { kotlin.math.abs(it - x) }
    return best.roundToInt().toFloat().coerceAtLeast(1f)
}
