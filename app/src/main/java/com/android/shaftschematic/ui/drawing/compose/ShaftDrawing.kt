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

/**
 * ShaftDrawing
 *
 * Compose preview that uses the shared layout/renderer pipeline for geometry and draws a
 * **unit-aware grid** underlay with integer **major spacing** and **minor = major/2**.
 *
 * Heuristic:
 *  - Choose a major spacing (in the *display* unit) as a *nice* integer near overall/10.
 *  - "Nice" integers are picked from {1, 2, 5} × 10^k, giving values like 1,2,5,10,20,25,50,100...
 *  - Minors are exactly half of majors (can be 0.5 fractions in display units).
 *  - We allow leftover space at the end (no forced stretching).
 *
 * Notes:
 *  - All model geometry is in millimeters; [unit] only alters grid spacing/legend (and any labels).
 *  - We disable the renderer's grid via RenderOptions (to avoid double drawing).
 *  - Tapers remain **trapezoids** (renderer uses straight top/bottom edges from SET→LET).
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun ShaftDrawing(
    spec: ShaftSpec,
    unit: UnitSystem,
    showGrid: Boolean,
    modifier: Modifier = Modifier.fillMaxSize() // ensures the Canvas fills its parent
) {
    val textMeasurer = rememberTextMeasurer()

    // Disable internal renderer grid; we draw our own here.
    val opts = remember(unit, showGrid) {
        RenderOptions(
            showGrid = false,
            gridUseInches = (unit == UnitSystem.INCHES),
        )
    }

    // Compute a safe overall for preview if the user hasn't set one yet
    val lastEndMm = remember(spec) {
        listOfNotNull(
            spec.bodies.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
            spec.tapers.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
            spec.liners.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
            spec.threads.maxOfOrNull { it.startFromAftMm + it.lengthMm },
        ).maxOrNull() ?: 0f
    }
    val safeSpec = if (spec.overallLengthMm <= 0f && lastEndMm > 0f) {
        // preview-only fallback so scaling is sane; PDF can still require an explicit overall
        spec.copy(overallLengthMm = lastEndMm)
    } else spec

    Canvas(modifier = modifier) {
        // Lay out drawing coordinates (mm → px mapping, content rect, centerline, etc.).
        val layout = ShaftLayout.compute(
            spec = safeSpec,
            leftPx = 0f,
            topPx = 0f,
            rightPx = size.width,
            bottomPx = size.height
        )

        if (showGrid) {
            drawIntegerSteppedGrid(
                layout = layout,
                unit = unit,
                textMeasurer = textMeasurer
            )
        }

        // Draw the actual shaft (bodies, liners, threads, tapers-as-trapezoids, dims).
        with(ShaftRenderer) {
            // Member extension: receiver is the Canvas' DrawScope
            draw(layout, opts, textMeasurer)
        }
    }
}

/* ─────────────────────────────
 * Integer-stepped unit-aware grid
 * ───────────────────────────── */

