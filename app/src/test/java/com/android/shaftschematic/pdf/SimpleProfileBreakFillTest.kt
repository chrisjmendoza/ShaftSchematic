package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The wear/undercut sheets' simple profile shades a broken body run as two stubs bounded by the
 * break's own S — not as one rectangle across the whole run.
 *
 * This sheet paints ALL its shade fills in a pre-pass under ALL its outlines (so a liner's shade
 * lands over a body's), which is why its break geometry is derived once and read by both passes.
 * Before that, the pre-pass knew nothing about breaks: it laid grey straight through the gap that
 * is meant to read as bare paper, and squared the fill off at both stub ends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SimpleProfileBreakFillTest {

    /** See `BreakEdgeFillSeamTest.warmNativeGraphics` — a bare Path first would kill the JVM. */
    @Before
    fun warmNativeGraphics() {
        Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
    }

    private val w = 500
    private val h = 200
    private val cy = 100f
    private val strokePt = 1.5f

    /** 400 mm at 1 pt/mm — past COMPRESS_TRIGGER_PT, so the run breaks at the midpoint. */
    private fun render(shaded: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = strokePt; color = Color.BLACK
        }
        val fill = if (!shaded) null else Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.argb(40, 0, 0, 0)
        }
        drawSimpleShaftProfile(
            c = c,
            spec = ShaftSpec(
                overallLengthMm = 400f,
                bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 80f)),
            ),
            cy = cy,
            outline = outline,
            geomRect = RectF(0f, 0f, w.toFloat(), h.toFloat()),
            xAt = { it },
            rPx = { dia -> dia / 2f },
            bodyFill = fill,
            taperFill = null,
            linerFill = null,
            ptPerMm = 1f,
            dimStrokeWidthPt = 0.8f,
        )
        return bmp
    }

    /** The layout this fixture produces, from the real math rather than pinned numbers. */
    private val pair = breakPairLayout(
        runLenPt = 400f,
        desiredAmplitudePt = 40f * 0.6f,
        classicGapPt = kotlin.math.min(ZIGZAG_GAP_MAX_PT, 0.25f * 400f),
        strokeWidthPt = strokePt,
    )
    private val leftEnd = 200f - pair.gapPt / 2f
    private val rightBeg = 200f + pair.gapPt / 2f

    /** x of the break S at height fraction [t] — `breakX + 3·amp·t(1−t)(1−2t)`. */
    private fun sX(breakX: Float, t: Float) =
        breakX + 3f * pair.amplitudePt * t * (1f - t) * (1f - 2f * t)

    private fun sY(t: Float) = cy - 40f + t * 80f

    private fun px(bmp: Bitmap, x: Float, y: Float) = bmp.getPixel(Math.round(x), Math.round(y))

    @Test
    fun `the break gap is bare paper, not shaded through`() {
        val bmp = render(shaded = true)
        // Dead centre of the gap: both S's cross the break line at mid height, and each eye
        // narrows to its tip there, so nothing but paper belongs at this pixel.
        assertEquals(Color.WHITE, px(bmp, 200f, cy))
    }

    @Test
    fun `each stub fills up to its own S`() {
        val bmp = render(shaded = true)
        val clear = 3f
        assertTrue(
            "aft stub must fill up to its S in the upper half",
            px(bmp, sX(leftEnd, 0.1f) - clear, sY(0.1f)) != Color.WHITE,
        )
        assertTrue(
            "fwd stub must fill up to its S in the lower half",
            px(bmp, sX(rightBeg, 0.9f) + clear, sY(0.9f)) != Color.WHITE,
        )
        assertEquals(
            "no shade may spill past the aft S",
            Color.WHITE, px(bmp, sX(leftEnd, 0.1f) + clear, sY(0.1f)),
        )
        assertEquals(
            "no shade may spill past the fwd S",
            Color.WHITE, px(bmp, sX(rightBeg, 0.9f) - clear, sY(0.9f)),
        )
        // …and the body still shades everywhere away from the break.
        listOf(20f, 120f, 300f, 380f).forEach {
            assertTrue("body shade missing at x=$it", px(bmp, it, cy) != Color.WHITE)
        }
    }

    @Test
    fun `the unshaded sheet is untouched`() {
        // No fill paint, no fill work: the outlines and the glyph are all that print.
        val bmp = render(shaded = false)
        assertEquals(Color.WHITE, px(bmp, 120f, cy))
        assertEquals(Color.WHITE, px(bmp, 200f, cy))
        assertTrue("the top outline must still print", px(bmp, 120f, cy - 40f) != Color.WHITE)
    }
}
