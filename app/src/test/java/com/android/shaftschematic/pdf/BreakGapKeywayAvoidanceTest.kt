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

    private val strokePt = 1.5f

    /** ONE implementation serves both composers (`drawBodyRunsWithBreaks`). */
    private fun render(avoid: List<KeywaySpan>, fill: Paint? = null): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = strokePt; color = Color.BLACK
        }
        drawBodyRunsWithBreaks(
            c, listOf(longBody()), cy, { it }, { dia -> dia / 2f }, outline,
            RectF(0f, 0f, w.toFloat(), h.toFloat()), fill = fill,
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

    // ── A SHADED broken run fills to the S, not to the break line ─────────────
    //
    // The stubs used to fill as axis-aligned rectangles terminating on a straight vertical at
    // the break x, while the glyph strokes a cubic that leaves that vertical by √3/6·amplitude
    // with OPPOSITE sign in its two halves. That left a white crescent inside the outline in
    // one half of every stub and pushed grey past the curve in the other (on-device report:
    // body shading does not fill cleanly at the S breaks).

    /** The break pair this fixture lays out — recomputed, never hard-coded, from the real math. */
    private data class Gap(val leftEnd: Float, val rightBeg: Float, val amp: Float)

    private fun gapOf(avoid: List<KeywaySpan>): Gap {
        val r = 40f                              // Ø80 at rPx = dia/2
        val runPt = 400f                         // 400 mm at 1 pt/mm
        val pair = breakPairLayout(
            runLenPt = runPt,
            desiredAmplitudePt = r * 0.6f,
            classicGapPt = kotlin.math.min(ZIGZAG_GAP_MAX_PT, 0.25f * runPt),
            strokeWidthPt = strokePt,
        )
        val center = breakGapCenter(0f, runPt, pair.gapPt, avoid.map { it.loMm..it.hiMm })!!
        return Gap(center - pair.gapPt / 2f, center + pair.gapPt / 2f, pair.amplitudePt)
    }

    /**
     * x of the break S at height fraction [t] of the shaft. The cubic's y term collapses to
     * `yTop + t·h` exactly, so t is both the Bézier parameter and the height fraction, and x
     * is `breakX + 3·amp·t(1−t)(1−2t)` — positive in the top half, negative in the bottom.
     */
    private fun sX(breakX: Float, amp: Float, t: Float) =
        breakX + 3f * amp * t * (1f - t) * (1f - 2f * t)

    /** Shaft top = cy − 40, bottom = cy + 40, so height fraction [t] is this y. */
    private fun sY(t: Float) = cy - 40f + t * 80f

    private fun shadeFill() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(40, 0, 0, 0)
    }

    private fun px(bmp: Bitmap, x: Float, y: Float) =
        bmp.getPixel(Math.round(x), Math.round(y))

    /**
     * Sample offsets are 3 px clear of every curve in the fixture, so no antialiasing fringe and
     * no eye lobe can decide an assertion. The eye sits entirely on the VOID side of its own S
     * (it is bounded by that S's half-lobe and the return sweep), so a body-side sample is always
     * clean; the two bare-paper samples are placed in the half where the near stub's eye is not.
     */
    private val clearPx = 3f

    @Test
    fun `a shaded stub fills up to the S, both halves`() {
        val bmp = render(emptyList(), fill = shadeFill())
        val g = gapOf(emptyList())
        // Upper half: the left stub's S bows INTO the gap, so the fill must reach past the break
        // line — a rectangle ending at leftEnd leaves this crescent white.
        assertTrue(
            "left stub must fill up to its S in the upper half",
            px(bmp, sX(g.leftEnd, g.amp, 0.1f) - clearPx, sY(0.1f)) != Color.WHITE,
        )
        // Lower half: the right stub's S bows into the gap the other way, same crescent mirrored.
        assertTrue(
            "right stub must fill up to its S in the lower half",
            px(bmp, sX(g.rightBeg, g.amp, 0.9f) + clearPx, sY(0.9f)) != Color.WHITE,
        )
        // …and the halves where the fill retreats behind the break line still carry body shade.
        assertTrue(
            "left stub stays filled in the lower half",
            px(bmp, sX(g.leftEnd, g.amp, 0.9f) - clearPx, sY(0.9f)) != Color.WHITE,
        )
        assertTrue(
            "right stub stays filled in the upper half",
            px(bmp, sX(g.rightBeg, g.amp, 0.1f) + clearPx, sY(0.1f)) != Color.WHITE,
        )
    }

    @Test
    fun `the break gap stays bare paper beside each S`() {
        val bmp = render(emptyList(), fill = shadeFill())
        val g = gapOf(emptyList())
        // Just past the left S in the upper half — the right stub's eye starts further right.
        assertEquals(
            "no shade may spill past the left S",
            Color.WHITE, px(bmp, sX(g.leftEnd, g.amp, 0.1f) + clearPx, sY(0.1f)),
        )
        // Just before the right S in the lower half — the left stub's eye ends further left.
        assertEquals(
            "no shade may spill past the right S",
            Color.WHITE, px(bmp, sX(g.rightBeg, g.amp, 0.9f) - clearPx, sY(0.9f)),
        )
    }

    @Test
    fun `a keyway-shifted gap fills to its S just the same`() {
        val avoid = listOf(KeywaySpan(150f, 250f))
        val bmp = render(avoid, fill = shadeFill())
        val g = gapOf(avoid)
        assertTrue("the fixture must actually shift the gap", kotlin.math.abs(g.leftEnd - 190f) > 1f)
        assertTrue(
            "left stub must fill up to its S in the upper half",
            px(bmp, sX(g.leftEnd, g.amp, 0.1f) - clearPx, sY(0.1f)) != Color.WHITE,
        )
        assertTrue(
            "right stub must fill up to its S in the lower half",
            px(bmp, sX(g.rightBeg, g.amp, 0.9f) + clearPx, sY(0.9f)) != Color.WHITE,
        )
        assertEquals(
            "no shade may spill past the left S",
            Color.WHITE, px(bmp, sX(g.leftEnd, g.amp, 0.1f) + clearPx, sY(0.1f)),
        )
    }

    @Test
    fun `an unbroken shaded run still fills as a plain rectangle`() {
        // The fallback path is untouched: no clear gap placement, so the whole run fills square.
        val bmp = render(listOf(KeywaySpan(0f, 400f)), fill = shadeFill())
        listOf(20f, 200f, 380f).forEach { x ->
            assertTrue("plain run must fill end to end at x=$x", px(bmp, x, cy) != Color.WHITE)
        }
    }
}
