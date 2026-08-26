package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.KeywayClocking
import com.android.shaftschematic.model.LinerAuthoredReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `drawBodyKeywaysPdf` — the ONE body-keyway pass, called by the schematic composer and by the
 * runout/consolidated sheet. The sheet used to have no body-keyway pass at all: a keyway
 * authored on a body printed on the schematic and was missing from the runout sheet
 * (on-device report), even though that sheet already pins the body at true width for it.
 *
 * Renders to a bitmap and asserts the slot is really inked, so the pass cannot be silently
 * dropped from either sheet again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BodyKeywayDrawTest {

    private val w = 400
    private val h = 200
    private val cy = 100f

    /** Identity map: 1 pt per mm, so a 20 mm keyway is 20 pt of slot. */
    private val xAt: (Float) -> Float = { mm -> mm }

    private fun keyedBody(
        offsetFromEndMm: Float = 0f,
        end: LinerAuthoredReference = LinerAuthoredReference.AFT,
    ) = Body(
        id = "b1",
        startFromAftMm = 0f,
        lengthMm = 300f,
        diaMm = 100f,
        keywayWidthMm = 20f,
        keywayDepthMm = 8f,
        keywayLengthMm = 100f,
        keywayOffsetFromEndMm = offsetFromEndMm,
        keywayEnd = end,
    )

    private fun render(bodies: List<Body>, clocking: KeywayClocking = KeywayClocking.NONE, hidden: Set<String> = emptySet()): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.BLACK
        }
        drawBodyKeywaysPdf(c, bodies, xAt, cy, 1f, outline, clocking, hidden, emptySet())
        return bmp
    }

    /** Count inked (non-white) pixels inside a box. */
    private fun ink(bmp: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        var n = 0
        for (x in x0 until x1) for (y in y0 until y1) {
            if (bmp.getPixel(x, y) != Color.WHITE) n++
        }
        return n
    }

    @Test
    fun `an aft-referenced open keyway inks a slot at the aft end of the body`() {
        val bmp = render(listOf(keyedBody()))
        // Slot walls sit at cy ± halfW (20 mm wide → ±10 pt) over the keyway's 100 mm run.
        assertTrue("the slot must be drawn", ink(bmp, 0, 85, 105, 115) > 0)
        // Nothing is drawn beyond the keyway's own length — the rest of the body is the
        // body pass's business, not this one's.
        assertEquals(0, ink(bmp, 150, 0, w, h))
    }

    @Test
    fun `a body without a keyway draws nothing`() {
        val bmp = render(listOf(Body(id = "b2", startFromAftMm = 0f, lengthMm = 300f, diaMm = 100f)))
        assertEquals(0, ink(bmp, 0, 0, w, h))
    }

    @Test
    fun `a floating keyway inks a slot inset from the face`() {
        val bmp = render(listOf(keyedBody(offsetFromEndMm = 50f)))
        // Inset by 50 mm: the face end stays clear, the slot lands beyond it.
        assertEquals(0, ink(bmp, 0, 0, 45, h))
        assertTrue(ink(bmp, 50, 85, 155, 115) > 0)
    }

    @Test
    fun `a fwd-referenced keyway inks a slot at the fwd end`() {
        val bmp = render(listOf(keyedBody(end = LinerAuthoredReference.FWD)))
        // Measured from the FWD face (300 mm), so the slot runs back toward 200 mm.
        assertTrue(ink(bmp, 200, 85, 300, 115) > 0)
        assertEquals(0, ink(bmp, 0, 0, 190, h))
    }

    /**
     * Under a compressed x-map the slot keeps TRUE scale: its pinned window maps linearly at
     * the diameter scale, and the draw derives pt/mm from the slot's OWN mapped span — a
     * body-average pt/mm would shrink the slot inside the very window pinned to keep it real.
     */
    @Test
    fun `a compressed body still draws its keyway at true scale`() {
        // FWD-open 100 mm keyway on a 300 mm body → slot [200,300]. The map compresses
        // [0,200] to half scale and keeps [200,300] true: slot must draw exactly [100,200].
        val map: (Float) -> Float = { mm -> if (mm <= 200f) mm * 0.5f else 100f + (mm - 200f) }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.BLACK
        }
        drawBodyKeywaysPdf(
            c, listOf(keyedBody(end = LinerAuthoredReference.FWD)), map, cy, 1f, outline,
        )
        assertTrue("slot inked over its true-scale mapped span", ink(bmp, 105, 85, 195, 115) > 0)
        assertTrue("slot walls at TRUE half-width (±10), not the body average", ink(bmp, 105, 88, 195, 92) > 0)
        assertEquals("no slot ink in the compressed region", 0, ink(bmp, 0, 0, 95, h))
        assertEquals("no slot ink beyond the fwd face", 0, ink(bmp, 205, 0, w, h))
    }

    /** A 180° secondary host is a far-side feature: dashed outline, no white void fill. */
    @Test
    fun `a hidden keyway still draws - dashed, and less ink than a near-side slot`() {
        val near = render(listOf(keyedBody()))
        val far = render(listOf(keyedBody()), clocking = KeywayClocking.DEG_180, hidden = setOf("b1"))
        val nearInk = ink(near, 0, 85, 105, 115)
        val farInk = ink(far, 0, 85, 105, 115)
        assertTrue("a hidden keyway is still drawn", farInk > 0)
        assertTrue("a dashed far-side slot inks less than a solid one", farInk < nearInk)
    }
}
