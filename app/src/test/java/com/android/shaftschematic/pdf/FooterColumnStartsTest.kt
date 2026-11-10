package com.android.shaftschematic.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A printed footer column whose line would wrap takes room its neighbour is not using (on-device
 * request: the FWD column wrapped `KW: 2" × 5/8" × 9 3/16"` with half the band empty beside it).
 */
class FooterColumnStartsTest {

    // A 700 pt band: default middle start 280 (40%), FWD start 532 (76%).
    private val left = 0f
    private val right = 700f
    private val midDefault = 280f
    private val rightDefault = 532f
    private val pad = 6f
    private val gutter = 15f

    private fun starts(aftW: Float, midW: Float, fwdW: Float) =
        footerColumnStarts(left, right, midDefault, rightDefault, pad, gutter, aftW, midW, fwdW)

    @Test
    fun `columns that fit keep the default split`() {
        assertEquals(midDefault to rightDefault, starts(aftW = 200f, midW = 150f, fwdW = 150f))
    }

    @Test
    fun `a long FWD line pulls its column left into unused middle room`() {
        // FWD needs 220 → would start at 700 − 220 − 1 = 479; the middle text ends at 280 + 150.
        assertEquals(midDefault to 479f, starts(aftW = 200f, midW = 150f, fwdW = 220f))
    }

    @Test
    fun `the FWD column never starts inside the middle column's text`() {
        // Middle text runs to 280 + 230; FWD stops a gutter past it, at 525, and wraps the rest.
        assertEquals(midDefault to 525f, starts(aftW = 200f, midW = 230f, fwdW = 400f))
    }

    @Test
    fun `a long AFT line pushes the middle start right, never squeezing the middle text`() {
        // AFT needs 300 → middle at 300 + 15 + 1 = 316; middle text (150) still fits before 532.
        assertEquals(316f to rightDefault, starts(aftW = 300f, midW = 150f, fwdW = 150f))
        // A middle column that needs all its default room stays put.
        assertEquals(midDefault to rightDefault, starts(aftW = 300f, midW = 400f, fwdW = 150f))
    }

    @Test
    fun `a column never moves the wrong way`() {
        // Nothing needs room: no column starts left of its middle default or right of its FWD default.
        assertEquals(midDefault to rightDefault, starts(aftW = 0f, midW = 0f, fwdW = 0f))
    }
}
