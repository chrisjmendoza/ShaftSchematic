// file: com/android/shaftschematic/ui/drawing/compose/ShaftDrawing.kt
package com.android.shaftschematic.ui.drawing.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.android.shaftschematic.ui.config.DisplayCompressionConfig as DC
import kotlinx.coroutines.launch
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.drawing.render.GridRenderer.drawAdaptiveShaftGrid
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ThreadStyle
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * File: ShaftDrawing.kt
 * Layer: UI → Compose
 * Purpose: Compose wrapper that measures text, computes layout, draws optional grid,
 * and delegates actual shaft geometry to renderers.
 *
 * Responsibilities
 * • Own a Canvas and call: ShaftLayout.compute → (optional) Grid → ShaftRenderer.draw.
 * • Mediates unit settings (mm/in) only for labels; geometry remains mm throughout.
 * • Provide accessibility semantics (content descriptions for measurements when applicable).
 *
 * Data Flow
 * spec(Model, mm) → layout(pxPerMm, origin, rect) → grid/renderer (Canvas) →
 * overlays (badges, dimension labels via TextMeasurer).
 *
 * Notes
 * • This composable should not mutate model state; it is pure rendering.
 * • Keep allocations outside drawScope when possible (remember TextMeasurer).
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

    // One RenderOptions instance: grid drawn here (renderer.showGrid=false)
    val opts = remember(unit) {
        RenderOptions(
            showGrid = false,
            gridUseInches = (unit == UnitSystem.INCHES),
            paddingPx = 16,
            textSizePx = 22f,
            outlineColor = Color.Black.toArgb(),
            outlineWidthPx = 2f,
            bodyFillColor = Color.Transparent.toArgb(),
            linerFillColor = Color.Transparent.toArgb(),
            taperFillColor = Color.Black.copy(alpha = 0.10f).toArgb(),
            threadStyle = ThreadStyle.UNIFIED,
            threadUseHatchColor = true,
            threadFillColor = Color.Transparent.toArgb(),
            threadHatchColor = Color.Black.toArgb(),
            threadStrokePx = 0f
        )
    }

    // Preview-safe override: ensure something is visible even if overallLengthMm==0
    val safeSpec = remember(spec) {
        val lastEnd = listOfNotNull(
            spec.bodies.maxOfOrNull { it.startFromAftMm + it.lengthMm },
            spec.tapers.maxOfOrNull { it.startFromAftMm + it.lengthMm },
            spec.liners.maxOfOrNull { it.startFromAftMm + it.lengthMm },
            spec.threads.maxOfOrNull { it.startFromAftMm + it.lengthMm },
        ).maxOrNull() ?: 0f
        if (spec.overallLengthMm <= 0f && lastEnd > 0f) spec.copy(overallLengthMm = lastEnd) else spec
    }

    // ── UI-only Transform State (pan+zoom) ───────────────────────────────────
    val scope = rememberCoroutineScope()
    val MIN_SCALE = 0.5f
    val MAX_SCALE = 4.0f
    val RESET_MS = 140

    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // Combined gesture system: pinch/pan + double-tap
    val gestureMod = Modifier.pointerInput(Unit) {
        detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
            val oldScale = scale.value
            val unclamped = oldScale * zoom
            val newScale = unclamped.coerceIn(MIN_SCALE, MAX_SCALE)
            val z = if (oldScale != 0f) newScale / oldScale else 1f
            val newOffset = offset.value * z + centroid * (1f - z) + pan
            scope.launch {
                if (newScale != oldScale) scale.snapTo(newScale)
                offset.snapTo(newOffset)
            }
        }
    }.pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = {
                scope.launch {
                    scale.animateTo(1f, tween(RESET_MS))
                    offset.animateTo(Offset.Zero, tween(RESET_MS))
                }
            }
        )
    }

    // ── Layout + Draw, under transform ───────────────────────────────────────
    Box(modifier = modifier.then(gestureMod)) {
        Canvas(Modifier.fillMaxSize()) {
            val padX = opts.paddingPx.toFloat()
            val padY = 8f

            val layout = ShaftLayout.compute(
                spec = safeSpec,
                leftPx = padX,
                topPx = padY,
                rightPx = size.width - padX,
                bottomPx = size.height - padY
            )

            withTransform({
                translate(offset.value.x, offset.value.y)
                scale(scale.value, scale.value, pivot = Offset.Zero)
            }) {
                if (showGrid) {
                    drawAdaptiveShaftGrid(layout = layout, unit = unit, targetMajorPx = 90f)
                }
                with(ShaftRenderer) {
                    draw(spec = safeSpec, layout = layout, opts = opts, textMeasurer = textMeasurer)
                }
            }
        }

        // Compact Reset icon (translucent, top-right)
        IconButton(
            onClick = {
                scope.launch {
                    scale.animateTo(1f, tween(RESET_MS))
                    offset.animateTo(Offset.Zero, tween(RESET_MS))
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset view",
                tint = Color.Black.copy(alpha = 0.8f)
            )
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
    val targetRows = 10
    val rawMajorY = (bottom - top) / targetRows
    val majorGapY = rawMajorY.coerceAtLeast(12f)
    val minorGapY = majorGapY / 2f
    val showMinorY = minorGapY >= 6f

    fun drawHorizontals(step: Float, isMajor: Boolean) {
        val stroke = if (isMajor) 2f else 1f
        val color  = if (isMajor) Color(0x66000000) else Color(0x33000000)
        var y = midY
        while (y <= bottom + 0.5f) { drawLine(color, Offset(left, y), Offset(right, y), stroke) ; y += step }
        y = midY - step
        while (y >= top - 0.5f)   { drawLine(color, Offset(left, y), Offset(right, y), stroke) ; y -= step }
    }
    if (showMinorY) drawHorizontals(minorGapY, isMajor = false)
    drawHorizontals(majorGapY, isMajor = true)

    // --- Vertical majors: locked to x=0 origin in display units ---
    val spanMm = layout.maxXMm - layout.minXMm
    val spanDisp = (spanMm / mmPerUnit).coerceAtLeast(1f)
    val majDisp = chooseNiceIntegerNear(spanDisp / 10f).coerceAtLeast(1f)
    val minDisp = majDisp / 2f

    val majorGapX = majDisp * mmPerUnit * pxPerMm
    val minorGapX = minDisp * mmPerUnit * pxPerMm
    val showMinorX = minorGapX >= 6f

    val xAtZero = left + (0f - layout.minXMm) * pxPerMm

    fun drawVerticals(step: Float, isMajor: Boolean) {
        val stroke = if (isMajor) 2f else 1f
        val color  = if (isMajor) Color(0x66000000) else Color(0x33000000)
        var x = xAtZero
        while (x <= right + 0.5f) { drawLine(color, Offset(x, top), Offset(x, bottom), stroke); x += step }
        x = xAtZero - step
        while (x >= left - 0.5f)  { drawLine(color, Offset(x, top), Offset(x, bottom), stroke); x -= step }
    }
    if (showMinorX) drawVerticals(minorGapX, isMajor = false)
    drawVerticals(majorGapX, isMajor = true)

    // --- Labels: 0 at origin & center, integers outward on majors only ---
    val unitSuffix = if (unit == UnitSystem.INCHES) " in" else " mm"
    val normal = TextStyle(color = Color.Black, fontSize = 12.sp, platformStyle = PlatformTextStyle(false))
    val special = TextStyle(color = Color.Black, fontSize = 14.sp, platformStyle = PlatformTextStyle(false))

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

/** Choose a “nice” integer near x using {1,2,5}×10^k. */
private fun chooseNiceIntegerNear(x: Float): Float {
    if (x <= 0f) return 1f
    val k = kotlin.math.floor(log10(x.toDouble())).toInt()
    val mag = 10f.pow(k)
    val candidates = listOf(1f * mag, 2f * mag, 5f * mag, 10f * mag)
    val best = candidates.minBy { kotlin.math.abs(it - x) }
    return best.roundToInt().toFloat().coerceAtLeast(1f)
}
