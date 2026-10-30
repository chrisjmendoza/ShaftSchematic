package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.BREAK_PAIR_REACH_FRAC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * breakPairLayout — the paired S-break glyphs must never overlap (on-device report:
 * the two edges crossed on a tall shaft's narrow compressed runs), and must not stand
 * further apart than the glyph needs (on-device report: a fixed 20 pt gap read as a
 * missing piece). Pins: the clearance invariant (gap − reach·amp − stroke ≥ the 1pt
 * minimum), [BREAK_PAIR_DAYLIGHT_PT] of daylight exactly wherever the run has room, the
 * half-run gap ceiling, and the amplitude clamp on runs too narrow for the full glyph.
 */
class BreakPairLayoutTest {

    private fun layout(runLenPt: Float, amp: Float, stroke: Float = 1f) =
        breakPairLayout(runLenPt, amp, stroke)

    /** The invariant itself: daylight between the pair's nearest curves. */
    private fun clearance(l: BreakPairLayout, stroke: Float) =
        l.gapPt - l.amplitudePt * BREAK_PAIR_REACH_FRAC - stroke

    @Test
    fun `reach fraction matches the glyph's cubic geometry`() {
        // Main S peaks at √3/6 of amp; the 1.5-full return sweep at 1.5·√3/6 — the pair
        // closes (1 + 1.5)·√3/6 of the amplitude in each half of the shaft.
        assertEquals(2.5f * sqrt(3f) / 6f, BREAK_PAIR_REACH_FRAC, 1e-5f)
    }

    @Test
    fun `pair always keeps at least the minimum clearance`() {
        // Sweep of shaft sizes × run widths, including the overlap case from the
        // on-device report (tall shaft, narrow foreshortened run).
        for (r in listOf(10f, 25f, 40f, 54f)) {
            for (len in listOf(40f, 64f, 100f, 200f, 500f)) {
                val l = layout(len, desiredAmp(r))
                assertTrue(
                    "r=$r len=$len clearance=${clearance(l, 1f)}",
                    clearance(l, 1f) >= BREAK_PAIR_MIN_CLEAR_PT - 1e-3f,
                )
            }
        }
    }

    @Test
    fun `with room the pair keeps exactly the daylight at every shaft height`() {
        for (r in listOf(10f, 25f, 40f, 54f)) {
            val l = layout(500f, desiredAmp(r))
            assertEquals("r=$r", BREAK_PAIR_DAYLIGHT_PT, clearance(l, 1f), 1e-3f)
            assertEquals("full glyph r=$r", desiredAmp(r), l.amplitudePt, 1e-4f)
        }
    }

    @Test
    fun `the on-device 9 inch shaft's gap tightens from the old 20 pt`() {
        // The report's sheet drew the shaft ~51 pt tall (r ≈ 25.5): the old fixed 20 pt gap
        // left ~8 pt of paper between the curves.
        val l = layout(300f, desiredAmp(25.5f))
        assertTrue("gap=${l.gapPt}", l.gapPt < 16f)
        assertTrue(clearance(l, 1f) < 8f)
    }

    @Test
    fun `gap never exceeds half the run - stubs keep a quarter each side`() {
        val l = layout(48f, desiredAmp(54f))
        assertTrue(l.gapPt <= 24f + 1e-3f)
        // The glyph flattens instead, still clearing the minimum.
        assertTrue(l.amplitudePt < desiredAmp(54f))
        assertTrue(clearance(l, 1f) >= BREAK_PAIR_MIN_CLEAR_PT - 1e-3f)
    }

    @Test
    fun `amplitude never goes negative on degenerate runs`() {
        val l = breakPairLayout(runLenPt = 4f, desiredAmplitudePt = 30f, strokeWidthPt = 2f)
        assertTrue(l.amplitudePt >= 0f)
    }

    /** Every composer site derives the desired amplitude as 0.6 × drawn radius. */
    private fun desiredAmp(radiusPt: Float) = radiusPt * 0.6f
}
