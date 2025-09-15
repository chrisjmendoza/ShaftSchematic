package com.android.shaftschematic.ui.drawing.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer

@Composable
fun ShaftDrawing(
    spec: ShaftSpecMm,
    opts: RenderOptions,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val pad = opts.paddingPx.toFloat()
        val left = pad
        val top = pad
        val right = (w - pad).coerceAtLeast(left + 1f)
        val bottom = (h - pad).coerceAtLeast(top + 1f)

        // Safely recompute layout when inputs change
        val layout = remember(spec, w, h, opts.paddingPx) {
            ShaftLayout.compute(
                spec,
                left,
                top,
                right,
                bottom
            )
        }

        drawIntoCanvas { c ->
            val renderer = ShaftRenderer()
            renderer.render(
                canvas = c.nativeCanvas,
                layout = layout,
                opts = opts
            )
        }
    }
}
