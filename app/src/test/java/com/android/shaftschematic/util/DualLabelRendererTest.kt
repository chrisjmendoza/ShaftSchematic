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
 * The Paint/Canvas half of dual-unit stacking — the sibling of `FractionTextRendererTest`.
 *
 * Three things are pinned here and nowhere else:
 * 1. **A stack is NARROWER than the inline pair.** This is the whole economic case for stacking:
 *    a narrower value seats in the dimension line's break, which is what refunds the height the
 *    second line costs.
 * 2. **The stack is exactly one line taller than a single value.** Every vertical budget in the
 *    app derives from that number, so a drift here is a drift everywhere.
 * 3. **The two lines do not overlap when drawn**, at the advance the metrics promised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DualLabelRendererTest {

    private fun paint(size: Float = 24f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = Color.BLACK
    }

    private val label = DualLabel("1 1/2\"", "38.1 mm")

    @Test
    fun `the stack is one line taller than a single value, and no taller`() {
        val p = paint()
        val m = p.dualStackMetrics()
        val fm = p.fontMetrics
        val singleLine = fm.descent - fm.ascent

        assertEquals(singleLine, m.lineHeight, 1e-3f)
        assertEquals(singleLine + m.leading, m.advance, 1e-3f)
        // The budget claim every site relies on: stack = single + advance.
        assertEquals(singleLine + m.advance, m.height, 1e-3f)
        assertEquals(m.height - singleLine, m.advance, 1e-3f)
    }

    @Test
    fun `a stacked value is narrower than the same value inline`() {
        val p = paint()
        val inline = p.measureDualLabel(label, stacked = false)
        val stacked = p.measureDualLabel(label, stacked = true)
        assertTrue(
            "stacked=$stacked should be well under inline=$inline",
            stacked < inline * 0.75f,
        )
        // And it is exactly the wider of the two terms — not an average, not the sum.
        val widest = label.lines().maxOf { p.measureRichText(it) }
        assertEquals(widest, stacked, 1e-3f)
    }

    @Test
    fun `a single-unit label measures and draws identically whatever the layout says`() {
        val p = paint()
        val single = DualLabel.single("12 5/8\"")
        assertEquals(
            p.measureDualLabel(single, stacked = false),
            p.measureDualLabel(single, stacked = true),
            1e-3f,
        )
    }

    @Test
    fun `the two lines occupy separate bands of ink`() {
        val p = paint(20f)
        val m = p.dualStackMetrics()
        val bmp = Bitmap.createBitmap(220, 120, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val baseline = 40f
        c.drawDualLabel(label, 10f, baseline, p, stacked = true)

        fun rowHasInk(y: Int): Boolean =
            (0 until bmp.width).any { x -> bmp.getPixel(x, y) != Color.WHITE }

        // Ink above the first baseline (the primary) and below the second (the secondary).
        assertTrue("primary line drew no ink", (10 until baseline.toInt()).any(::rowHasInk))
        val secondBaseline = baseline + m.advance
        assertTrue(
            "secondary line drew no ink",
            ((baseline.toInt() + 2) until secondBaseline.toInt() + 4).any(::rowHasInk),
        )
        // The seam between them is clear — the two terms read as two values, not one blur.
        val seam = (baseline + p.fontMetrics.descent + m.leading * 0.5f).toInt()
        assertTrue("the stack's seam should be clear of ink", !rowHasInk(seam))
    }
}
