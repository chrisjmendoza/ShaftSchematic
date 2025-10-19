// file: com/android/shaftschematic/ui/drawing/compose/ShaftDrawing.kt
package com.android.shaftschematic.ui.drawing.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.drawing.render.GridRenderer.drawAdaptiveShaftGrid
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.drawing.render.ThreadStyle
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.launch

/**
 * ShaftDrawing
 *
 * Layer: UI → Compose
 *
 * Purpose
 * - Own a Canvas and call: ShaftLayout.compute → (optional) Grid → ShaftRenderer.draw.
 * - Bridge editor state (units, grid, **highlight** selection) into RenderOptions.
 * - Provide preview-safe behavior when overallLengthMm == 0 (still shows geometry).
 *
 * Contracts
 * - Geometry remains **mm** throughout; UnitSystem affects labels/grid only.
 * - Pure rendering: this composable does not mutate model state.
 * - Pan/zoom are view transforms only; renderer receives canonical layout bounds.
 *
 * Highlight
 * - When [highlightEnabled] and [highlightId] is non-null, we send a high-contrast preset
 *   (colored glow + white edge) via RenderOptions. If the renderer build doesn’t yet include
 *   highlight fields, these are simply ignored (no behavior change).
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun ShaftDrawing(
    spec: ShaftSpec,
    unit: UnitSystem,
    showGrid: Boolean,
    // Highlight bridge (safe defaults)
    highlightEnabled: Boolean = false,
    highlightId: Any? = null,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    // Stable text measurer (expensive object)
    val textMeasurer = rememberTextMeasurer()

    // Theme-derived highlight glow color (edge stays white for contrast)
    val themeGlow: Color = MaterialTheme.colorScheme.primary

    // RenderOptions (keep most defaults; set only what we actively control here)
    // NOTE: legacy color fields in RenderOptions are ARGB Ints → use toArgb().
    //       highlight colors are Color → pass Color directly.
    val options = RenderOptions(
        // Grid is drawn in this composable; renderer's internal grid stays off.
        showGrid = false,
        gridUseInches = (unit == UnitSystem.INCHES),

        // Visual tuning (retain prior look)
        paddingPx = 16,
        textSizePx = 22f,
        outlineColor = Color.Black.toArgb(),
        outlineWidthPx = 2f,
        bodyFillColor = Color.Transparent.toArgb(),
        linerFillColor = Color.Transparent.toArgb(),
        taperFillColor = Color.Black.copy(alpha = 0.10f).toArgb(),

        // Threads (preview uses legacy hatch style)
        threadStyle = ThreadStyle.HATCH,      // ← legacy look restored
        threadUseHatchColor = true,
        threadFillColor = Color.Transparent.toArgb(),
        threadHatchColor = Color.Black.toArgb(),
        threadStrokePx = 0f,

        // Highlight preset (obvious: colored glow + white edge)
        highlightEnabled = highlightEnabled,
        highlightId = highlightId,
        highlightGlowColor = themeGlow,
        highlightEdgeColor = Color.White,
        // slightly louder defaults so it’s unmistakable in testing
        highlightGlowAlpha = 0.75f,
        highlightEdgeAlpha = 1.0f,
        highlightGlowExtraPx = 12f,
        highlightEdgeExtraPx = 6f
    )

    // Preview-safe spec: if OAL is zero but parts exist, extend to last occupied end.
    val safeSpec = remember(spec) {
        val lastEnd = buildList {
            spec.bodies.maxOfOrNull { it.startFromAftMm + it.lengthMm }?.let(::add)
            spec.tapers.maxOfOrNull { it.startFromAftMm + it.lengthMm }?.let(::add)
            spec.liners.maxOfOrNull { it.startFromAftMm + it.lengthMm }?.let(::add)
            spec.threads.maxOfOrNull { it.startFromAftMm + it.lengthMm }?.let(::add)
        }.maxOrNull() ?: 0f
        if (spec.overallLengthMm <= 0f && lastEnd > 0f) spec.copy(overallLengthMm = lastEnd) else spec
    }

    // ──────────────────────────────
    // Pan / Zoom (UI only)
    // ──────────────────────────────
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    val gestures = Modifier
        .pointerInput(Unit) {
            detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                val old = scale.value
                val new = (old * zoom).coerceIn(0.5f, 4.0f)
                val z = if (old != 0f) new / old else 1f
                val newOffset = offset.value * z + centroid * (1f - z) + pan
                scope.launch {
                    if (new != old) scale.snapTo(new)
                    offset.snapTo(newOffset)
                }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = {
                    scope.launch {
                        scale.animateTo(1f, tween(140))
                        offset.animateTo(Offset.Zero, tween(140))
                    }
                }
            )
        }

    // ──────────────────────────────
    // Layout + Draw
    // ──────────────────────────────
    Box(modifier = modifier.then(gestures)) {
        Canvas(Modifier.fillMaxSize()) {
            val padX = options.paddingPx.toFloat()
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
                // Grid: beneath geometry; renderer receives showGrid=false
                if (showGrid) drawAdaptiveShaftGrid(layout = layout, unit = unit, targetMajorPx = 90f)

                // Geometry: renderer consumes highlight from options
                with(ShaftRenderer) {
                    draw(
                        spec = safeSpec,
                        layout = layout,
                        opts = options,
                        textMeasurer = textMeasurer
                    )
                }
            }
        }

        // Compact reset control (top-right)
        IconButton(
            onClick = {
                scope.launch {
                    scale.animateTo(1f, tween(140))
                    offset.animateTo(Offset.Zero, tween(140))
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Reset view",
                tint = Color.Black.copy(alpha = 0.8f)
            )
        }
    }
}
