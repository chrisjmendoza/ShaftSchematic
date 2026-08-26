package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.KeywaySpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A keyed body COMPRESSES AND BREAKS like any other run — only the keyway's own protected
 * window is off-limits to the break gap (on-device direction: a 95%-shaft body must keep
 * its break or a long shaft cannot render; the slot area alone must never compress).
 *
 * `breakGapCenter` places the gap: span midpoint by convention, shifted the minimal
 * distance that clears every protected window, plain-rect fallback only when nothing
 * clears. ONE draw implementation (`drawBodyRunsWithBreaks`) serves both composers, so one
 * render here covers both sheets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BreakGapKeywayAvoidanceTest {

    // ── The pure placement ────────────────────────────────────────────────────

    @Test
    fun `clear span - the gap stays centered`() {
        assertEquals(200f, breakGapCenter(0f, 400f, 20f, emptyList())!!, 1e-3f)
    }

    @Test
    fun `a window off to one side leaves the centered gap alone`() {
        assertEquals(200f, breakGapCenter(0f, 400f, 20f, listOf(280f..400f))!!, 1e-3f)
    }

    @Test
    fun `a window over the middle shifts the gap minimally off it`() {
        val c = breakGapCenter(0f, 400f, 20f, listOf(150f..250f))!!
        // Gap [c−10, c+10] must clear [150,250]; nearest legal centers are 140 or 260.
        assertTrue("gap must clear the window", c + 10f <= 150f + 1e-2f || c - 10f >= 250f - 1e-2f)
        assertTrue("shift must be minimal", kotlin.math.abs(c - 200f) <= 60f + 1e-2f)
    }

    @Test
    fun `a window swallowing the whole span yields no placement`() {
        assertNull(breakGapCenter(0f, 400f, 20f, listOf(0f..400f)))
    }

    @Test
    fun `a span too short for the gap yields no placement`() {
        assertNull(breakGapCenter(0f, 30f, 20f, emptyList()))
    }

    @Test
    fun `two windows - the gap lands in the clear lane between them`() {
        val c = breakGapCenter(0f, 400f, 20f, listOf(0f..120f, 260f..400f))
        assertNotNull(c)
        assertTrue(c!! - 10f >= 120f - 1e-2f && c + 10f <= 260f + 1e-2f)
    }

    // ── Both draw sites honor the placement ───────────────────────────────────

    private val w = 500
    private val h = 200
    private val cy = 100f

    /** 400 mm at 1 pt/mm = 400 pt of run — far past the 220 pt long-span trigger. */
    private fun longBody() = Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 80f)

    /** ONE implementation serves both composers (`drawBodyRunsWithBreaks`). */
    private fun render(avoid: List<KeywaySpan>, runout: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.BLACK
        }
        drawBodyRunsWithBreaks(
            c, listOf(longBody()), cy, { it }, { dia -> dia / 2f }, outline,
            RectF(0f, 0f, w.toFloat(), h.toFloat()),
            truePtPerMm = 1f, keywayAvoidSpansMm = avoid,
        )
        return bmp
    }

    /** Columns of the run's top edge (y ≈ cy − r = 60) carrying ink, over [x0,x1). */
    private fun topLineInk(bmp: Bitmap, x0: Int, x1: Int): Int {
        var n = 0
        for (x in x0 until x1) for (y in 57..63) {
            if (bmp.getPixel(x, y) != Color.WHITE) { n++; break }
        }
        return n
    }

    private fun assertBrokenOutsideWindow(bmp: Bitmap, winX0: Int, winX1: Int) {
        assertTrue(
            "the run must still break",
            topLineInk(bmp, 5, 395) < 388,
        )
        assertEquals(
            "the gap must never cut the protected window",
            winX1 - winX0, topLineInk(bmp, winX0, winX1),
        )
    }

    @Test
    fun `keyed long run still breaks, gap clear of an end window`() {
        assertBrokenOutsideWindow(render(listOf(KeywaySpan(280f, 400f))), 282, 398)
    }

    @Test
    fun `a mid-body window shifts the gap rather than losing the break`() {
        assertBrokenOutsideWindow(render(listOf(KeywaySpan(150f, 250f))), 152, 248)
    }

    @Test
    fun `no clear placement - the run prints plain rather than cut the window`() {
        val bmp = render(listOf(KeywaySpan(0f, 400f)))
        assertEquals(390, topLineInk(bmp, 5, 395))
    }

    @Test
    fun `no window - the classic centered break`() {
        val bmp = render(emptyList())
        assertTrue("centered gap", topLineInk(bmp, 195, 205) < 5)
    }
}
