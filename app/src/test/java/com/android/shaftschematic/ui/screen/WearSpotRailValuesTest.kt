package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.pdf.WearRailSpanLayout
import com.android.shaftschematic.util.DualLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wear detail page's rail prints each value over a sheet-white knockout: a short span's value
 * is centred over a span narrower than itself, across that span's own witness ticks, and without
 * the knockout the ticks struck through its digits (on-device report). Pins the planned box.
 */
class WearSpotRailValuesTest {

    private val ascent = -10f
    private val descent = 3f
    private val pad = 1.5f
    private val railY = 100f

    private fun span(x0: Float, x1: Float, text: String, row: Int, seats: Boolean) =
        WearRailSpanLayout(
            x0Pt = x0, x1Pt = x1, label = DualLabel.single(text), labelCxPt = (x0 + x1) / 2f,
            labelRow = row, seatsInBreak = seats, arrowInward = true,
        )

    private fun plan(vararg spans: WearRailSpanLayout) =
        wearSpotRailValues(spans.toList(), railY, labelWidth = { it.length * 8f }, ascent, descent, pad)

    @Test
    fun `a fallback value's knockout covers its text and both of its span's ticks`() {
        // A 10 px span carrying a 40 px value: centred over it, across both ticks.
        val v = plan(span(200f, 210f, "1 1/2", row = 0, seats = false)).single()

        assertEquals(205f, v.centerX, 0f)
        assertTrue("covers the AFT tick", v.haloLeft < 200f)
        assertTrue("covers the FWD tick", v.haloRight > 210f)
        assertEquals(v.baseline + ascent - pad, v.haloTop, 1e-4f)
        assertEquals(v.baseline + descent + pad, v.haloBottom, 1e-4f)
        assertTrue("row 0 prints above the line", v.baseline < railY)
    }

    @Test
    fun `a second-row fallback value prints below the line, still knocked out`() {
        val values = plan(
            span(200f, 210f, "1 1/2", row = 0, seats = false),
            span(210f, 216f, "3/4", row = 1, seats = false),
        )
        val below = values[1]
        assertTrue("row 1 sits below the line", below.baseline + ascent > railY)
        assertTrue(below.haloLeft < 210f && below.haloRight > 216f)
    }

    @Test
    fun `a seated value centres on the line inside its break`() {
        val v = plan(span(0f, 300f, "12", row = 0, seats = true)).single()
        assertEquals(railY, (v.baseline + ascent + v.baseline + descent) / 2f, 1e-4f)
        assertEquals(16f, v.width, 0f)
    }
}
