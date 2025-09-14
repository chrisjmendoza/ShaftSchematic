package com.android.shaftschematic.ui.drawing.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.ui.drawing.ShaftDrawingView
import com.android.shaftschematic.ui.drawing.render.RenderOptions

@Composable
fun ShaftDrawing(
    spec: ShaftSpecMm,
    opts: RenderOptions,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> ShaftDrawingView(ctx) },
        update = { v ->
            v.spec = spec
            v.options = opts
        }
    )
}
