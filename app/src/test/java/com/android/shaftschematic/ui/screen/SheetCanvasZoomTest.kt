package com.android.shaftschematic.ui.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Wear and Undercut overviews zoom (on-device report: a short liner was too small to tap).
 * A tap must land where the zoomed drawing shows it, and the drawing never leaves its box.
 */
class SheetCanvasZoomTest {

    private val box = IntSize(400, 200)

    @Test
    fun `at 1x a tap is where it lands`() {
        assertEquals(Offset(123f, 45f), sheetZoomToCanvas(Offset(123f, 45f), box, 1f, Offset.Zero))
    }

    @Test
    fun `a zoomed tap maps back through the centre pivot and the pan`() {
        // At 2x about the centre (200, 100), canvas x 250 draws at 300; panned 40 left it draws at 260.
        val canvas = sheetZoomToCanvas(Offset(260f, 100f), box, 2f, Offset(-40f, 0f))
        assertEquals(250f, canvas.x, 1e-4f)
        assertEquals(100f, canvas.y, 1e-4f)
    }

    @Test
    fun `the pan is held inside the overhang and none at 1x`() {
        assertEquals(Offset.Zero, clampedSheetZoomOffset(Offset(50f, 50f), 1f, box))
        // 3x overhangs by (3-1) * size / 2 each way.
        assertEquals(Offset(400f, -200f), clampedSheetZoomOffset(Offset(900f, -900f), 3f, box))
        assertEquals(Offset(10f, 20f), clampedSheetZoomOffset(Offset(10f, 20f), 3f, box))
    }

    @Test
    fun `pinching scales within 1x to the cap, and a one-finger pan waits for a zoom`() {
        val zoom = SheetCanvasZoom()
        zoom.onBoxSize(box)
        assertFalse(zoom.isZoomed)
        zoom.transform(0.5f, Offset.Zero)
        assertEquals(1f, zoom.scale, 0f)
        zoom.transform(2f, Offset(30f, 0f))
        assertTrue(zoom.isZoomed)
        assertEquals(Offset(30f, 0f), zoom.offset)
        zoom.transform(100f, Offset.Zero)
        assertEquals(SheetCanvasZoom.MAX_SCALE, zoom.scale, 0f)
        // Back to 1x drops the pan, so the whole drawing is in the box again.
        zoom.transform(0.01f, Offset.Zero)
        assertEquals(Offset.Zero, zoom.offset)
    }
}
