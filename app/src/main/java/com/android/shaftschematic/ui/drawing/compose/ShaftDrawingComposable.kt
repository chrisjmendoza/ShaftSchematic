package com.android.shaftschematic.ui.drawing.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer

/**
 * Compose wrapper that renders the shaft drawing using ShaftLayout + ShaftRenderer.
 *
 * Call it from your screen:
 *   ShaftDrawing(spec = spec, opts = opts, modifier = Modifier.fillMaxWidth().height(300.dp))
 */
@Composable
fun ShaftDrawing(
    spec: ShaftSpecMm,
    opts: RenderOptions,
    modifier: Modifier = Modifier.fillMaxWidth().height(300.dp)
) {
    val density = LocalDensity.current
    val renderer = remember { ShaftRenderer() }

    Canvas(modifier = modifier) {
        // Canvas size in PX
        val sizePx = Size(size.width, size.height)

        // Convert Compose px (Float) to Int for layout
        val widthPx = sizePx.width.toInt()
        val heightPx = sizePx.height.toInt()

        // Compute layout for current canvas size
        val layout = ShaftLayout.compute(
            spec = spec,
            targetWidthPx = widthPx,
            maxHeightPx = heightPx,
            paddingPx = opts.paddingPx
        )

        // Use the Android native canvas for the existing renderer
        drawIntoCanvas { canvas ->
            renderer.render(canvas.nativeCanvas, layout, opts)
        }
    }
}
