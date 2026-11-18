package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.android.shaftschematic.pdf.notes.DiaCallout
import com.android.shaftschematic.pdf.notes.DiaToleranceText
import com.android.shaftschematic.pdf.notes.DiameterLeaderRenderer
import com.android.shaftschematic.pdf.notes.LeaderSide
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A toleranced Ø value is wider than the same value without, and the box the renderer reserves
 * for it ([DiameterLeaderRenderer.occupancy] — what the callout planner and the name labels avoid)
 * covers the stacked limits it inks: measured and drawn from one layout, with real text metrics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiaToleranceOccupancyTest {

    private val shaftBottomY = 200f

    private fun renderer(dualStacked: Boolean = false) = DiameterLeaderRenderer(
        pageX = { mm -> mm.toFloat() },
        shaftTopY = 100f,
        shaftBottomY = shaftBottomY,
        linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 0.7f },
        textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f },
        dualStacked = dualStacked,
    )

    private val plain = DiaCallout(xMm = 300.0, valueMm = 228.6, side = LeaderSide.BELOW, unit = UnitSystem.INCHES)
    private val toleranced = plain.copy(tolerance = DiaToleranceText("+.002", "-.000"))

    @Test
    fun `a toleranced value reserves a wider box that holds the stack`() {
        val r = renderer()
        val plainBox = r.occupancy(listOf(plain)).first()
        val tolBox = r.occupancy(listOf(toleranced)).first()
        val plainW = plainBox.right - plainBox.left
        val tolW = tolBox.right - tolBox.left
        assertTrue("toleranced $tolW > plain $plainW", tolW > plainW + 10f)
        // The stack is centred on the value's digits and stays near the value's own line height.
        assertTrue(tolBox.top <= plainBox.top + 0.01f)
        assertTrue(tolBox.bottom >= plainBox.bottom - 0.01f)
        assertTrue(tolBox.bottom - tolBox.top < (plainBox.bottom - plainBox.top) * 1.8f)
    }

    @Test
    fun `no tolerance reserves exactly what it always did`() {
        val r = renderer()
        assertEquals(r.occupancy(listOf(plain)), r.occupancy(listOf(plain.copy(tolerance = null))))
    }

    @Test
    fun `the inked stack stays inside the reserved box`() {
        val r = renderer()
        val box = r.occupancy(listOf(toleranced)).first()
        val bmp = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        r.draw(Canvas(bmp), listOf(toleranced))
        // Every inked pixel right of the leader's drop belongs to the label: none may fall outside
        // the reserved text box (1 px of anti-alias slack).
        val leaderX = 300
        var inkRight = -1
        for (y in 0 until bmp.height) for (x in leaderX + 2 until bmp.width) {
            if (y < box.top - 1 || y > box.bottom + 1 || x > box.right + 1) {
                val px = bmp.getPixel(x, y)
                if (y > shaftBottomY + 20 && Color.red(px) < 128) {
                    throw AssertionError("ink at ($x, $y) outside the reserved box $box")
                }
            } else if (Color.red(bmp.getPixel(x, y)) < 128) {
                inkRight = maxOf(inkRight, x)
            }
        }
        assertTrue("the limits were inked past the value", inkRight > box.left + (box.right - box.left) * 0.75f)
    }

    @Test
    fun `a stacked dual value keeps its secondary on the second line and the limits on the first`() {
        val dual = toleranced.copy(dual = true)
        val stacked = renderer(dualStacked = true).occupancy(listOf(dual)).first()
        val inline = renderer(dualStacked = false).occupancy(listOf(dual)).first()
        // Inline sets the secondary after the stack; stacked sets it under the value, so it is narrower.
        assertTrue((stacked.right - stacked.left) < (inline.right - inline.left))
    }
}
