// file: com/android/shaftschematic/ui/drawing/compose/ShaftDrawing.kt
package com.android.shaftschematic.ui.drawing.compose

import android.graphics.Paint
import android.util.Log
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.drawing.render.GridRenderer.drawAdaptiveShaftGrid
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.drawing.render.ThreadStyle
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.ResolvedThread
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.PreviewColorSetting
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.VerboseLog
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.util.concurrent.atomic.AtomicReference

// Developer instrumentation for preview hit-testing and selection arbitration.
// Enable only when debugging tap-to-select behavior.
private const val ENABLE_TAP_SELECT_DEBUG = false

private fun logTapSelect(message: String) {
    if (!ENABLE_TAP_SELECT_DEBUG) return
    Log.d("TapSelect", message)
}

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
    resolvedComponents: List<ResolvedComponent> = emptyList(),
    unit: UnitSystem,
    showGrid: Boolean,
    showLayoutDebugOverlay: Boolean = false,
    showOalMarkers: Boolean = false,
    blackWhiteOnly: Boolean = false,
    previewOutline: PreviewColorSetting = PreviewColorSetting(preset = PreviewColorPreset.STEEL),
    previewBodyFill: PreviewColorSetting = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
    previewTaperFill: PreviewColorSetting = PreviewColorSetting(preset = PreviewColorPreset.STEEL),
    previewLinerFill: PreviewColorSetting = PreviewColorSetting(preset = PreviewColorPreset.BRONZE),
    previewThreadFill: PreviewColorSetting = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
    previewThreadHatch: PreviewColorSetting = PreviewColorSetting(preset = PreviewColorPreset.STEEL),
    // Highlight bridge (safe defaults)
    highlightEnabled: Boolean = false,
    highlightId: Any? = null,
    onTapComponentId: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    // Stable text measurer (expensive object)
    val textMeasurer = rememberTextMeasurer()

    // Theme-derived highlight glow color (edge stays white for contrast)
    val themeGlow: Color = MaterialTheme.colorScheme.primary
    val debugMarkerColor: Color = MaterialTheme.colorScheme.error

    val previewScheme = MaterialTheme.colorScheme
    val outlineColor = if (blackWhiteOnly) Color.Black else previewOutline.resolve(previewScheme)
    val bodyFill = if (blackWhiteOnly) Color.Transparent else previewBodyFill.resolve(previewScheme)
    val taperFill = if (blackWhiteOnly) Color.Transparent else previewTaperFill.resolve(previewScheme)
    val linerFill = if (blackWhiteOnly) Color.Transparent else previewLinerFill.resolve(previewScheme)
    val threadFill = if (blackWhiteOnly) Color.Transparent else previewThreadFill.resolve(previewScheme)
    val threadHatch = if (blackWhiteOnly) Color.Black else previewThreadHatch.resolve(previewScheme)

    fun fillAlpha(preset: PreviewColorPreset, fallback: Float): Float = when (preset) {
        PreviewColorPreset.TRANSPARENT -> 0f
        PreviewColorPreset.STEEL -> 0.18f
        PreviewColorPreset.STAINLESS -> 0.14f
        PreviewColorPreset.BRONZE -> 0.16f
        PreviewColorPreset.CUSTOM -> fallback
    }

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
        outlineColor = outlineColor.toArgb(),
        outlineWidthPx = 2f,
        bodyFillColor = bodyFill.copy(alpha = fillAlpha(previewBodyFill.preset, fallback = 0.10f)).toArgb(),
        linerFillColor = linerFill.copy(alpha = fillAlpha(previewLinerFill.preset, fallback = 0.16f)).toArgb(),
        taperFillColor = taperFill.copy(alpha = fillAlpha(previewTaperFill.preset, fallback = 0.14f)).toArgb(),

        // Threads (preview uses legacy hatch style)
        threadStyle = ThreadStyle.HATCH,      // ← legacy look restored
        threadUseHatchColor = true,
        threadFillColor = threadFill.copy(alpha = fillAlpha(previewThreadFill.preset, fallback = 0.10f)).toArgb(),
        threadHatchColor = threadHatch.toArgb(),
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
    var gestureMoved by remember { mutableStateOf(false) }
    var gestureScaled by remember { mutableStateOf(false) }

    val latestLayoutRef = remember { AtomicReference<ShaftLayout.Result?>(null) }
    val latestComponentsState = rememberUpdatedState(resolvedComponents)
    val latestOnTapState = rememberUpdatedState(onTapComponentId)

    val gestures = Modifier
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press) {
                        gestureMoved = false
                        gestureScaled = false
                    }
                }
            }
        }
        .pointerInput(Unit) {
            detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                if (pan.getDistance() > 5f) gestureMoved = true
                if (abs(zoom - 1f) > 0.01f) gestureScaled = true
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
                onTap = { pos ->
                    if (gestureMoved || gestureScaled) {
                        logTapSelect("tap ignored: moved=$gestureMoved scaled=$gestureScaled")
                        return@detectTapGestures
                    }
                    val layout = latestLayoutRef.get() ?: return@detectTapGestures
                    val s = scale.value
                    if (s == 0f) return@detectTapGestures
                    val localX = (pos.x - offset.value.x) / s
                    val tappedMm = layout.xMmFromPx(localX)
                    val components = latestComponentsState.value
                    val onTap = latestOnTapState.value
                    var hitId: String? = null
                    components.filterIsInstance<ResolvedBody>().forEach { comp ->
                        if (tappedMm >= comp.startMmPhysical && tappedMm < comp.endMmPhysical) hitId = comp.authoredSourceId
                    }
                    components.filterIsInstance<ResolvedTaper>().forEach { comp ->
                        if (tappedMm >= comp.startMmPhysical && tappedMm < comp.endMmPhysical) hitId = comp.authoredSourceId
                    }
                    components.filterIsInstance<ResolvedThread>().forEach { comp ->
                        if (tappedMm >= comp.startMmPhysical && tappedMm < comp.endMmPhysical) hitId = comp.authoredSourceId
                    }
                    components.filterIsInstance<ResolvedLiner>().forEach { comp ->
                        if (tappedMm >= comp.startMmPhysical && tappedMm < comp.endMmPhysical) hitId = comp.authoredSourceId
                    }
                    logTapSelect(
                        "tapPx=${pos.x}, tapMm=$tappedMm, scale=${scale.value}, offsetX=${offset.value.x}, selected=$hitId"
                    )
                    if (hitId != null) onTap?.invoke(hitId)
                },
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
        val lastLayoutDbg = remember { arrayOfNulls<String>(1) }
        val lastOalWinDbg = remember { arrayOfNulls<String>(1) }

        Canvas(Modifier.fillMaxSize()) {
            val padX = options.paddingPx.toFloat()
            val padY = 8f

            val layout = ShaftLayout.compute(
                spec = safeSpec,
                leftPx = padX,
                topPx = padY,
                rightPx = size.width - padX,
                bottomPx = size.height - padY,
                resolvedComponents = resolvedComponents.takeIf { it.isNotEmpty() }
            )
            latestLayoutRef.set(layout)

            val dbg = layout.dbg()
            if (VerboseLog.isEnabled(VerboseLog.Category.RENDER) && lastLayoutDbg[0] != dbg) {
                lastLayoutDbg[0] = dbg
                VerboseLog.d(VerboseLog.Category.RENDER, "ShaftDrawing") { "layout: $dbg" }
            }

            withTransform({
                translate(offset.value.x, offset.value.y)
                scale(scale.value, scale.value, pivot = Offset.Zero)
            }) {
                // Grid: beneath geometry; renderer receives showGrid=false
                if (showGrid) {
                    val extraW = size.width
                    val extraH = size.height
                    drawAdaptiveShaftGrid(
                        layout = layout,
                        unit = unit,
                        targetMajorPx = 90f,
                        drawLeftPx = -extraW,
                        drawTopPx = -extraH,
                        drawRightPx = size.width + extraW,
                        drawBottomPx = size.height + extraH
                    )
                }

                // Geometry: renderer consumes highlight from options
                with(ShaftRenderer) {
                    draw(
                        spec = safeSpec,
                        layout = layout,
                        opts = options,
                        textMeasurer = textMeasurer,
                        components = resolvedComponents.takeIf { it.isNotEmpty() }
                    )
                }

                val wantOal = showOalMarkers || VerboseLog.isEnabled(VerboseLog.Category.OAL)
                if (wantOal) {
                    val win = computeOalWindow(safeSpec)
                    if (VerboseLog.isEnabled(VerboseLog.Category.OAL)) {
                        val winDbg = "oalWindow: startMm=${"%.3f".format(win.measureStartMm)} endMm=${"%.3f".format(win.measureEndMm)} oalMm=${"%.3f".format(win.oalMm)}"
                        if (lastOalWinDbg[0] != winDbg) {
                            lastOalWinDbg[0] = winDbg
                            VerboseLog.d(VerboseLog.Category.OAL, "ShaftDrawing") { winDbg }
                        }
                    }

                    if (showOalMarkers) {
                        val xStart = layout.xPx(win.measureStartMm.toFloat())
                        val xEnd = layout.xPx(win.measureEndMm.toFloat())
                        val stroke = (2f / scale.value).coerceAtLeast(0.5f)
                        drawLine(
                            color = debugMarkerColor,
                            start = Offset(xStart, layout.contentTopPx),
                            end = Offset(xStart, layout.contentBottomPx),
                            strokeWidth = stroke
                        )
                        drawLine(
                            color = debugMarkerColor,
                            start = Offset(xEnd, layout.contentTopPx),
                            end = Offset(xEnd, layout.contentBottomPx),
                            strokeWidth = stroke
                        )
                    }
                }
            }

            if (showLayoutDebugOverlay) {
                val p = Paint().apply {
                    isAntiAlias = true
                    color = Color.Black.toArgb()
                    textSize = 28f
                }
                drawContext.canvas.nativeCanvas.drawText(layout.dbg(), 12f, 32f, p)
                drawContext.canvas.nativeCanvas.drawText(
                    "zoom=${"%.2f".format(scale.value)} pan=(${"%.0f".format(offset.value.x)},${"%.0f".format(offset.value.y)})",
                    12f,
                    64f,
                    p
                )
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
