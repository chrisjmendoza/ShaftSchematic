package com.android.shaftschematic.ui.drawing.view

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
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var spec: ShaftSpecMm? = null
        set(value) { field = value; invalidate() }

    var opts: RenderOptions = RenderOptions()
        set(value) { field = value; invalidate() }

    private val renderer = ShaftRenderer()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = spec ?: return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 2f || h <= 2f) return

        val pad = opts.paddingPx.toFloat()
        val left = pad
        val top = pad
        val right = (w - pad).coerceAtLeast(left + 1f)
        val bottom = (h - pad).coerceAtLeast(top + 1f)

        // IMPORTANT: positional args — matches ShaftLayout.compute signature
        val layout = ShaftLayout.compute(
            s,
            left,
            top,
            right,
            bottom
        )

        renderer.render(canvas, layout, opts)
    }
}
