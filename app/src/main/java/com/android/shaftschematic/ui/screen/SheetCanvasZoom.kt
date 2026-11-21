package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * Pinch-zoom for a whole-shaft tab canvas (the Wear and Undercut overviews; on-device report: a
 * short liner on a long shaft was too small to tap). The pattern of the Runout preview and the
 * two detail pages: the transform drives the Canvas `graphicsLayer` — scale about the centre
 * pivot, then translate — and a tap is mapped back through [toCanvas] before it is hit-tested.
 *
 * Unlike those, an overview sits in the tab's scrolling column on a phone, so a ONE-finger drag
 * pans only while zoomed in ([isZoomed]); at 1× it scrolls the page as it always did. The scale
 * runs 1×–[MAX_SCALE] (the whole drawing is already on screen at 1×), and the pan is held so the
 * drawing never leaves its box. Screen state only — nothing here reaches a document.
 */
@Stable
internal class SheetCanvasZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    private var boxSize by mutableStateOf(IntSize.Zero)

    val isZoomed: Boolean get() = scale > 1f

    val transformState = TransformableState { zoomChange, panChange, _ -> transform(zoomChange, panChange) }

    fun transform(zoomChange: Float, panChange: Offset) {
        scale = (scale * zoomChange).coerceIn(1f, MAX_SCALE)
        offset = clampedSheetZoomOffset(offset + panChange, scale, boxSize)
    }

    fun onBoxSize(size: IntSize) {
        boxSize = size
        offset = clampedSheetZoomOffset(offset, scale, size)
    }

    /** [raw] (a touch in the box) in the untransformed canvas the drawing was laid out in. */
    fun toCanvas(raw: Offset, size: IntSize): Offset = sheetZoomToCanvas(raw, size, scale, offset)

    companion object {
        const val MAX_SCALE = 6f
    }
}

@Composable
internal fun rememberSheetCanvasZoom(): SheetCanvasZoom = remember { SheetCanvasZoom() }

/** The gestures, on the box that owns the tap handler: pinch always, one-finger pan while zoomed. */
internal fun Modifier.sheetCanvasZoomGestures(zoom: SheetCanvasZoom): Modifier =
    onSizeChanged(zoom::onBoxSize).transformable(zoom.transformState, canPan = { zoom.isZoomed })

/** The transform, on the Canvas itself. */
internal fun Modifier.sheetCanvasZoomLayer(zoom: SheetCanvasZoom): Modifier =
    graphicsLayer(
        scaleX = zoom.scale,
        scaleY = zoom.scale,
        translationX = zoom.offset.x,
        translationY = zoom.offset.y,
    )

/**
 * The pan that keeps a drawing scaled by [scale] about its centre covering its [size] box: at
 * most half the overhang each way, none at 1×.
 */
internal fun clampedSheetZoomOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    if (scale <= 1f) return Offset.Zero
    val maxX = (scale - 1f) * size.width / 2f
    val maxY = (scale - 1f) * size.height / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/** Inverts the `graphicsLayer` transform: undo the translation, then the scale about the centre. */
internal fun sheetZoomToCanvas(raw: Offset, size: IntSize, scale: Float, offset: Offset): Offset {
    val pivotX = size.width / 2f
    val pivotY = size.height / 2f
    return Offset(
        (raw.x - offset.x - pivotX) / scale + pivotX,
        (raw.y - offset.y - pivotY) / scale + pivotY,
    )
}
