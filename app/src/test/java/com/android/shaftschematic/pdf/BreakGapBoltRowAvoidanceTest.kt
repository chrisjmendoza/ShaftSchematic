package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.KeywaySpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The S-break gap steers clear of a coupler bolt-row window exactly as it does a keyway
 * window — the third avoid source beside keyway windows and seal areas. A break cut through
 * a hole row or a cross-bore put the paper gap where the bolts are (manual-figure report).
 * ONE draw implementation (`drawBodyRunsWithBreaks`) serves both composers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BreakGapBoltRowAvoidanceTest {

    private val w = 500
    private val h = 200
    private val cy = 100f
    private val strokePt = 1.5f

    /** 400 mm at 1 pt/mm = 400 pt of run — past the long-span trigger, so it breaks. */
    private fun longBody() = Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 80f)

    private fun render(boltWindows: List<KeywaySpan>, keyways: List<KeywaySpan> = emptyList()): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = strokePt; color = Color.BLACK
        }
        drawBodyRunsWithBreaks(
            c, listOf(longBody()), cy, { it }, { dia -> dia / 2f }, outline,
            RectF(0f, 0f, w.toFloat(), h.toFloat()),
            truePtPerMm = 1f,
            keywayAvoidSpansMm = keyways,
            boltRowAvoidSpansMm = boltWindows,
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
        assertTrue("the run must still break", topLineInk(bmp, 5, 395) < 388)
        assertEquals(
            "the gap must never cut the bolt-row window",
            winX1 - winX0, topLineInk(bmp, winX0, winX1),
        )
    }

    @Test
    fun `a bolt row over the middle shifts the gap rather than losing the break`() {
        assertBrokenOutsideWindow(render(listOf(KeywaySpan(150f, 250f))), 152, 248)
    }

    @Test
    fun `a bolt row at the coupling end leaves the centered gap alone`() {
        val bmp = render(listOf(KeywaySpan(300f, 400f)))
        assertBrokenOutsideWindow(bmp, 302, 398)
        assertTrue("centered gap", topLineInk(bmp, 195, 205) < 5)
    }

    @Test
    fun `bolt windows and keyway windows avoid together`() {
        // Keyway window over the middle, bolt window just AFT of where the gap would shift to.
        val bmp = render(boltWindows = listOf(KeywaySpan(100f, 140f)), keyways = listOf(KeywaySpan(150f, 250f)))
        assertBrokenOutsideWindow(bmp, 102, 138)
        assertEquals(98, topLineInk(bmp, 152, 250))
    }

    @Test
    fun `a window swallowing the run prints it plain rather than cut a hole`() {
        assertEquals(390, topLineInk(render(listOf(KeywaySpan(0f, 400f))), 5, 395))
    }
}
