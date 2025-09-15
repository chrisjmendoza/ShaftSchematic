package com.android.shaftschematic.ui.drawing.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.rememberTextMeasurer
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer

@OptIn(ExperimentalTextApi::class)
@Composable
fun ShaftDrawing(
    spec: ShaftSpecMm,
    opts: RenderOptions,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val pad = opts.paddingPx.toFloat()
        val left = pad
        val top = pad
        val right = (w - pad).coerceAtLeast(left + 1f)
        val bottom = (h - pad).coerceAtLeast(top + 1f)

        val layout = ShaftLayout.compute(spec, left, top, right, bottom)

        ShaftRenderer().run { render(layout, opts, textMeasurer) }
    }
}
