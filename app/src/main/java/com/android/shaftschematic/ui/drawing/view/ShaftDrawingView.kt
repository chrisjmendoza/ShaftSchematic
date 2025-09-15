package com.android.shaftschematic.ui.drawing.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer

/**
 * Classic Android View that renders the shaft drawing.
 * Set [spec] and [opts], then call invalidate().
 */
class ShaftDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var spec: ShaftSpecMm? = null
        set(value) {
            field = value
            invalidate()
        }

    var opts: RenderOptions? = null
        set(value) {
            field = value
            invalidate()
        }

    private val renderer = ShaftRenderer()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = spec ?: return
        val o = opts ?: return

        // Guard against zero size
        if (width <= 0 || height <= 0) return

        val layout = ShaftLayout.compute(
            spec = s,
            targetWidthPx = width,
            maxHeightPx = height,
            paddingPx = o.paddingPx
        )

        renderer.render(canvas, layout, o)
    }
}
