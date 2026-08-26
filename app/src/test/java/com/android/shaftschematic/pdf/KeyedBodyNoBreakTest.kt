package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.model.Body
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A keyway-bearing body never carries the S-break — on either composer's body pass.
 *
 * The span is pinned at true width (`keywayPinnedBodySpans`), so foreshortening cannot reach
 * it; what these pin is the LONG-SPAN glyph (`COMPRESS_TRIGGER_PT`), given up deliberately for
 * keyed runs: a break gap could land inside the slot, and the slot must read as real geometry
 * end-to-end. The footer's compression note shares the exemption at its call site, so the note
 * and the drawn breaks cannot disagree.
 *
 * Detection: a broken run's top outline has a GAP at the run centre; a plain run's top line is
 * continuous. Both draw sites are asserted with the same probe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyedBodyNoBreakTest {

    private val w = 500
    private val h = 200
    private val cy = 100f

    /** 400 mm at 1 pt/mm = 400 pt of run — far past the 220 pt long-span trigger. */
    private fun longBody() = Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 80f)

    private fun renderSchematic(keyed: Boolean): Bitmap = render { c, outline ->
        drawBodiesCompressedCenterBreak(
            c, listOf(longBody()), cy, { it }, { dia -> dia / 2f }, outline,
            RectF(0f, 0f, w.toFloat(), h.toFloat()),
            truePtPerMm = 1f,
            keyedBodyIds = if (keyed) setOf("b1") else emptySet(),
        )
    }

    private fun renderRunout(keyed: Boolean): Bitmap = render { c, outline ->
        drawBodiesForRunout(
            c, listOf(longBody()), cy, { it }, { dia -> dia / 2f }, outline,
            RectF(0f, 0f, w.toFloat(), h.toFloat()),
            truePtPerMm = 1f,
            keyedBodyIds = if (keyed) setOf("b1") else emptySet(),
        )
    }

    private fun render(draw: (Canvas, Paint) -> Unit): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        draw(c, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.BLACK
        })
        return bmp
    }

    /** Ink on the run's top edge (y ≈ cy − r = 60) across the given x range. */
    private fun topLineInk(bmp: Bitmap, x0: Int, x1: Int): Int {
        var n = 0
        for (x in x0 until x1) for (y in 57..63) {
            if (bmp.getPixel(x, y) != Color.WHITE) { n++; break }
        }
        return n
    }

    @Test
    fun `schematic - unkeyed long run breaks at centre`() {
        // The classic gap tops out at ZIGZAG_GAP_MAX_PT (20): the centre must have a hole.
        assertTrue(topLineInk(renderSchematic(keyed = false), 195, 205) < 5)
    }

    @Test
    fun `schematic - keyed long run draws continuous`() {
        assertEquals(10, topLineInk(renderSchematic(keyed = true), 195, 205))
    }

    @Test
    fun `runout - unkeyed long run breaks at centre`() {
        assertTrue(topLineInk(renderRunout(keyed = false), 195, 205) < 5)
    }

    @Test
    fun `runout - keyed long run draws continuous`() {
        assertEquals(10, topLineInk(renderRunout(keyed = true), 195, 205))
    }

    /** The exemption keys off the BASE id, so a fragment (`"b1#2"`) of a keyed body is exempt too. */
    @Test
    fun `a keyed body's fragment id is exempt through its base id`() {
        val bmp = render { c, outline ->
            drawBodiesForRunout(
                c, listOf(longBody().copy(id = "b1#2")), cy, { it }, { dia -> dia / 2f }, outline,
                RectF(0f, 0f, w.toFloat(), h.toFloat()),
                truePtPerMm = 1f,
                keyedBodyIds = setOf("b1"),
            )
        }
        assertEquals(10, topLineInk(bmp, 195, 205))
    }
}