@OptIn(ExperimentalTextApi::class)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntegerSteppedGrid(
    layout: ShaftLayout.Result,
    unit: UnitSystem,
    textMeasurer: TextMeasurer
) {
    // Target number of "major steps" across the length (≈ feel).
    // Use 10 to match the example: overall=200 → major 20; overall=208 → major 20 with 8 leftover.
    val TARGET_DIVISIONS = 10f

    val left   = layout.contentLeftPx
    val right  = layout.contentRightPx
    val top    = layout.contentTopPx
    val bottom = layout.contentBottomPx
    val cy     = layout.centerlineYPx
    val pxPerMm = layout.pxPerMm

    val overallMm = max(1f, layout.spec.overallLengthMm)

    // Convert overall into **display units**
    val toDisp = if (unit == UnitSystem.MILLIMETERS) 1f else (1f / 25.4f)
    val toMm   = if (unit == UnitSystem.MILLIMETERS) 1f else 25.4f

    val overallDisp = overallMm * toDisp
    val rawStepDisp = (overallDisp / TARGET_DIVISIONS).coerceAtLeast(1f)

    // Snap to a *nice* integer (from {1,2,5} × 10^k), but always an INTEGER.
    val majorDisp = snapNiceInteger(rawStepDisp)

    // Convert back to mm for drawing
    val majorMm  = majorDisp * toMm
    val minorMm  = majorMm * 0.5f
    val majorPx  = majorMm * pxPerMm
    val minorPx  = minorMm * pxPerMm

    // Avoid over-dense minors
    val minVisible = 6f
    val showMinors = minorPx >= minVisible

    // Pens/colors
    val majorStroke = 1.25f
    val minorStroke = 1.0f
    val majorColor  = Color(0x33000000)   // 20% black
    val minorColor  = Color(0x1A000000)   // 10% black

    // ---- Vertical lines (X along length in mm) ----
    val lastMinorIdx = floor(overallMm / minorMm).toInt()  // no extra line beyond overall
    for (i in 0..lastMinorIdx) {
        val mm = i * minorMm
        val x = left + mm * pxPerMm
        val isMajor = (i % 2 == 0) // every 2 minors = 1 major (since minor = major/2)
        if (isMajor) {
            drawLine(majorColor, Offset(x, top), Offset(x, bottom), majorStroke)
        } else if (showMinors) {
            drawLine(minorColor, Offset(x, top), Offset(x, bottom), minorStroke)
        }
    }

    // ---- Horizontal lines (Y about centerline) ----
    // Step in "minor" pixels up/down from the centerline.
    var off = 0f
    var j = 0
    while (cy + off <= bottom + 0.5f) {
        val isMajor = (j % 2 == 0)
        val col = if (isMajor) majorColor else minorColor
        val sw  = if (isMajor) majorStroke else minorStroke

        if (isMajor || showMinors) {
            // below centerline
            val y1 = cy + off
            if (y1 in top - 0.5f..bottom + 0.5f) drawLine(col, Offset(left, y1), Offset(right, y1), sw)
            // mirror above centerline (skip double-drawing centerline at off==0)
            if (off > 0f) {
                val y2 = cy - off
                if (y2 in top - 0.5f..bottom + 0.5f) drawLine(col, Offset(left, y2), Offset(right, y2), sw)
            }
        }
        off += minorPx
        j++
    }

    // ---- Legend: show the major length in display units ----
    val pad  = 8f
    val barH = 6f
    val barW = majorPx.coerceAtMost(right - left - 2 * pad)
    if (barW > 8f) {
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(right - barW - pad, top + pad),
            size = androidx.compose.ui.geometry.Size(barW, barH)
        )
        val legend = if (unit == UnitSystem.INCHES) {
            String.format("%.0f in", majorDisp) // integer by construction
        } else {
            String.format("%.0f mm", majorDisp) // integer by construction
        }
        val style = TextStyle(
            color = Color.Black,
            fontSize = 12.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
        val layoutText = textMeasurer.measure(AnnotatedString(legend), style)
        drawText(
            layoutText,
            topLeft = Offset(right - layoutText.size.width - pad, top + pad + barH + 6f)
        )
    }
}

/**
 * Snap a positive float to a *nice* **integer** near it using the {1,2,5}×10^k ladder.
 * Examples:
 *  - 19.7 → 20
 *  - 21.3 → 20 (closer to 20 than 25; ties round to the higher)
 *  - 3.6  → 4
 *  - 0.9  → 1
 */
private fun snapNiceInteger(x: Float): Float {
    if (x <= 1f) return 1f
    val log10 = kotlin.math.log10(x)
    val k = floor(log10).toInt()
    val mag = 10f.pow(k)           // magnitude (…1, 10, 100,…)
    val n = x / mag                // normalized to [1,10)

    // Candidate bases in [1,10) multiplied by mag → {1, 2, 5}×mag
    val c1 = 1f * mag
    val c2 = 2f * mag
    val c5 = 5f * mag
    val candidates = listOf(c1, c2, c5, 10f * mag)

    // Pick nearest; if exactly halfway, prefer the larger (cleaner/roomier)
    val best = candidates.minBy { kotlin.math.abs(it - x) }
    // Ensure it's an integer in display units: round to nearest integer.
    return best.roundToInt().toFloat().coerceAtLeast(1f)
}
