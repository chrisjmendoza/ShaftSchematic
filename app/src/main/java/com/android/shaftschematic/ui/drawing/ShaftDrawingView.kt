package com.android.shaftschematic.ui.drawing

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer

class ShaftDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var spec: ShaftSpecMm? = null
        set(value) { field = value; invalidate() }

    var options: RenderOptions = RenderOptions()
        set(value) { field = value; invalidate() }

    private val layoutEngine by lazy { ShaftLayout(resources.displayMetrics) }
    private val renderer by lazy { ShaftRenderer() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = spec ?: return
        val result = layoutEngine.layout(
            spec = s,
            canvasWidthPx = width,
            canvasHeightPx = height,
            opts = options
        )
        renderer.render(canvas, result, options)
    }
}
