// file: com/android/shaftschematic/ui/drawing/compose/ShaftDrawing.kt
package com.android.shaftschematic.ui.drawing.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.rememberTextMeasurer
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.util.UnitSystem

/**
 * ShaftDrawing
 *
 * Preview renderer using the shared layout/renderer pipeline:
 *  - Geometry is always canonical (mm) inside [ShaftSpec].
 *  - [unit] controls labels and grid legend (inches vs mm).
 *  - [showGrid] toggles the engineering grid underlay.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun ShaftDrawing(
    spec: ShaftSpec,
    unit: UnitSystem,
    showGrid: Boolean,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Build render options from current state.
    val opts = remember(unit, showGrid) {
        RenderOptions(
            showGrid = showGrid,
            gridUseInches = (unit == UnitSystem.INCHES),
        )
    }

    Canvas(modifier = modifier) {
        // Fit mm → px mapping into current canvas bounds
        val layout = ShaftLayout.compute(
            spec = spec,
            leftPx = 0f,
            topPx = 0f,
            rightPx = size.width,
            bottomPx = size.height
        )

        // Crisp white background under grid/lines
        drawRect(Color.White)

        // Delegate to the DrawScope renderer
        ShaftRenderer.drawOn(
            scope = this,              // Canvas' DrawScope
            layout = layout,
            opts = opts,
            textMeasurer = textMeasurer
        )
    }
}
