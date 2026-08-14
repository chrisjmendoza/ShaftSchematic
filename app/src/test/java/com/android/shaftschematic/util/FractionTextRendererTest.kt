package com.android.shaftschematic.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Paint/Canvas half of the fraction typography system.
 *
 * Two things are worth pinning here and nowhere else:
 * 1. **Measure and draw agree.** Every rail seats its value by centring on the measured width;
 *    if the measurement drifted from the ink the value would sit off-centre in its break.
 * 2. **A built-up fraction stays inside the line box.** That is the property that let every
 *    vertical budget in the app — rail bands, footer line pitch, strip rows — stay untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FractionTextRendererTest {

    private fun paint(size: Float = 24f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = Color.BLACK
    }

    /**
     * The stacked preset by name, never [FractionTextStyle.Default]: the shipped default is a
     * product decision that has already moved once, and the draw-site default is
     * [FractionTypography.active], which the Settings toggle moves at runtime. These assertions
     * are about the stacked construction itself.
     */
    private val stacked = FractionTextStyle.Stacked

    @Test
    fun `text with no fraction measures exactly as plain text`() {
        val p = paint()
        assertEquals(p.measureText("Ø 5.125\""), p.measureRichText("Ø 5.125\"", stacked), 0.001f)
    }

    /**
     * The whole point of measuring rich: a stack is one digit column wide, not two plus a
     * slash. A rail that reserved the plain width would cut too big a break.
     */
    @Test
    fun `a built-up fraction is narrower than the same characters inline`() {
        val p = paint()
        assertTrue(p.measureRichText("3/16", stacked) < p.measureText("3/16"))
        assertTrue(p.measureRichText("399/4000", stacked) < p.measureText("399/4000"))
    }

    @Test
    fun `a wide denominator still measures sanely`() {
        val p = paint()
        val w = p.measureRichText("399/4000", stacked)
        // At most the wider digit row (4 digits at 0.6 scale) plus its bar overhang and bearings.
        assertTrue("w=$w", w > 0f && w < p.measureText("4000"))
    }

    /** Ink must land inside the advance the caller reserved — nothing spills into a neighbour. */
    @Test
    fun `stacked ink stays within the measured advance and the font's line box`() {
        val p = paint(40f)
        val text = "1 3/16\""
        val w = p.measureRichText(text, stacked)
        val fm = p.fontMetrics

        val padX = 20f
        val baseline = 100f
        val bmp = Bitmap.createBitmap((w + padX * 2).toInt() + 2, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.drawRichText(text, padX, baseline, p, stacked)

        val ink = inkBounds(bmp)
        assertTrue("nothing was drawn", ink != null)
        val (left, top, right, bottom) = ink!!

        // 1 px of slack for antialiasing at the edges.
        assertTrue("ink left $left < reserved ${padX - 1}", left >= padX - 1)
        assertTrue("ink right $right > reserved ${padX + w + 1}", right <= padX + w + 1)
        assertTrue("ink rises above the ascent", top >= baseline + fm.ascent - 1)
        assertTrue("ink drops below the descent", bottom <= baseline + fm.descent + 1)
    }

    /** Centre alignment must centre the whole construction, not just the plain runs. */
    @Test
    fun `centre aligned rich text is symmetric about x`() {
        val p = paint(40f).apply { textAlign = Paint.Align.CENTER }
        val text = "5/8\""
        val w = p.measureRichText(text, stacked)

        val cx = 120f
        val bmp = Bitmap.createBitmap(240, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.drawRichText(text, cx, 100f, p, stacked)

        val ink = inkBounds(bmp)!!
        val drawnCx = (ink.first + ink.third) / 2f
        assertTrue("drawn centre $drawnCx vs $cx", kotlin.math.abs(drawnCx - cx) <= w * 0.12f)
    }

    @Test
    fun `diagonal style also fits its measured advance`() {
        val p = paint(40f)
        val style = FractionTextStyle(style = FractionStyle.DIAGONAL)
        val text = "2 7/8\""
        val w = p.measureRichText(text, style)

        val padX = 20f
        val bmp = Bitmap.createBitmap((w + padX * 2).toInt() + 2, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.drawRichText(text, padX, 100f, p, style)

        val ink = inkBounds(bmp)!!
        assertTrue(ink.first >= padX - 1)
        assertTrue(ink.third <= padX + w + 1)
    }

    @Test
    fun `inline style is a pure passthrough`() {
        val p = paint()
        val style = FractionTextStyle(style = FractionStyle.INLINE)
        assertEquals(p.measureText("1 3/16\""), p.measureRichText("1 3/16\"", style), 0.001f)
    }

    /** Left, top, right, bottom of every non-white pixel. */
    private fun inkBounds(bmp: Bitmap): Quad? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                if (bmp.getPixel(x, y) != Color.WHITE) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < left) null else Quad(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    private data class Quad(val first: Float, val second: Float, val third: Float, val fourth: Float)
}
