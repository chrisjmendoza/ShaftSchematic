// file: com/android/shaftschematic/ui/drawing/compose/ShaftThumbnail.kt
package com.android.shaftschematic.ui.drawing.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.lastOccupiedEndMm
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.PreviewColorSetting

/**
 * ShaftThumbnail — a small, static rendering of a shaft.
 *
 * Same two calls as [ShaftDrawing] (`ShaftLayout.compute` → `ShaftRenderer.draw`) with
 * everything interactive removed: no pan, no zoom, no double-tap reset, no tap-to-select, no
 * grid, no highlight, no debug overlays. There is no second renderer and no duplicated drawing
 * math — a template preview is the editor's preview, sized down.
 *
 * Used by the template browser, where a `LazyColumn` composes only the visible cards, so an
 * unexpanded bucket costs nothing.
 */
@Composable
fun ShaftThumbnail(
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>,
    modifier: Modifier = Modifier.fillMaxSize(),
    outline: PreviewColorSetting = PreviewColorSetting(preset = PreviewColorPreset.STEEL),
    bodyFill: PreviewColorSetting = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
    taperFill: PreviewColorSetting = PreviewColorSetting(preset = PreviewColorPreset.STEEL),
    linerFill: PreviewColorSetting = PreviewColorSetting(preset = PreviewColorPreset.BRONZE),
    threadFill: PreviewColorSetting = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
    threadHatch: PreviewColorSetting = PreviewColorSetting(preset = PreviewColorPreset.STEEL),
) {
    val scheme = MaterialTheme.colorScheme

    fun fillAlpha(preset: PreviewColorPreset, fallback: Float): Float = when (preset) {
        PreviewColorPreset.TRANSPARENT -> 0f
        PreviewColorPreset.STEEL -> 0.18f
        PreviewColorPreset.STAINLESS -> 0.14f
        PreviewColorPreset.BRONZE -> 0.16f
        PreviewColorPreset.CUSTOM -> fallback
    }

    // No unit key: a thumbnail draws geometry only — no dimensions, no callouts, nothing the
    // display unit could change — so the render options do not depend on it.
    val options = remember(scheme, outline, bodyFill, taperFill, linerFill, threadFill, threadHatch) {
        RenderOptions(
            paddingPx = 8,
            outlineColor = outline.resolve(scheme).toArgb(),
            // Thinner than the editor preview: a thumbnail is a fraction of the width, and a
            // 2 px outline swallows a small shaft's shape.
            outlineWidthPx = 1.5f,
            bodyFillColor = bodyFill.resolve(scheme)
                .copy(alpha = fillAlpha(bodyFill.preset, 0.10f)).toArgb(),
            linerFillColor = linerFill.resolve(scheme)
                .copy(alpha = fillAlpha(linerFill.preset, 0.16f)).toArgb(),
            taperFillColor = taperFill.resolve(scheme)
                .copy(alpha = fillAlpha(taperFill.preset, 0.14f)).toArgb(),
            threadFillColor = threadFill.resolve(scheme)
                .copy(alpha = fillAlpha(threadFill.preset, 0.10f)).toArgb(),
            threadHatchColor = threadHatch.resolve(scheme).toArgb(),
            highlightEnabled = false,
        )
    }

    // Same preview-safe fallback the editor uses: a spec with parts but no authored OAL still
    // draws, extended to its last occupied end.
    val safeSpec = remember(spec) {
        if (spec.overallLengthMm <= 0f) {
            val end = spec.lastOccupiedEndMm()
            if (end > 0f) spec.copy(overallLengthMm = end) else spec
        } else spec
    }

    Canvas(modifier) {
        val padX = options.paddingPx.toFloat()
        val padY = 4f
        val layout = ShaftLayout.compute(
            spec = safeSpec,
            leftPx = padX,
            topPx = padY,
            rightPx = size.width - padX,
            bottomPx = size.height - padY,
            resolvedComponents = resolvedComponents.takeIf { it.isNotEmpty() },
        )
        with(ShaftRenderer) {
            draw(
                spec = safeSpec,
                layout = layout,
                opts = options,
                components = resolvedComponents.takeIf { it.isNotEmpty() },
            )
        }
    }
}
