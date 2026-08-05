package com.android.shaftschematic.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min
import kotlin.math.sqrt

/**
 * breakPairLayout — the paired S-break glyphs must never overlap (on-device report:
 * the two edges crossed on a tall shaft's narrow compressed runs). Pins: the clearance
 * invariant (gap − reach·amp − stroke ≥ the 1pt minimum), the classic gap surviving
 * untouched when it already clears, gap widening before amplitude flattening, the
 * half-run gap ceiling, and the amplitude clamp on runs too narrow for the full glyph.
 */
class BreakPairLayoutTest {

    private val classicCap = 20f // ZIGZAG_GAP_MAX_PT at every composer site

    private fun classicGap(runLenPt: Float) = min(classicCap, 0.25f * runLenPt)

    private fun layout(runLenPt: Float, amp: Float, stroke: Float = 1f) =
        breakPairLayout(runLenPt, amp, classicGap(runLenPt), stroke)

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
    fun `small glyphs keep the classic gap untouched`() {
        // A thin shaft's amplitude clears the classic gap easily — layout is identity.
        val l = layout(200f, desiredAmp(10f))
        assertEquals(classicGap(200f), l.gapPt, 1e-4f)
        assertEquals(desiredAmp(10f), l.amplitudePt, 1e-4f)
    }

    @Test
    fun `tall shafts widen the gap before flattening the glyph`() {
        // 1.5in-tall shaft (r=54, amp=32.4) on a long run: the needed gap exceeds the
        // classic 20pt cap — the gap grows and the full amplitude survives.
        val amp = desiredAmp(54f)
        val l = layout(500f, amp)
        assertTrue(l.gapPt > classicCap)
        assertEquals(amp, l.amplitudePt, 1e-4f)
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
        val l = breakPairLayout(runLenPt = 4f, desiredAmplitudePt = 30f, classicGapPt = 1f, strokeWidthPt = 2f)
        assertTrue(l.amplitudePt >= 0f)
    }

    /** Every composer site derives the desired amplitude as 0.6 × drawn radius. */
    private fun desiredAmp(radiusPt: Float) = radiusPt * 0.6f
}
